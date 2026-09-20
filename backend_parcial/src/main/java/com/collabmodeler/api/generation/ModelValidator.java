package com.collabmodeler.api.generation;

import com.collabmodeler.api.diagram.DiagramDocument;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ModelValidator {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");
    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

    private static final Set<String> SCALAR_TYPES = Set.of(
        "String", "Integer", "Long", "Decimal", "Boolean", "Date", "DateTime", "UUID", "Binary", "Text"
    );

    private static final Set<String> VALID_CARDINALITIES = Set.of(
        "1", "0..1", "0..*", "1..*", "0..N", "1..N", "*"
    );

    private static final Set<String> RESERVED_AUTH_NAMES = Set.of(
        "authuser", "authrole", "authrefreshtoken", "_app_auth_users", "_app_auth_tokens"
    );

    public void validate(DiagramDocument diagram, String groupId, String artifactId) {
        if (groupId == null || !GROUP_ID_PATTERN.matcher(groupId).matches()) {
            throw new ModelValidationException(diagram != null ? diagram.id() : null, "INVALID_GROUP_ID", "groupId inválido: " + groupId);
        }
        if (artifactId == null || !ARTIFACT_ID_PATTERN.matcher(artifactId).matches()) {
            throw new ModelValidationException(diagram != null ? diagram.id() : null, "INVALID_ARTIFACT_ID", "artifactId inválido: " + artifactId);
        }
        if (diagram == null) {
            throw new ModelValidationException(null, "NULL_DIAGRAM", "El diagrama no puede ser nulo");
        }

        Map<UUID, DiagramDocument.ClassElement> classMap = new HashMap<>();
        Set<String> classNamesLower = new HashSet<>();

        // 1. Validate enumerations
        Set<String> enumNames = new HashSet<>();
        for (var enumeration : diagram.enumerations()) {
            if (enumeration.name() == null || !IDENTIFIER_PATTERN.matcher(enumeration.name()).matches()) {
                throw new ModelValidationException(enumeration.id(), "INVALID_ENUM_NAME", "Nombre de enumeración inválido: " + enumeration.name());
            }
            if (!enumNames.add(enumeration.name().toLowerCase(Locale.ROOT))) {
                throw new ModelValidationException(enumeration.id(), "DUPLICATE_ENUM_NAME", "Nombre de enumeración duplicado: " + enumeration.name());
            }
            if (enumeration.values() == null || enumeration.values().isEmpty()) {
                throw new ModelValidationException(enumeration.id(), "EMPTY_ENUM", "La enumeración debe tener al menos un valor: " + enumeration.name());
            }
            Set<String> valueNames = new HashSet<>();
            for (var val : enumeration.values()) {
                if (val.name() == null || !val.name().matches("^[A-Za-z0-9_]+$")) {
                    throw new ModelValidationException(val.id(), "INVALID_ENUM_VALUE", "Valor de enumeración inválido: " + val.name());
                }
                if (!valueNames.add(val.name().toUpperCase(Locale.ROOT))) {
                    throw new ModelValidationException(val.id(), "DUPLICATE_ENUM_VALUE", "Valor de enumeración duplicado en " + enumeration.name() + ": " + val.name());
                }
            }
        }

        // 2. Validate classes and attributes
        for (var item : diagram.classes()) {
            if (item.name() == null || !IDENTIFIER_PATTERN.matcher(item.name()).matches()) {
                throw new ModelValidationException(item.id(), "INVALID_CLASS_NAME", "Nombre de clase inválido: " + item.name());
            }
            String lowerName = item.name().toLowerCase(Locale.ROOT);
            if (RESERVED_AUTH_NAMES.contains(lowerName)) {
                throw new ModelValidationException(item.id(), "RESERVED_AUTH_NAME_COLLISION", "El nombre de clase '" + item.name() + "' colisiona con el esquema aislado de autenticación");
            }
            if (!classNamesLower.add(lowerName)) {
                throw new ModelValidationException(item.id(), "DUPLICATE_CLASS_NAME", "Nombre de clase duplicado: " + item.name());
            }
            classMap.put(item.id(), item);

            Set<String> attrNamesLower = new HashSet<>();
            for (var attribute : item.attributes()) {
                if (attribute.name() == null || !IDENTIFIER_PATTERN.matcher(attribute.name()).matches()) {
                    throw new ModelValidationException(attribute.id(), "INVALID_ATTRIBUTE_NAME", "Nombre de atributo inválido: " + attribute.name() + " en clase " + item.name());
                }
                if (!attrNamesLower.add(attribute.name().toLowerCase(Locale.ROOT))) {
                    throw new ModelValidationException(attribute.id(), "DUPLICATE_ATTRIBUTE_NAME", "Nombre de atributo duplicado: " + attribute.name() + " en clase " + item.name());
                }
                if (!SCALAR_TYPES.contains(attribute.type()) && !enumNames.contains(attribute.type().toLowerCase(Locale.ROOT))) {
                    throw new ModelValidationException(attribute.id(), "UNKNOWN_ATTRIBUTE_TYPE", "Tipo no reconocido '" + attribute.type() + "' para el atributo " + attribute.name());
                }
            }
        }

        // 3. Validate generalizations and inheritance cycles
        Map<UUID, UUID> childToParent = new HashMap<>();
        for (var gen : diagram.generalizations()) {
            if (!classMap.containsKey(gen.parentId())) {
                throw new ModelValidationException(gen.id(), "UNKNOWN_PARENT_CLASS", "Clase padre inexistente en generalización: " + gen.parentId());
            }
            if (!classMap.containsKey(gen.childId())) {
                throw new ModelValidationException(gen.id(), "UNKNOWN_CHILD_CLASS", "Clase hija inexistente en generalización: " + gen.childId());
            }
            if (gen.parentId().equals(gen.childId())) {
                throw new ModelValidationException(gen.id(), "SELF_INHERITANCE", "Una clase no puede heredar de sí misma: " + classMap.get(gen.childId()).name());
            }
            if (childToParent.put(gen.childId(), gen.parentId()) != null) {
                throw new ModelValidationException(gen.id(), "MULTIPLE_INHERITANCE_UNSUPPORTED", "Herencia múltiple no soportada para clase " + classMap.get(gen.childId()).name());
            }
        }

        // Cycle detection in generalizations
        for (var childId : childToParent.keySet()) {
            Set<UUID> visited = new HashSet<>();
            UUID current = childId;
            while (current != null) {
                if (!visited.add(current)) {
                    throw new ModelValidationException(childId, "INHERITANCE_CYCLE", "Ciclo de herencia detectado involucrando la clase " + classMap.get(childId).name());
                }
                current = childToParent.get(current);
            }
        }

        // 4. Validate primary keys
        for (var item : diagram.classes()) {
            boolean isChild = childToParent.containsKey(item.id());
            long primaryKeys = item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).count();
            if (!isChild) {
                if (primaryKeys != 1) {
                    throw new ModelValidationException(item.id(), "INVALID_PRIMARY_KEY", "La clase " + item.name() + " debe tener exactamente una clave primaria (tiene " + primaryKeys + ")");
                }
            } else {
                // If it is a child in JOINED inheritance, it inherits the PK from parent, or can declare a matching PK
                if (primaryKeys > 1) {
                    throw new ModelValidationException(item.id(), "INVALID_PRIMARY_KEY", "La clase hija " + item.name() + " no puede tener múltiples claves primarias");
                }
            }
        }

        // 5. Validate associations
        for (var assoc : diagram.associations()) {
            if (!classMap.containsKey(assoc.sourceId())) {
                throw new ModelValidationException(assoc.id(), "UNKNOWN_SOURCE_CLASS", "Clase origen inexistente en asociación: " + assoc.sourceId());
            }
            if (!classMap.containsKey(assoc.targetId())) {
                throw new ModelValidationException(assoc.id(), "UNKNOWN_TARGET_CLASS", "Clase destino inexistente en asociación: " + assoc.targetId());
            }
            if (!VALID_CARDINALITIES.contains(assoc.sourceCardinality())) {
                throw new ModelValidationException(assoc.id(), "INVALID_SOURCE_CARDINALITY", "Cardinalidad origen inválida: " + assoc.sourceCardinality());
            }
            if (!VALID_CARDINALITIES.contains(assoc.targetCardinality())) {
                throw new ModelValidationException(assoc.id(), "INVALID_TARGET_CARDINALITY", "Cardinalidad destino inválida: " + assoc.targetCardinality());
            }
            String owning = assoc.owningSide() != null ? assoc.owningSide().toUpperCase(Locale.ROOT) : "SOURCE";
            if (!"SOURCE".equals(owning) && !"TARGET".equals(owning)) {
                throw new ModelValidationException(assoc.id(), "INVALID_OWNING_SIDE", "owningSide debe ser SOURCE o TARGET: " + assoc.owningSide());
            }
        }
    }
}
