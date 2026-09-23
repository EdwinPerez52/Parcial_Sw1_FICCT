package com.collabmodeler.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AiService {
    private final AiProperties properties; private final ObjectMapper mapper; private final RestClient rest;
    public AiService(AiProperties properties, ObjectMapper mapper, RestClient.Builder builder) {
        this.properties = properties; this.mapper = mapper; this.rest = builder.build();
    }
    public JsonNode analyzeImage(byte[] image, String contentType) {
        String prompt = "Analiza la fotografía como un diagrama UML de clases o entidad-relación. " +
            "Extrae solamente lo visible: nombres de clases, atributos, tipos, claves, relaciones, roles y cardinalidades. " +
            "Conserva exactamente los nombres legibles, no inventes elementos y registra toda ambigüedad en warnings. " +
            "Convierte tipos SQL equivalentes a los tipos escalares permitidos. Si no hay un diagrama reconocible, devuelve " +
            "classes y associations vacíos, baja confianza y una advertencia clara.";
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image);
        JsonNode result = chat(properties.getVisionModel(), List.of(Map.of("role", "user", "content", List.of(
            Map.of("type", "text", "text", prompt),
            Map.of("type", "image_url", "image_url", Map.of("url", dataUrl, "detail", "high"))))), imageResponseFormat());
        return ImageProposalValidator.validate(result);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeMobileImage(byte[] image, String contentType, String contextPrompt) {
        String prompt = "Extrae los datos de la imagen o comprobante como una propuesta CRUD estructurada. " +
            "Responde exactamente en JSON: {action:'create|update|delete|search', entity:'NombreEntidad', data:{clave:valor}, confidence:0.95}. " +
            "No inventes datos ni generes SQL o código. " +
            (contextPrompt != null && !contextPrompt.isBlank() ? " Contexto adicional del usuario: " + contextPrompt : "");
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image);
        JsonNode node = chat(properties.getVisionModel(), List.of(Map.of("role", "user", "content", List.of(Map.of("type", "text", "text", prompt), Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))));
        return mapper.convertValue(node, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeMobilePrompt(String userPrompt) {
        String systemPrompt = "Interpreta el comando del usuario para datos de una app móvil y responde en JSON: " +
            "{action:'create|update|delete|search', entity:'NombreEntidad', data:{clave:valor}, confidence:0.95}. " +
            "No generes SQL ni código ejecutable.";
        String model = properties.getTextModel() != null && !properties.getTextModel().isBlank() ? properties.getTextModel() : properties.getVisionModel();
        JsonNode node = chat(model, List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userPrompt)));
        return mapper.convertValue(node, Map.class);
    }
    private JsonNode chat(String model, List<Map<String, Object>> messages) {
        return chat(model, messages, Map.of("type", "json_object"));
    }

    private JsonNode chat(String model, List<Map<String, Object>> messages, Map<String, Object> responseFormat) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) throw new AiUnavailableException("Configura AI_API_KEY para usar el proveedor de IA");

        List<String> modelsToTry = new java.util.ArrayList<>();
        modelsToTry.add(model);
        if (properties.getBaseUrl() != null && properties.getBaseUrl().contains("generativelanguage.googleapis.com")) {
            if (!"gemini-flash-latest".equals(model)) modelsToTry.add("gemini-flash-latest");
            if (!"gemini-flash-lite-latest".equals(model)) modelsToTry.add("gemini-flash-lite-latest");
        }

        RestClientException lastException = null;
        for (String candidateModel : modelsToTry) {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    JsonNode response = rest.post().uri(properties.getBaseUrl() + "/chat/completions").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .body(Map.of("model", candidateModel, "messages", messages, "temperature", 0,
                            "response_format", responseFormat))
                        .retrieve().body(JsonNode.class);
                    String content = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText();
                    return mapper.readTree(content);
                } catch (RestClientException exception) {
                    lastException = exception;
                    String msg = exception.getMessage() == null ? "" : exception.getMessage();
                    if (msg.contains("503") || msg.contains("high demand") || msg.contains("429")) {
                        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    break;
                } catch (Exception exception) {
                    throw new IllegalArgumentException("El proveedor de IA devolvió JSON inválido", exception);
                }
            }
        }
        throw new AiUnavailableException("El proveedor de IA no respondió correctamente. Revisa AI_API_KEY y AI_VISION_MODEL. Detalle: " + (lastException != null ? lastException.getMessage() : "Desconocido"));
    }

    private Map<String, Object> imageResponseFormat() {
        Map<String, Object> attribute = strictObject(Map.of(
            "name", Map.of("type", "string"),
            "type", Map.of("type", "string", "enum", List.of("String", "Text", "Integer", "Long", "Decimal", "Boolean", "Date", "DateTime", "UUID", "Binary")),
            "primaryKey", Map.of("type", "boolean"),
            "required", Map.of("type", "boolean"),
            "unique", Map.of("type", "boolean")));
        Map<String, Object> diagramClass = strictObject(Map.of(
            "name", Map.of("type", "string"),
            "attributes", Map.of("type", "array", "items", attribute)));
        Map<String, Object> cardinality = Map.of("type", "string", "enum", List.of("0..1", "1", "0..*", "1..*"));
        Map<String, Object> association = strictObject(Map.of(
            "source", Map.of("type", "string"),
            "target", Map.of("type", "string"),
            "sourceCardinality", cardinality,
            "targetCardinality", cardinality,
            "name", Map.of("type", List.of("string", "null")),
            "sourceRole", Map.of("type", List.of("string", "null")),
            "targetRole", Map.of("type", List.of("string", "null")),
            "owningSide", Map.of("type", "string", "enum", List.of("SOURCE", "TARGET"))));
        Map<String, Object> schema = strictObject(Map.of(
            "classes", Map.of("type", "array", "items", diagramClass),
            "associations", Map.of("type", "array", "items", association),
            "warnings", Map.of("type", "array", "items", Map.of("type", "string")),
            "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1)));
        return Map.of("type", "json_schema", "json_schema", Map.of(
            "name", "uml_image_extraction", "strict", true, "schema", schema));
    }

    private Map<String, Object> strictObject(Map<String, Object> properties) {
        return Map.of("type", "object", "additionalProperties", false, "properties", properties,
            "required", List.copyOf(properties.keySet()));
    }
}
