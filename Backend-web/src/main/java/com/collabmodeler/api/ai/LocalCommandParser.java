package com.collabmodeler.api.ai;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.collabmodeler.api.diagram.DiagramOperationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LocalCommandParser {
    private static final String NAME = "([A-Za-z_][A-Za-z0-9_]*)";
    private static final Pattern CREATE_CLASS = Pattern.compile("(?:crea|crear|creame|agrega|agregar|anade|anadir|genera|generar) (?:(?:un|una|la) )?clase (?:llamada |que se llame )?" + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern RENAME_CLASS = Pattern.compile("(?:renombra|renombrar|cambia el nombre de) (?:la )?clase " + NAME + " (?:a|por) " + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVE_CLASS = Pattern.compile("(?:mueve|mover) (?:la )?clase " + NAME + " (?:a|hasta) \\(?(-?\\d+(?:\\.\\d+)?)\\s*[,; ]\\s*(-?\\d+(?:\\.\\d+)?)\\)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_CLASS = Pattern.compile("(?:elimina|eliminar|borra|borrar) (?:la )?clase " + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_ATTRIBUTE = Pattern.compile("(?:agrega|agregar|crea|crear) (?:un )?atributo " + NAME + " (?:de tipo |tipo )" + NAME + " (?:a|en) (?:la clase )?" + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANGE_ATTRIBUTE = Pattern.compile("(?:cambia|modifica|actualiza) (?:el )?atributo " + NAME + " (?:de|en) (?:la clase )?" + NAME + " (?:a tipo|al tipo|tipo) " + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_ATTRIBUTE = Pattern.compile("(?:elimina|eliminar|borra|borrar) (?:el )?atributo " + NAME + " (?:de|en) (?:la clase )?" + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_ASSOCIATION = Pattern.compile("(?:relaciona|relacionar|asocia|asociar|crea una relacion entre) (?:la clase )?" + NAME + " (?:con|y) (?:la clase )?" + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_ASSOCIATION = Pattern.compile("(?:elimina|eliminar|borra|borrar) (?:la )?(?:relacion|asociacion) (?:entre )?" + NAME + " (?:y|con) " + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_ENUM = Pattern.compile("(?:crea|crear|agrega|agregar) (?:una )?enumeracion " + NAME + "(?: con valores? (.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_ENUM = Pattern.compile("(?:elimina|eliminar|borra|borrar) (?:la )?enumeracion " + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_INHERITANCE = Pattern.compile(NAME + " (?:hereda de|extiende a?) " + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_INHERITANCE = Pattern.compile("(?:elimina|eliminar|borra|borrar) (?:la )?herencia (?:de )?" + NAME + " (?:a|con|hacia) " + NAME, Pattern.CASE_INSENSITIVE);
    private final ObjectMapper mapper;

    public LocalCommandParser(ObjectMapper mapper) { this.mapper = mapper; }

    public Optional<DiagramOperationRequest> parse(String raw, DiagramDocument diagram) {
        if (raw == null || raw.contains(";") || raw.contains("\n")) return Optional.empty();
        String command = normalize(raw.trim());
        Matcher m;
        if ((m = CREATE_CLASS.matcher(command)).matches()) {
            ObjectNode payload = mapper.createObjectNode(); payload.put("id", UUID.randomUUID().toString()); payload.put("name", m.group(1));
            payload.putArray("attributes"); payload.putObject("position").put("x", 160 + diagram.classes().size() * 35).put("y", 120 + diagram.classes().size() * 30); payload.put("version", 1);
            return Optional.of(create(diagram, "CLASS_CREATED", payload, null));
        }
        if ((m = RENAME_CLASS.matcher(command)).matches()) { var value = findClass(diagram, m.group(1)); return Optional.of(create(diagram, "CLASS_RENAMED", object("id", value.id().toString(), "name", m.group(2)), value.version())); }
        if ((m = MOVE_CLASS.matcher(command)).matches()) { var value = findClass(diagram, m.group(1)); ObjectNode payload = object("id", value.id().toString()); payload.put("x", Double.parseDouble(m.group(2))); payload.put("y", Double.parseDouble(m.group(3))); return Optional.of(create(diagram, "CLASS_MOVED", payload, value.version())); }
        if ((m = DELETE_CLASS.matcher(command)).matches()) { var value = findClass(diagram, m.group(1)); return Optional.of(create(diagram, "CLASS_DELETED", object("id", value.id().toString()), value.version())); }
        if ((m = ADD_ATTRIBUTE.matcher(command)).matches()) {
            String type = scalar(m.group(2)); if (type == null) return Optional.empty();
            var owner = findClass(diagram, m.group(3)); ObjectNode attribute = object("id", UUID.randomUUID().toString(), "name", m.group(1), "type", type);
            attribute.put("primaryKey", false); attribute.put("required", false); attribute.put("unique", false); attribute.put("version", 1);
            ObjectNode payload = object("classId", owner.id().toString()); payload.set("attribute", attribute);
            return Optional.of(create(diagram, "ATTRIBUTE_CREATED", payload, owner.version()));
        }
        if ((m = CHANGE_ATTRIBUTE.matcher(command)).matches()) {
            String type = scalar(m.group(3)); if (type == null) return Optional.empty();
            var owner = findClass(diagram, m.group(2)); var attribute = findAttribute(owner, m.group(1)); ObjectNode updated = mapper.valueToTree(attribute); updated.put("type", type);
            ObjectNode payload = object("classId", owner.id().toString()); payload.set("attribute", updated);
            return Optional.of(create(diagram, "ATTRIBUTE_UPDATED", payload, attribute.version()));
        }
        if ((m = DELETE_ATTRIBUTE.matcher(command)).matches()) { var owner = findClass(diagram, m.group(2)); var attribute = findAttribute(owner, m.group(1)); return Optional.of(create(diagram, "ATTRIBUTE_DELETED", object("classId", owner.id().toString(), "id", attribute.id().toString()), attribute.version())); }
        if ((m = CREATE_ASSOCIATION.matcher(command)).matches()) {
            var source = findClass(diagram, m.group(1)); var target = findClass(diagram, m.group(2)); ObjectNode payload = object("id", UUID.randomUUID().toString(), "sourceId", source.id().toString(), "targetId", target.id().toString(), "sourceCardinality", "1", "targetCardinality", "0..*", "owningSide", "SOURCE"); payload.put("version", 1);
            return Optional.of(create(diagram, "ASSOCIATION_CREATED", payload, null));
        }
        if ((m = DELETE_ASSOCIATION.matcher(command)).matches()) { var link = findAssociation(diagram, m.group(1), m.group(2)); return Optional.of(create(diagram, "ASSOCIATION_DELETED", object("id", link.id().toString()), link.version())); }
        if ((m = CREATE_ENUM.matcher(command)).matches()) {
            ObjectNode payload = object("id", UUID.randomUUID().toString(), "name", m.group(1)); var values = payload.putArray("values");
            if (m.group(2) != null) for (String name : m.group(2).split("\\s*,\\s*|\\s+y\\s+")) values.addObject().put("id", UUID.randomUUID().toString()).put("name", name.trim()).put("version", 1);
            payload.putObject("position").put("x", 220 + diagram.enumerations().size() * 35).put("y", 180 + diagram.enumerations().size() * 30); payload.put("version", 1);
            return Optional.of(create(diagram, "ENUMERATION_CREATED", payload, null));
        }
        if ((m = DELETE_ENUM.matcher(command)).matches()) { var value = findEnumeration(diagram, m.group(1)); return Optional.of(create(diagram, "ENUMERATION_DELETED", object("id", value.id().toString()), value.version())); }
        if ((m = CREATE_INHERITANCE.matcher(command)).matches()) { var child = findClass(diagram, m.group(1)); var parent = findClass(diagram, m.group(2)); ObjectNode payload = object("id", UUID.randomUUID().toString(), "parentId", parent.id().toString(), "childId", child.id().toString()); payload.put("version", 1); return Optional.of(create(diagram, "GENERALIZATION_CREATED", payload, null)); }
        if ((m = DELETE_INHERITANCE.matcher(command)).matches()) { var child = findClass(diagram, m.group(1)); var parent = findClass(diagram, m.group(2)); var link = diagram.generalizations().stream().filter(v -> v.childId().equals(child.id()) && v.parentId().equals(parent.id())).findFirst().orElseThrow(() -> new IllegalArgumentException("Herencia no encontrada")); return Optional.of(create(diagram, "GENERALIZATION_DELETED", object("id", link.id().toString()), link.version())); }
        return Optional.empty();
    }

    private DiagramOperationRequest create(DiagramDocument diagram, String type, ObjectNode payload, Long expected) { return new DiagramOperationRequest(UUID.randomUUID(), diagram.revision(), expected, type, payload); }
    private ObjectNode object(String... pairs) { ObjectNode value = mapper.createObjectNode(); for (int i = 0; i < pairs.length; i += 2) value.put(pairs[i], pairs[i + 1]); return value; }
    private DiagramDocument.ClassElement findClass(DiagramDocument d, String name) { return d.classes().stream().filter(v -> v.name().equalsIgnoreCase(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Clase no encontrada: " + name)); }
    private DiagramDocument.Attribute findAttribute(DiagramDocument.ClassElement c, String name) { return c.attributes().stream().filter(v -> v.name().equalsIgnoreCase(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Atributo no encontrado: " + name)); }
    private DiagramDocument.Enumeration findEnumeration(DiagramDocument d, String name) { return d.enumerations().stream().filter(v -> v.name().equalsIgnoreCase(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Enumeración no encontrada: " + name)); }
    private DiagramDocument.Association findAssociation(DiagramDocument d, String a, String b) { var one = findClass(d, a); var two = findClass(d, b); return d.associations().stream().filter(v -> (v.sourceId().equals(one.id()) && v.targetId().equals(two.id())) || (v.sourceId().equals(two.id()) && v.targetId().equals(one.id()))).findFirst().orElseThrow(() -> new IllegalArgumentException("Relación no encontrada")); }
    private String scalar(String raw) { return switch (raw.toLowerCase(Locale.ROOT)) { case "texto", "string", "varchar", "char" -> "String"; case "entero", "integer", "int", "smallint" -> "Integer"; case "largo", "long", "bigint" -> "Long"; case "decimal", "numeric", "float", "double", "real", "money" -> "Decimal"; case "booleano", "boolean", "bool" -> "Boolean"; case "fecha", "date" -> "Date"; case "fechahora", "datetime", "timestamp" -> "DateTime"; case "uuid" -> "UUID"; case "binario", "binary", "blob", "bytea" -> "Binary"; default -> null; }; }
    private String normalize(String value) { return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", ""); }
}
