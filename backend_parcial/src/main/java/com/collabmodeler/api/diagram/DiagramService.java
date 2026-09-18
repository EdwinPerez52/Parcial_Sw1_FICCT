package com.collabmodeler.api.diagram;

import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.support.ConflictException;
import com.collabmodeler.api.support.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DiagramService {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SCALAR_TYPES = Set.of(
        "String", "Text", "Integer", "Long", "Decimal", "Boolean", "Date", "DateTime", "UUID", "Binary"
    );
    private static final Set<String> CARDINALITIES = Set.of("0..1", "1", "0..*", "1..*");
    private static final Set<String> OWNING_SIDES = Set.of("SOURCE", "TARGET");

    private final DiagramRepository diagrams;
    private final DiagramOperationRepository operations;
    private final ObjectMapper mapper;
    private final AccessService access;

    public DiagramService(DiagramRepository diagrams, DiagramOperationRepository operations, ObjectMapper mapper, AccessService access) {
        this.diagrams = diagrams;
        this.operations = operations;
        this.mapper = mapper;
        this.access = access;
    }

    @Transactional
    public DiagramDocument create(String name, String ownerSubject, String ownerName) {
        if (name == null || name.isBlank() || name.length() > 180) {
            throw new IllegalArgumentException("El nombre del diagrama es obligatorio y admite hasta 180 caracteres");
        }
        UUID id = UUID.randomUUID();
        DiagramDocument document = new DiagramDocument(id, name.trim(), 0, List.of(), List.of(), List.of(), List.of(), List.of());
        diagrams.save(new DiagramEntity(id, document.name(), write(document), ownerSubject));
        access.addOwner(id, ownerSubject, ownerName);
        return document;
    }

    @Transactional(readOnly = true)
    public DiagramDocument get(UUID id) {
        return read(diagrams.findById(id).orElseThrow(() -> new NotFoundException("Diagrama no encontrado")).getModelJson());
    }

    @Transactional(readOnly = true)
    public List<DiagramOperationEntity> operationsSince(UUID id, long revision) {
        if (!diagrams.existsById(id)) throw new NotFoundException("Diagrama no encontrado");
        return operations.findByDiagramIdAndResultRevisionGreaterThanOrderByResultRevision(id, revision);
    }

    @Transactional
    public DiagramDocument apply(UUID id, DiagramOperationRequest request, String authorSubject, String authorName) {
        validateRequest(request);
        DiagramEntity entity = diagrams.findForUpdate(id).orElseThrow(() -> new NotFoundException("Diagrama no encontrado"));
        if (operations.existsById(request.operationId())) return read(entity.getModelJson());
        if (request.baseRevision() > entity.getRevision()) {
            throw conflict("BASE_REVISION_AHEAD", "La revisión base está adelantada al servidor",
                entity.getRevision(), null, null);
        }

        DiagramDocument current = read(entity.getModelJson());
        DiagramDocument updated = applyOperation(current, request, entity.getRevision(), false);
        validateDocument(updated);
        long nextRevision = entity.getRevision() + 1;
        updated = withRevision(updated, nextRevision);
        entity.updateModel(nextRevision, write(updated));
        operations.save(new DiagramOperationEntity(request.operationId(), id, request.baseRevision(), nextRevision,
            request.type(), request.payload().toString(), authorSubject, authorName));
        return updated;
    }

    private DiagramDocument applyOperation(DiagramDocument current, DiagramOperationRequest request,
                                           long serverRevision, boolean nested) {
        validateRequest(request);
        if (request.baseRevision() > serverRevision) {
            throw conflict("BASE_REVISION_AHEAD", "La revisión base está adelantada al servidor",
                serverRevision, null, null);
        }
        List<DiagramDocument.ClassElement> classes = new ArrayList<>(current.classes());
        List<DiagramDocument.Enumeration> enumerations = new ArrayList<>(current.enumerations());
        List<DiagramDocument.Association> associations = new ArrayList<>(current.associations());
        List<DiagramDocument.Generalization> generalizations = new ArrayList<>(current.generalizations());
        List<DiagramDocument.PackageElement> packages = new ArrayList<>(current.packages());
        JsonNode payload = request.payload();

        switch (request.type()) {
            case "CLASS_CREATED" -> {
                DiagramDocument.ClassElement value = mapper.convertValue(payload, DiagramDocument.ClassElement.class);
                classes.add(new DiagramDocument.ClassElement(requiredId(value.id()), value.name(),
                    normalizeAttributes(value.attributes()), value.position(), 1));
            }
            case "CLASS_RENAMED" -> {
                UUID id = uuid(payload, "id");
                int index = classIndex(classes, id);
                DiagramDocument.ClassElement old = classes.get(index);
                assertVersion(old.version(), requireExpected(request, id), serverRevision, id);
                classes.set(index, new DiagramDocument.ClassElement(old.id(), text(payload, "name"),
                    old.attributes(), old.position(), old.version() + 1));
            }
            case "CLASS_MOVED" -> {
                UUID id = uuid(payload, "id");
                int index = classIndex(classes, id);
                DiagramDocument.ClassElement old = classes.get(index);
                assertVersion(old.version(), requireExpected(request, id), serverRevision, id);
                classes.set(index, new DiagramDocument.ClassElement(old.id(), old.name(), old.attributes(),
                    new DiagramDocument.Position(number(payload, "x"), number(payload, "y")), old.version() + 1));
            }
            case "CLASS_DELETED" -> {
                UUID id = uuid(payload, "id");
                int index = classIndex(classes, id);
                assertVersion(classes.get(index).version(), requireExpected(request, id), serverRevision, id);
                classes.remove(index);
                Set<UUID> removedAssociationIds = associations.stream()
                    .filter(link -> link.sourceId().equals(id) || link.targetId().equals(id))
                    .map(DiagramDocument.Association::id).collect(java.util.stream.Collectors.toSet());
                associations.removeIf(link -> link.sourceId().equals(id) || link.targetId().equals(id));
                generalizations.removeIf(link -> link.parentId().equals(id) || link.childId().equals(id));
                packages.replaceAll(value -> withoutPackageMember(value, id));
                removedAssociationIds.forEach(associationId ->
                    packages.replaceAll(value -> withoutPackageMember(value, associationId)));
            }
            case "ATTRIBUTE_CREATED" -> {
                UUID classId = uuid(payload, "classId");
                int index = classIndex(classes, classId);
                DiagramDocument.ClassElement owner = classes.get(index);
                assertVersion(owner.version(), requireExpected(request, classId), serverRevision, classId);
                var attributes = new ArrayList<>(owner.attributes());
                DiagramDocument.Attribute value = mapper.convertValue(payload.path("attribute"), DiagramDocument.Attribute.class);
                attributes.add(new DiagramDocument.Attribute(requiredId(value.id()), value.name(), value.type(),
                    value.primaryKey(), value.required(), value.unique(), 1));
                classes.set(index, copyClass(owner, attributes, owner.version() + 1));
            }
            case "ATTRIBUTE_UPDATED" -> {
                UUID classId = uuid(payload, "classId");
                int ownerIndex = classIndex(classes, classId);
                DiagramDocument.ClassElement owner = classes.get(ownerIndex);
                DiagramDocument.Attribute value = mapper.convertValue(payload.path("attribute"), DiagramDocument.Attribute.class);
                int attributeIndex = attributeIndex(owner.attributes(), requiredId(value.id()));
                DiagramDocument.Attribute old = owner.attributes().get(attributeIndex);
                assertVersion(old.version(), requireExpected(request, old.id()), serverRevision, old.id());
                var attributes = new ArrayList<>(owner.attributes());
                attributes.set(attributeIndex, new DiagramDocument.Attribute(old.id(), value.name(), value.type(),
                    value.primaryKey(), value.required(), value.unique(), old.version() + 1));
                classes.set(ownerIndex, copyClass(owner, attributes, owner.version() + 1));
            }
            case "ATTRIBUTE_REORDERED" -> {
                UUID classId = uuid(payload, "classId");
                int ownerIndex = classIndex(classes, classId);
                DiagramDocument.ClassElement owner = classes.get(ownerIndex);
                assertVersion(owner.version(), requireExpected(request, classId), serverRevision, classId);
                var attributes = new ArrayList<>(owner.attributes());
                DiagramDocument.Attribute moved = attributes.remove(attributeIndex(attributes, uuid(payload, "attributeId")));
                int newIndex = payload.path("newIndex").asInt(-1);
                if (newIndex < 0 || newIndex > attributes.size()) throw new IllegalArgumentException("Posición de atributo inválida");
                attributes.add(newIndex, moved);
                classes.set(ownerIndex, copyClass(owner, attributes, owner.version() + 1));
            }
            case "ATTRIBUTE_DELETED" -> {
                UUID classId = uuid(payload, "classId");
                UUID id = uuid(payload, "id");
                int ownerIndex = classIndex(classes, classId);
                DiagramDocument.ClassElement owner = classes.get(ownerIndex);
                int index = attributeIndex(owner.attributes(), id);
                assertVersion(owner.attributes().get(index).version(), requireExpected(request, id), serverRevision, id);
                var attributes = new ArrayList<>(owner.attributes());
                attributes.remove(index);
                classes.set(ownerIndex, copyClass(owner, attributes, owner.version() + 1));
            }
            case "ASSOCIATION_CREATED" -> {
                DiagramDocument.Association value = mapper.convertValue(payload, DiagramDocument.Association.class);
                associations.add(copyAssociation(value, 1));
            }
            case "ASSOCIATION_UPDATED" -> {
                DiagramDocument.Association value = mapper.convertValue(payload, DiagramDocument.Association.class);
                int index = associationIndex(associations, requiredId(value.id()));
                DiagramDocument.Association old = associations.get(index);
                assertVersion(old.version(), requireExpected(request, old.id()), serverRevision, old.id());
                associations.set(index, copyAssociation(value, old.version() + 1));
            }
            case "ASSOCIATION_DELETED" -> {
                UUID id = uuid(payload, "id");
                int index = associationIndex(associations, id);
                assertVersion(associations.get(index).version(), requireExpected(request, id), serverRevision, id);
                associations.remove(index);
                packages.replaceAll(value -> withoutPackageMember(value, id));
            }
            case "ENUMERATION_CREATED" -> {
                DiagramDocument.Enumeration value = mapper.convertValue(payload, DiagramDocument.Enumeration.class);
                enumerations.add(new DiagramDocument.Enumeration(requiredId(value.id()), value.name(),
                    normalizeEnumValues(value.values()), value.position(), 1));
            }
            case "ENUMERATION_UPDATED" -> {
                DiagramDocument.Enumeration value = mapper.convertValue(payload, DiagramDocument.Enumeration.class);
                int index = enumerationIndex(enumerations, requiredId(value.id()));
                DiagramDocument.Enumeration old = enumerations.get(index);
                assertVersion(old.version(), requireExpected(request, old.id()), serverRevision, old.id());
                enumerations.set(index, new DiagramDocument.Enumeration(old.id(), value.name(),
                    mergeEnumValueVersions(old.values(), value.values()), value.position(), old.version() + 1));
            }
            case "ENUMERATION_VALUE_CREATED" -> {
                UUID enumerationId = uuid(payload, "enumerationId");
                int index = enumerationIndex(enumerations, enumerationId);
                DiagramDocument.Enumeration old = enumerations.get(index);
                assertVersion(old.version(), requireExpected(request, enumerationId), serverRevision, enumerationId);
                DiagramDocument.EnumerationValue value = mapper.convertValue(payload.path("value"), DiagramDocument.EnumerationValue.class);
                var values = new ArrayList<>(old.values());
                values.add(new DiagramDocument.EnumerationValue(requiredId(value.id()), value.name(), 1));
                enumerations.set(index, new DiagramDocument.Enumeration(old.id(), old.name(), values, old.position(), old.version() + 1));
            }
            case "ENUMERATION_VALUE_UPDATED" -> {
                UUID enumerationId = uuid(payload, "enumerationId");
                int index = enumerationIndex(enumerations, enumerationId);
                DiagramDocument.Enumeration old = enumerations.get(index);
                DiagramDocument.EnumerationValue value = mapper.convertValue(payload.path("value"), DiagramDocument.EnumerationValue.class);
                int valueIndex = enumerationValueIndex(old.values(), requiredId(value.id()));
                DiagramDocument.EnumerationValue previous = old.values().get(valueIndex);
                assertVersion(previous.version(), requireExpected(request, previous.id()), serverRevision, previous.id());
                var values = new ArrayList<>(old.values());
                values.set(valueIndex, new DiagramDocument.EnumerationValue(previous.id(), value.name(), previous.version() + 1));
                enumerations.set(index, new DiagramDocument.Enumeration(old.id(), old.name(), values, old.position(), old.version() + 1));
            }
            case "ENUMERATION_VALUE_DELETED" -> {
                UUID enumerationId = uuid(payload, "enumerationId");
                UUID id = uuid(payload, "id");
                int index = enumerationIndex(enumerations, enumerationId);
                DiagramDocument.Enumeration old = enumerations.get(index);
                int valueIndex = enumerationValueIndex(old.values(), id);
                assertVersion(old.values().get(valueIndex).version(), requireExpected(request, id), serverRevision, id);
                var values = new ArrayList<>(old.values()); values.remove(valueIndex);
                enumerations.set(index, new DiagramDocument.Enumeration(old.id(), old.name(), values, old.position(), old.version() + 1));
            }
            case "ENUMERATION_DELETED" -> {
                UUID id = uuid(payload, "id");
                int index = enumerationIndex(enumerations, id);
                assertVersion(enumerations.get(index).version(), requireExpected(request, id), serverRevision, id);
                String name = enumerations.get(index).name();
                boolean used = classes.stream().flatMap(item -> item.attributes().stream())
                    .anyMatch(attribute -> attribute.type().equals(name) || attribute.type().equals(id.toString()));
                if (used) throw new IllegalArgumentException("No se puede eliminar una enumeración utilizada por atributos");
                enumerations.remove(index);
                packages.replaceAll(value -> withoutPackageMember(value, id));
            }
            case "GENERALIZATION_CREATED" -> {
                DiagramDocument.Generalization value = mapper.convertValue(payload, DiagramDocument.Generalization.class);
                generalizations.add(new DiagramDocument.Generalization(requiredId(value.id()), value.parentId(), value.childId(), 1));
            }
            case "GENERALIZATION_DELETED" -> {
                UUID id = uuid(payload, "id");
                int index = generalizationIndex(generalizations, id);
                assertVersion(generalizations.get(index).version(), requireExpected(request, id), serverRevision, id);
                generalizations.remove(index);
            }
            case "PACKAGE_CREATED" -> {
                DiagramDocument.PackageElement value = mapper.convertValue(payload, DiagramDocument.PackageElement.class);
                packages.add(new DiagramDocument.PackageElement(requiredId(value.id()), value.name(), value.parentId(),
                    value.memberIds(), 1));
            }
            case "PACKAGE_UPDATED" -> {
                DiagramDocument.PackageElement value = mapper.convertValue(payload, DiagramDocument.PackageElement.class);
                int index = packageIndex(packages, requiredId(value.id()));
                DiagramDocument.PackageElement old = packages.get(index);
                assertVersion(old.version(), requireExpected(request, old.id()), serverRevision, old.id());
                packages.set(index, new DiagramDocument.PackageElement(old.id(), value.name(), value.parentId(),
                    value.memberIds(), old.version() + 1));
            }
            case "PACKAGE_DELETED" -> {
                UUID id = uuid(payload, "id");
                int index = packageIndex(packages, id);
                DiagramDocument.PackageElement value = packages.get(index);
                assertVersion(value.version(), requireExpected(request, id), serverRevision, id);
                if (!value.memberIds().isEmpty() || packages.stream().anyMatch(item -> id.equals(item.parentId()))) {
                    throw new IllegalArgumentException("No se puede eliminar un paquete que todavÃ­a contiene elementos");
                }
                packages.remove(index);
            }
            case "BATCH" -> {
                if (nested) throw new IllegalArgumentException("No se permiten lotes anidados");
                JsonNode children = payload.path("operations");
                if (!children.isArray() || children.isEmpty()) throw new IllegalArgumentException("El lote debe incluir operaciones");
                DiagramDocument result = current;
                Set<UUID> childOperationIds = new HashSet<>();
                for (JsonNode child : children) {
                    DiagramOperationRequest nestedRequest = mapper.convertValue(child, DiagramOperationRequest.class);
                    if (nestedRequest.operationId() == null) throw new IllegalArgumentException("Cada operación del lote requiere operationId");
                    if (!childOperationIds.add(nestedRequest.operationId())) throw new IllegalArgumentException("operationId duplicado dentro del lote");
                    result = applyOperation(result, nestedRequest, serverRevision, true);
                }
                return result;
            }
            case "MODEL_RESTORED" -> throw new IllegalArgumentException("MODEL_RESTORED solo puede crearse al restaurar un hito persistente");
            default -> throw new IllegalArgumentException("Tipo de operación no soportado: " + request.type());
        }
        return new DiagramDocument(current.id(), current.name(), current.revision(),
            classes, enumerations, associations, generalizations, packages);
    }

    private void validateDocument(DiagramDocument document) {
        Set<UUID> ids = new HashSet<>();
        Set<String> typeNames = new HashSet<>();
        for (DiagramDocument.ClassElement item : document.classes()) {
            requireUniqueId(ids, item.id());
            validateName(item.name(), "clase");
            requireUniqueName(typeNames, item.name(), "tipo");
            Set<String> attributeNames = new HashSet<>();
            for (DiagramDocument.Attribute attribute : item.attributes()) {
                requireUniqueId(ids, attribute.id());
                validateName(attribute.name(), "atributo");
                requireUniqueName(attributeNames, attribute.name(), "atributo");
                if (attribute.type() == null || attribute.type().isBlank()) throw new IllegalArgumentException("El tipo del atributo es obligatorio");
            }
        }
        for (DiagramDocument.Enumeration item : document.enumerations()) {
            requireUniqueId(ids, item.id());
            validateName(item.name(), "enumeración");
            requireUniqueName(typeNames, item.name(), "tipo");
            Set<String> values = new HashSet<>();
            for (DiagramDocument.EnumerationValue value : item.values()) {
                requireUniqueId(ids, value.id());
                validateName(value.name(), "valor de enumeración");
                requireUniqueName(values, value.name(), "valor de enumeración");
            }
        }
        Set<String> allowedTypes = new HashSet<>(SCALAR_TYPES);
        document.enumerations().forEach(value -> {
            allowedTypes.add(value.name());
            allowedTypes.add(value.id().toString());
        });
        document.classes().stream().flatMap(item -> item.attributes().stream()).forEach(attribute -> {
            if (!allowedTypes.contains(attribute.type())) throw new IllegalArgumentException("Tipo de atributo no soportado: " + attribute.type());
        });

        Set<UUID> classIds = new HashSet<>();
        document.classes().forEach(item -> classIds.add(item.id()));
        Set<String> associationKeys = new HashSet<>();
        for (DiagramDocument.Association link : document.associations()) {
            requireUniqueId(ids, link.id());
            requireClass(classIds, link.sourceId());
            requireClass(classIds, link.targetId());
            if (!CARDINALITIES.contains(link.sourceCardinality()) || !CARDINALITIES.contains(link.targetCardinality())) {
                throw new IllegalArgumentException("Cardinalidad inválida");
            }
            if (!OWNING_SIDES.contains(link.owningSide())) throw new IllegalArgumentException("Lado propietario inválido");
            if (link.name() != null && !link.name().isBlank()) validateName(link.name(), "asociación");
            if (link.sourceRole() != null && !link.sourceRole().isBlank()) validateName(link.sourceRole(), "rol");
            if (link.targetRole() != null && !link.targetRole().isBlank()) validateName(link.targetRole(), "rol");
            String associationKey = link.sourceId() + ":" + link.targetId() + ":"
                + (link.name() == null ? "" : link.name().toLowerCase(Locale.ROOT));
            if (!associationKeys.add(associationKey)) throw new IllegalArgumentException("Asociación duplicada");
        }
        Set<String> inheritancePairs = new HashSet<>();
        for (DiagramDocument.Generalization link : document.generalizations()) {
            requireUniqueId(ids, link.id());
            requireClass(classIds, link.parentId());
            requireClass(classIds, link.childId());
            if (link.parentId().equals(link.childId())) throw new IllegalArgumentException("Una clase no puede heredarse a sí misma");
            if (!inheritancePairs.add(link.parentId() + ":" + link.childId())) throw new IllegalArgumentException("Generalización duplicada");
        }
        assertNoInheritanceCycles(document.classes(), document.generalizations());

        Set<UUID> packageIds = new HashSet<>();
        for (DiagramDocument.PackageElement item : document.packages()) {
            requireUniqueId(ids, item.id());
            packageIds.add(item.id());
            validateName(item.name(), "paquete");
        }
        Set<String> packageNames = new HashSet<>();
        Set<UUID> packageMembers = new HashSet<>();
        Set<UUID> containableIds = new HashSet<>();
        document.classes().forEach(item -> containableIds.add(item.id()));
        document.enumerations().forEach(item -> containableIds.add(item.id()));
        document.associations().forEach(item -> containableIds.add(item.id()));
        for (DiagramDocument.PackageElement item : document.packages()) {
            if (item.parentId() != null && !packageIds.contains(item.parentId())) {
                throw new IllegalArgumentException("Paquete padre inexistente: " + item.parentId());
            }
            requireUniqueName(packageNames, (item.parentId() == null ? "root" : item.parentId()) + ":" + item.name(), "paquete");
            for (UUID memberId : item.memberIds()) {
                if (!containableIds.contains(memberId)) throw new IllegalArgumentException("Elemento de paquete inexistente: " + memberId);
                if (!packageMembers.add(memberId)) throw new IllegalArgumentException("Un elemento no puede pertenecer a mÃ¡s de un paquete: " + memberId);
            }
            assertPackageParentChain(item.id(), document.packages(), new HashSet<>());
        }
    }

    private void assertNoInheritanceCycles(List<DiagramDocument.ClassElement> classes,
                                           List<DiagramDocument.Generalization> links) {
        for (DiagramDocument.ClassElement item : classes) {
            visitInheritance(item.id(), links, new HashSet<>(), new HashSet<>());
        }
    }

    private void visitInheritance(UUID id, List<DiagramDocument.Generalization> links,
                                  Set<UUID> path, Set<UUID> complete) {
        if (complete.contains(id)) return;
        if (!path.add(id)) throw new IllegalArgumentException("La herencia contiene un ciclo");
        links.stream().filter(link -> link.childId().equals(id))
            .forEach(link -> visitInheritance(link.parentId(), links, path, complete));
        path.remove(id);
        complete.add(id);
    }

    private DiagramDocument withRevision(DiagramDocument value, long revision) {
        return new DiagramDocument(value.id(), value.name(), revision, value.classes(), value.enumerations(),
            value.associations(), value.generalizations(), value.packages());
    }

    private DiagramDocument.ClassElement copyClass(DiagramDocument.ClassElement owner,
                                                   List<DiagramDocument.Attribute> attributes, long version) {
        return new DiagramDocument.ClassElement(owner.id(), owner.name(), attributes, owner.position(), version);
    }

    private DiagramDocument.Association copyAssociation(DiagramDocument.Association value, long version) {
        return new DiagramDocument.Association(requiredId(value.id()), value.sourceId(), value.targetId(),
            value.sourceCardinality(), value.targetCardinality(), value.name(), value.sourceRole(),
            value.targetRole(), value.owningSide() == null ? "SOURCE" : value.owningSide(), version);
    }

    private List<DiagramDocument.Attribute> normalizeAttributes(List<DiagramDocument.Attribute> attributes) {
        return attributes.stream().map(value -> new DiagramDocument.Attribute(requiredId(value.id()), value.name(),
            value.type(), value.primaryKey(), value.required(), value.unique(), Math.max(1, value.version()))).toList();
    }

    private List<DiagramDocument.EnumerationValue> normalizeEnumValues(List<DiagramDocument.EnumerationValue> values) {
        return values.stream().map(value -> new DiagramDocument.EnumerationValue(requiredId(value.id()),
            value.name(), Math.max(1, value.version()))).toList();
    }

    private List<DiagramDocument.EnumerationValue> mergeEnumValueVersions(
        List<DiagramDocument.EnumerationValue> oldValues, List<DiagramDocument.EnumerationValue> newValues
    ) {
        return newValues.stream().map(value -> {
            DiagramDocument.EnumerationValue old = oldValues.stream()
                .filter(candidate -> candidate.id().equals(value.id())).findFirst().orElse(null);
            return new DiagramDocument.EnumerationValue(requiredId(value.id()), value.name(),
                old == null ? 1 : old.version() + (old.name().equals(value.name()) ? 0 : 1));
        }).toList();
    }

    private int classIndex(List<DiagramDocument.ClassElement> values, UUID id) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(id)) return i;
        throw new NotFoundException("Clase no encontrada: " + id);
    }

    private int attributeIndex(List<DiagramDocument.Attribute> values, UUID id) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(id)) return i;
        throw new NotFoundException("Atributo no encontrado: " + id);
    }

    private int associationIndex(List<DiagramDocument.Association> values, UUID id) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(id)) return i;
        throw new NotFoundException("Asociación no encontrada: " + id);
    }

    private int enumerationIndex(List<DiagramDocument.Enumeration> values, UUID id) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(id)) return i;
        throw new NotFoundException("Enumeración no encontrada: " + id);
    }

    private int enumerationValueIndex(List<DiagramDocument.EnumerationValue> values, UUID id) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(id)) return i;
        throw new NotFoundException("Valor de enumeración no encontrado: " + id);
    }

    private int generalizationIndex(List<DiagramDocument.Generalization> values, UUID id) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(id)) return i;
        throw new NotFoundException("Generalización no encontrada: " + id);
    }

    private int packageIndex(List<DiagramDocument.PackageElement> values, UUID id) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id().equals(id)) return i;
        throw new NotFoundException("Paquete no encontrado: " + id);
    }

    private DiagramDocument.PackageElement withoutPackageMember(DiagramDocument.PackageElement value, UUID memberId) {
        if (!value.memberIds().contains(memberId)) return value;
        return new DiagramDocument.PackageElement(value.id(), value.name(), value.parentId(),
            value.memberIds().stream().filter(id -> !id.equals(memberId)).toList(), value.version() + 1);
    }

    private void assertPackageParentChain(UUID id, List<DiagramDocument.PackageElement> packages, Set<UUID> path) {
        if (!path.add(id)) throw new IllegalArgumentException("La jerarquÃ­a de paquetes contiene un ciclo");
        DiagramDocument.PackageElement value = packages.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
        if (value != null && value.parentId() != null) assertPackageParentChain(value.parentId(), packages, path);
        path.remove(id);
    }

    private Long requireExpected(DiagramOperationRequest request, UUID id) {
        if (request.expectedElementVersion() == null) {
            throw new IllegalArgumentException("expectedElementVersion es obligatorio para modificar " + id);
        }
        return request.expectedElementVersion();
    }

    private void validateRequest(DiagramOperationRequest request) {
        if (request == null || request.operationId() == null) throw new IllegalArgumentException("operationId es obligatorio");
        if (request.baseRevision() == null) throw new IllegalArgumentException("baseRevision es obligatorio");
        if (request.baseRevision() < 0) throw new IllegalArgumentException("baseRevision no puede ser negativa");
        if (request.type() == null || request.type().isBlank()) throw new IllegalArgumentException("type es obligatorio");
        if (request.payload() == null || request.payload().isNull()) throw new IllegalArgumentException("payload es obligatorio");
    }

    private void assertVersion(long actual, long expected, long revision, UUID id) {
        if (expected != actual) {
            throw new ConflictException("ELEMENT_VERSION_MISMATCH", "El elemento cambió en otra sesión", revision, id, expected, actual);
        }
    }

    private ConflictException conflict(String code, String message, long revision, UUID id, Long actual) {
        return new ConflictException(code, message, revision, id, actual);
    }

    private UUID uuid(JsonNode payload, String field) {
        try { return UUID.fromString(text(payload, field)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("UUID inválido en " + field); }
    }

    private String text(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("El campo " + field + " es obligatorio");
        return value.trim();
    }

    private double number(JsonNode payload, String field) {
        if (!payload.has(field) || !payload.path(field).isNumber()) throw new IllegalArgumentException("El campo " + field + " debe ser numérico");
        double value = payload.path(field).asDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("La posición debe ser finita");
        return value;
    }

    private UUID requiredId(UUID id) {
        if (id == null) throw new IllegalArgumentException("Todos los elementos requieren UUID");
        return id;
    }

    private void requireClass(Set<UUID> ids, UUID id) {
        if (id == null || !ids.contains(id)) throw new IllegalArgumentException("Referencia a clase inexistente: " + id);
    }

    private void requireUniqueId(Set<UUID> ids, UUID id) {
        requiredId(id);
        if (!ids.add(id)) throw new IllegalArgumentException("UUID duplicado: " + id);
    }

    private void validateName(String name, String kind) {
        if (name == null || !VALID_NAME.matcher(name).matches() || name.length() > 120) {
            throw new IllegalArgumentException("Nombre de " + kind + " inválido: " + name);
        }
    }

    private void requireUniqueName(Set<String> names, String name, String kind) {
        if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Nombre de " + kind + " duplicado: " + name);
    }

    private DiagramDocument read(String json) {
        try { return mapper.readValue(json, DiagramDocument.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Modelo persistido inválido", exception); }
    }

    private String write(DiagramDocument document) {
        try { return mapper.writeValueAsString(document); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("No se pudo serializar el modelo", exception); }
    }
}
