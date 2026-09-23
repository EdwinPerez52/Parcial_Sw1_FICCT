package com.collabmodeler.api.ai;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.collabmodeler.api.diagram.DiagramOperationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class OpenAiTextCommandProvider implements TextCommandProvider {
    private static final String PAYLOAD_CONTRACT = """
        Formas exactas de payload por type (UUID siempre es texto UUID v4):
        CLASS_CREATED {id,name,attributes:[],position:{x,y},version:1};
        CLASS_RENAMED {id,name}; CLASS_MOVED {id,x,y}; CLASS_DELETED {id};
        ATTRIBUTE_CREATED {classId,attribute:{id,name,type,primaryKey,required,unique,version:1}};
        ATTRIBUTE_UPDATED {classId,attribute:{id,name,type,primaryKey,required,unique,version}};
        ATTRIBUTE_REORDERED {classId,attributeId,newIndex}; ATTRIBUTE_DELETED {classId,id};
        ASSOCIATION_CREATED o ASSOCIATION_UPDATED {id,sourceId,targetId,sourceCardinality,targetCardinality,name,sourceRole,targetRole,owningSide,version};
        ASSOCIATION_DELETED {id};
        ENUMERATION_CREATED o ENUMERATION_UPDATED {id,name,values:[{id,name,version}],position:{x,y},version};
        ENUMERATION_DELETED {id};
        ENUMERATION_VALUE_CREATED o ENUMERATION_VALUE_UPDATED {enumerationId,value:{id,name,version}};
        ENUMERATION_VALUE_DELETED {enumerationId,id};
        GENERALIZATION_CREATED {id,parentId,childId,version:1}; GENERALIZATION_DELETED {id};
        BATCH {operations:[DiagramOperation,...]}. No anides BATCH.
        expectedElementVersion debe ser null al crear y la version actual del elemento al modificar o eliminar.
        Los tipos escalares permitidos son exactamente String, Text, Integer, Long, Decimal, Boolean, Date,
        DateTime, UUID y Binary. No uses tipos SQL como INT, VARCHAR o TIMESTAMP.
        Las cardinalidades permitidas son exactamente 0..1, 1, 0..* y 1..*.
        Incluye todos los campos indicados aunque su valor de texto sea null; no agregues otros campos.
        Para diseñar un dominio completo crea un modelo breve de 4 a 8 clases con atributos esenciales,
        claves primarias y asociaciones importantes con cardinalidades. Usa CLASS_CREATED con sus atributos
        incluidos y luego ASSOCIATION_CREATED reutilizando exactamente los UUID de las clases recién creadas.
        El modelo debe quedar conectado y contener como mínimo tres asociaciones.
        """;
    private static final List<String> TYPES = List.of(
        "CLASS_CREATED", "CLASS_RENAMED", "CLASS_MOVED", "CLASS_DELETED",
        "ATTRIBUTE_CREATED", "ATTRIBUTE_UPDATED", "ATTRIBUTE_REORDERED", "ATTRIBUTE_DELETED",
        "ASSOCIATION_CREATED", "ASSOCIATION_UPDATED", "ASSOCIATION_DELETED",
        "ENUMERATION_CREATED", "ENUMERATION_UPDATED", "ENUMERATION_DELETED",
        "ENUMERATION_VALUE_CREATED", "ENUMERATION_VALUE_UPDATED", "ENUMERATION_VALUE_DELETED",
        "GENERALIZATION_CREATED", "GENERALIZATION_DELETED", "BATCH"
    );
    private final AiProperties properties;
    private final ObjectMapper mapper;
    private final RestClient rest;

    public OpenAiTextCommandProvider(AiProperties properties, ObjectMapper mapper, RestClient.Builder builder) {
        this.properties = properties; this.mapper = mapper; this.rest = builder.build();
    }

    @Override public String id() { return "openai:" + properties.getTextModel(); }

    @Override
    public DiagramOperationRequest interpret(String instruction, DiagramDocument diagram) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiUnavailableException("La instrucción no es simple y AI_API_KEY no está configurada");
        }
        String system = "Convierte exclusivamente la instrucción puntual en una DiagramOperation UML o BATCH. " +
            "Usa solo UUID y versiones presentes en el diagrama para editar, crea UUID v4 para elementos nuevos, " +
            "no devuelvas SQL, código, markdown ni texto adicional. Responde solo invocando las herramientas de operaciones. " +
            "Puedes invocar varias herramientas; el adaptador las agrupará en un BATCH. No inventes elementos no solicitados.\n" +
            PAYLOAD_CONTRACT;
        List<Map<String, Object>> tools = TYPES.stream().filter(value -> !"BATCH".equals(value))
            .map(type -> Map.<String, Object>of(
                "type", "function",
                "function", Map.of(
                    "name", type,
                    "description", "Propone una operación UML segura de tipo " + type,
                    "strict", true,
                    "parameters", operationArgumentsSchema(type))))
            .toList();
        Exception lastFailure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String retry = attempt == 0 ? "" : "\nLa propuesta anterior fue inválida o insuficiente. " +
                "Corrígela respetando cada herramienta, usando 4 a 8 clases y al menos 3 asociaciones si se pidió un dominio completo.";
            try {
                JsonNode response = request(system, "Diagrama JSON: " + safeDiagram(diagram) +
                    "\nInstrucción: " + instruction + retry, tools);
                List<DiagramOperationRequest> operations = new ArrayList<>(
                    normalizeGeneratedIds(parseToolCalls(response, diagram), diagram));
                if (isDomainDesign(instruction)) {
                    if (countClasses(operations) > 8) {
                        throw new IllegalArgumentException("El dominio propuesto excede el máximo de 8 clases");
                    }
                    completeDomainClasses(system, instruction, operations, diagram, tools);
                    requireUsefulClassSet(operations);
                    completeDomainAssociations(system, instruction, operations, diagram, tools);
                    requireUsefulDomain(operations);
                }
                return asOperationOrBatch(operations, diagram);
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new AiUnavailableException("OpenAI rechazó la credencial. Revisa AI_API_KEY y los permisos del proyecto.");
                }
                if (status == 429) {
                    throw new AiUnavailableException("OpenAI rechazó la solicitud por límite o crédito insuficiente. Revisa la facturación y vuelve a intentarlo.");
                }
                lastFailure = exception;
            } catch (Exception exception) {
                lastFailure = exception;
            }
        }
        throw new IllegalArgumentException("El proveedor de IA devolvió una DiagramOperation inválida", lastFailure);
    }

    private void completeDomainClasses(String system, String instruction,
                                       List<DiagramOperationRequest> operations,
                                       DiagramDocument diagram,
                                       List<Map<String, Object>> tools) throws Exception {
        List<Map<String, Object>> classTool = tools.stream()
            .filter(tool -> "CLASS_CREATED".equals(
                ((Map<?, ?>) tool.get("function")).get("name")))
            .toList();
        Set<String> names = operations.stream()
            .filter(value -> "CLASS_CREATED".equals(value.type()))
            .map(value -> value.payload().path("name").asText().toLowerCase(java.util.Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
        for (int attempt = 0; countClasses(operations) < 4 && attempt < 6; attempt++) {
            String context = mapper.writeValueAsString(operations);
            JsonNode response = request(system,
                "Instrucción original: " + instruction +
                    "\nOperaciones ya propuestas: " + context +
                    "\nAgrega exactamente una CLASS_CREATED esencial que falte para este dominio. " +
                    "No repitas nombres y agrega sus atributos esenciales, incluida una clave primaria.",
                classTool);
            List<DiagramOperationRequest> proposed = normalizeGeneratedIds(parseToolCalls(response, diagram), diagram);
            for (DiagramOperationRequest value : proposed) {
                if (!"CLASS_CREATED".equals(value.type())) continue;
                String name = value.payload().path("name").asText().toLowerCase(java.util.Locale.ROOT);
                if (!name.isBlank() && names.add(name)) {
                    operations.add(value);
                    break;
                }
            }
        }
    }

    private JsonNode request(String system, String user, List<Map<String, Object>> tools) {
        return rest.post().uri(properties.getBaseUrl() + "/chat/completions")
            .contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + properties.getApiKey())
            .body(Map.of(
                "model", properties.getTextModel(), "temperature", 0,
                "messages", List.of(Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)),
                "tools", tools,
                "tool_choice", "required"))
            .retrieve().body(JsonNode.class);
    }

    private void completeDomainAssociations(String system, String instruction,
                                             List<DiagramOperationRequest> operations,
                                             DiagramDocument diagram,
                                             List<Map<String, Object>> tools) throws Exception {
        List<Map<String, Object>> associationTool = tools.stream()
            .filter(tool -> "ASSOCIATION_CREATED".equals(
                ((Map<?, ?>) tool.get("function")).get("name")))
            .toList();
        Set<String> existingPairs = new java.util.HashSet<>();
        operations.stream().filter(value -> "ASSOCIATION_CREATED".equals(value.type()))
            .forEach(value -> existingPairs.add(associationPair(value.payload())));
        for (int attempt = 0; countAssociations(operations) < 3 && attempt < 4; attempt++) {
            String context = mapper.writeValueAsString(operations);
            JsonNode response = request(system,
                "Instrucción original: " + instruction +
                    "\nOperaciones ya propuestas: " + context +
                    "\nAgrega exactamente una ASSOCIATION_CREATED importante que falte. " +
                    "Usa como sourceId y targetId exclusivamente los id de CLASS_CREATED anteriores, " +
                    "no repitas un par ya relacionado y expresa ambas cardinalidades UML.",
                associationTool);
            List<DiagramOperationRequest> proposed = normalizeGeneratedIds(parseToolCalls(response, diagram), diagram);
            for (DiagramOperationRequest value : proposed) {
                if (!"ASSOCIATION_CREATED".equals(value.type())) continue;
                String pair = associationPair(value.payload());
                if (referencesCreatedClasses(value.payload(), operations) && existingPairs.add(pair)) {
                    operations.add(value);
                    break;
                }
            }
        }
    }

    private boolean referencesCreatedClasses(JsonNode payload, List<DiagramOperationRequest> operations) {
        Set<String> classIds = operations.stream()
            .filter(value -> "CLASS_CREATED".equals(value.type()))
            .map(value -> value.payload().path("id").asText())
            .collect(java.util.stream.Collectors.toSet());
        return classIds.contains(payload.path("sourceId").asText())
            && classIds.contains(payload.path("targetId").asText())
            && !payload.path("sourceId").asText().equals(payload.path("targetId").asText());
    }

    private String associationPair(JsonNode payload) {
        String source = payload.path("sourceId").asText();
        String target = payload.path("targetId").asText();
        return source.compareTo(target) <= 0 ? source + ":" + target : target + ":" + source;
    }

    private long countAssociations(List<DiagramOperationRequest> operations) {
        return operations.stream().filter(value -> "ASSOCIATION_CREATED".equals(value.type())).count();
    }

    private long countClasses(List<DiagramOperationRequest> operations) {
        return operations.stream().filter(value -> "CLASS_CREATED".equals(value.type())).count();
    }

    private List<DiagramOperationRequest> parseToolCalls(JsonNode response, DiagramDocument diagram) throws Exception {
        JsonNode calls = response == null ? null : response.path("choices").path(0).path("message").path("tool_calls");
        if (calls == null || !calls.isArray() || calls.isEmpty()) {
            throw new IllegalArgumentException("El proveedor no invocó ninguna operación UML");
        }
        List<DiagramOperationRequest> operations = new java.util.ArrayList<>();
        for (JsonNode call : calls) {
            JsonNode function = call.path("function");
            String type = function.path("name").asText();
            if (!TYPES.contains(type) || "BATCH".equals(type)) {
                throw new IllegalArgumentException("Tipo no permitido por el contrato: " + type);
            }
            JsonNode rawArguments = function.path("arguments");
            JsonNode arguments = rawArguments.isTextual() ? mapper.readTree(rawArguments.asText()) : rawArguments;
            if (!arguments.isObject()) throw new IllegalArgumentException("Argumentos de operación inválidos");
            ObjectNode operationNode = ((ObjectNode) arguments).deepCopy();
            operationNode.put("operationId", UUID.randomUUID().toString());
            operationNode.put("baseRevision", diagram.revision());
            operationNode.put("type", type);
            operations.add(mapper.treeToValue(operationNode, DiagramOperationRequest.class));
        }
        return operations;
    }

    private List<DiagramOperationRequest> normalizeGeneratedIds(List<DiagramOperationRequest> operations,
                                                                 DiagramDocument diagram) {
        Map<String, String> replacements = new LinkedHashMap<>();
        for (DiagramOperationRequest operation : operations) registerCreatedIds(operation, replacements);
        return operations.stream().map(operation -> {
            JsonNode payload = operation.payload().deepCopy();
            remapReferences(payload, replacements, null);
            Long expected = creationTypes().contains(operation.type()) ? null : operation.expectedElementVersion();
            return new DiagramOperationRequest(UUID.randomUUID(), diagram.revision(), expected, operation.type(), payload);
        }).toList();
    }

    private void registerCreatedIds(DiagramOperationRequest operation, Map<String, String> replacements) {
        JsonNode payload = operation.payload();
        if (Set.of("CLASS_CREATED", "ASSOCIATION_CREATED", "ENUMERATION_CREATED", "GENERALIZATION_CREATED")
            .contains(operation.type())) registerId(payload.path("id"), replacements);
        if ("ATTRIBUTE_CREATED".equals(operation.type())) registerId(payload.path("attribute").path("id"), replacements);
        if ("ENUMERATION_VALUE_CREATED".equals(operation.type())) registerId(payload.path("value").path("id"), replacements);
        if ("CLASS_CREATED".equals(operation.type())) {
            payload.path("attributes").forEach(value -> registerId(value.path("id"), replacements));
        }
        if ("ENUMERATION_CREATED".equals(operation.type())) {
            payload.path("values").forEach(value -> registerId(value.path("id"), replacements));
        }
    }

    private void registerId(JsonNode value, Map<String, String> replacements) {
        String original = value.asText();
        if (original.isBlank() || replacements.putIfAbsent(original, UUID.randomUUID().toString()) != null) {
            throw new IllegalArgumentException("El proveedor repitió u omitió un identificador nuevo");
        }
    }

    private void remapReferences(JsonNode node, Map<String, String> replacements, String fieldName) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual() && ("id".equals(entry.getKey()) || entry.getKey().endsWith("Id"))) {
                    String replacement = replacements.get(value.asText());
                    if (replacement != null) entry.setValue(mapper.getNodeFactory().textNode(replacement));
                } else if (value.isTextual() && "type".equals(entry.getKey())) {
                    entry.setValue(mapper.getNodeFactory().textNode(normalizeAttributeType(value.asText())));
                } else if (value.isTextual() && entry.getKey().endsWith("Cardinality")) {
                    entry.setValue(mapper.getNodeFactory().textNode(normalizeCardinality(value.asText())));
                } else remapReferences(value, replacements, entry.getKey());
            });
        } else if (node.isArray()) {
            node.forEach(value -> remapReferences(value, replacements, fieldName));
        }
    }

    private String normalizeAttributeType(String value) {
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "STRING", "VARCHAR", "CHAR", "CHARACTER" -> "String";
            case "TEXT", "CLOB" -> "Text";
            case "INT", "INTEGER", "SMALLINT" -> "Integer";
            case "LONG", "BIGINT" -> "Long";
            case "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "REAL", "MONEY" -> "Decimal";
            case "BOOL", "BOOLEAN" -> "Boolean";
            case "DATE" -> "Date";
            case "DATETIME", "TIMESTAMP", "TIMESTAMPTZ" -> "DateTime";
            case "UUID" -> "UUID";
            case "BINARY", "BLOB", "BYTEA" -> "Binary";
            default -> value;
        };
    }

    private String normalizeCardinality(String value) {
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "*", "n", "many", "0:n", "0..n" -> "0..*";
            case "+", "1:n", "1..n" -> "1..*";
            case "0:1" -> "0..1";
            default -> value;
        };
    }

    private DiagramOperationRequest asOperationOrBatch(List<DiagramOperationRequest> operations,
                                                        DiagramDocument diagram) {
        if (operations.size() == 1) return operations.getFirst();
        ObjectNode batchPayload = mapper.createObjectNode();
        var children = batchPayload.putArray("operations");
        operations.forEach(operation -> children.add(mapper.valueToTree(operation)));
        return new DiagramOperationRequest(UUID.randomUUID(), diagram.revision(), null, "BATCH", batchPayload);
    }

    private boolean isDomainDesign(String instruction) {
        String value = instruction.toLowerCase(java.util.Locale.ROOT);
        return value.contains("diagrama") && (value.contains("base de datos") || value.contains("sistema") || value.contains("modelo"));
    }

    private void requireUsefulClassSet(List<DiagramOperationRequest> operations) {
        long classes = countClasses(operations);
        if (classes < 4 || classes > 8) {
            throw new IllegalArgumentException("El dominio propuesto requiere de 4 a 8 clases");
        }
    }

    private void requireUsefulDomain(List<DiagramOperationRequest> operations) {
        requireUsefulClassSet(operations);
        if (countAssociations(operations) < 3) {
            throw new IllegalArgumentException("El dominio propuesto requiere al menos 3 asociaciones");
        }
    }

    private Set<String> creationTypes() {
        // ATTRIBUTE_CREATED and ENUMERATION_VALUE_CREATED mutate an existing owner and therefore
        // must retain that owner's current version for optimistic concurrency control.
        return Set.of("CLASS_CREATED", "ASSOCIATION_CREATED", "ENUMERATION_CREATED", "GENERALIZATION_CREATED");
    }

    private String safeDiagram(DiagramDocument value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("No se pudo preparar el contexto mínimo", exception); }
    }

    private Map<String, Object> operationArgumentsSchema(String type) {
        return objectSchema(Map.of(
            "expectedElementVersion", Map.of("type", List.of("integer", "null"), "minimum", 1),
            "payload", payloadSchema(type)
        ));
    }

    private Map<String, Object> payloadSchema(String type) {
        Map<String, Object> position = objectSchema(Map.of(
            "x", Map.of("type", "number"), "y", Map.of("type", "number")));
        Map<String, Object> attribute = objectSchema(Map.of(
            "id", uuidSchema(), "name", textSchema(), "type", textSchema(),
            "primaryKey", Map.of("type", "boolean"), "required", Map.of("type", "boolean"),
            "unique", Map.of("type", "boolean"), "version", positiveIntegerSchema()));
        Map<String, Object> enumValue = objectSchema(Map.of(
            "id", uuidSchema(), "name", textSchema(), "version", positiveIntegerSchema()));
        return switch (type) {
            case "CLASS_CREATED" -> objectSchema(Map.of(
                "id", uuidSchema(), "name", textSchema(), "attributes", arraySchema(attribute),
                "position", position, "version", positiveIntegerSchema()));
            case "CLASS_RENAMED" -> objectSchema(Map.of("id", uuidSchema(), "name", textSchema()));
            case "CLASS_MOVED" -> objectSchema(Map.of(
                "id", uuidSchema(), "x", Map.of("type", "number"), "y", Map.of("type", "number")));
            case "CLASS_DELETED", "ASSOCIATION_DELETED", "ENUMERATION_DELETED", "GENERALIZATION_DELETED" ->
                objectSchema(Map.of("id", uuidSchema()));
            case "ATTRIBUTE_CREATED", "ATTRIBUTE_UPDATED" ->
                objectSchema(Map.of("classId", uuidSchema(), "attribute", attribute));
            case "ATTRIBUTE_REORDERED" -> objectSchema(Map.of(
                "classId", uuidSchema(), "attributeId", uuidSchema(),
                "newIndex", Map.of("type", "integer", "minimum", 0)));
            case "ATTRIBUTE_DELETED" -> objectSchema(Map.of("classId", uuidSchema(), "id", uuidSchema()));
            case "ASSOCIATION_CREATED", "ASSOCIATION_UPDATED" -> objectSchema(Map.of(
                "id", uuidSchema(), "sourceId", uuidSchema(), "targetId", uuidSchema(),
                "sourceCardinality", cardinalitySchema(), "targetCardinality", cardinalitySchema(),
                "name", nullableTextSchema(), "sourceRole", nullableTextSchema(), "targetRole", nullableTextSchema(),
                "owningSide", Map.of("type", "string", "enum", List.of("SOURCE", "TARGET")),
                "version", positiveIntegerSchema()));
            case "ENUMERATION_CREATED", "ENUMERATION_UPDATED" -> objectSchema(Map.of(
                "id", uuidSchema(), "name", textSchema(), "values", arraySchema(enumValue),
                "position", position, "version", positiveIntegerSchema()));
            case "ENUMERATION_VALUE_CREATED", "ENUMERATION_VALUE_UPDATED" ->
                objectSchema(Map.of("enumerationId", uuidSchema(), "value", enumValue));
            case "ENUMERATION_VALUE_DELETED" ->
                objectSchema(Map.of("enumerationId", uuidSchema(), "id", uuidSchema()));
            case "GENERALIZATION_CREATED" -> objectSchema(Map.of(
                "id", uuidSchema(), "parentId", uuidSchema(), "childId", uuidSchema(),
                "version", positiveIntegerSchema()));
            default -> throw new IllegalArgumentException("Tipo no soportado por el proveedor: " + type);
        };
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", properties,
            "required", List.copyOf(properties.keySet())
        );
    }

    private Map<String, Object> arraySchema(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private Map<String, Object> uuidSchema() { return Map.of("type", "string", "format", "uuid"); }
    private Map<String, Object> textSchema() { return Map.of("type", "string", "minLength", 1); }
    private Map<String, Object> nullableTextSchema() { return Map.of("type", List.of("string", "null")); }
    private Map<String, Object> cardinalitySchema() {
        return Map.of("type", "string", "enum", List.of("0..1", "1", "0..*", "1..*"));
    }
    private Map<String, Object> positiveIntegerSchema() { return Map.of("type", "integer", "minimum", 1); }
}
