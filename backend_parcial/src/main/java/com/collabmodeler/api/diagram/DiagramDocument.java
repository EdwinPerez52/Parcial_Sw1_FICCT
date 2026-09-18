package com.collabmodeler.api.diagram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Canonical JSON contract for a UML diagram. */
public record DiagramDocument(
    UUID id,
    String name,
    long revision,
    List<ClassElement> classes,
    List<Enumeration> enumerations,
    List<Association> associations,
    List<Generalization> generalizations,
    List<PackageElement> packages
) {
    public DiagramDocument {
        classes = classes == null ? new ArrayList<>() : new ArrayList<>(classes);
        enumerations = enumerations == null ? new ArrayList<>() : new ArrayList<>(enumerations);
        associations = associations == null ? new ArrayList<>() : new ArrayList<>(associations);
        generalizations = generalizations == null ? new ArrayList<>() : new ArrayList<>(generalizations);
        packages = packages == null ? new ArrayList<>() : new ArrayList<>(packages);
    }

    public DiagramDocument(UUID id, String name, long revision, List<ClassElement> classes,
                           List<Enumeration> enumerations, List<Association> associations,
                           List<Generalization> generalizations) {
        this(id, name, revision, classes, enumerations, associations, generalizations, List.of());
    }

    public DiagramDocument(UUID id, String name, long revision, List<ClassElement> classes,
                           List<Association> associations) {
        this(id, name, revision, classes, List.of(), associations, List.of());
    }

    public record Position(double x, double y) {}

    public record Attribute(
        UUID id, String name, String type, boolean primaryKey, boolean required,
        boolean unique, long version
    ) {
        public Attribute {
            if (version < 1) version = 1;
        }

        public Attribute(UUID id, String name, String type, boolean primaryKey, boolean required, boolean unique) {
            this(id, name, type, primaryKey, required, unique, 1);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClassElement(UUID id, String name, List<Attribute> attributes, Position position, long version) {
        public ClassElement {
            attributes = attributes == null ? new ArrayList<>() : new ArrayList<>(attributes);
            position = position == null ? new Position(0, 0) : position;
        }
    }

    public record EnumerationValue(UUID id, String name, long version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Enumeration(
        UUID id, String name, List<EnumerationValue> values, Position position, long version
    ) {
        public Enumeration {
            values = values == null ? new ArrayList<>() : new ArrayList<>(values);
            position = position == null ? new Position(0, 0) : position;
        }
    }

    public record Association(
        UUID id, UUID sourceId, UUID targetId, String sourceCardinality,
        String targetCardinality, String name, String sourceRole, String targetRole,
        String owningSide, long version
    ) {
        public Association {
            owningSide = owningSide == null || owningSide.isBlank() ? "SOURCE" : owningSide;
            if (version < 1) version = 1;
        }

        public Association(UUID id, UUID sourceId, UUID targetId, String sourceCardinality,
                           String targetCardinality, String name, long version) {
            this(id, sourceId, targetId, sourceCardinality, targetCardinality, name, null, null, "SOURCE", version);
        }
    }

    public record Generalization(UUID id, UUID parentId, UUID childId, long version) {}

    /** UML package containment. memberIds can reference classes, enumerations or associations. */
    public record PackageElement(
        UUID id, String name, UUID parentId, List<UUID> memberIds, long version
    ) {
        public PackageElement {
            memberIds = memberIds == null ? new ArrayList<>() : new ArrayList<>(memberIds);
            if (version < 1) version = 1;
        }
    }
}
