package com.collabmodeler.api.diagram;

import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.support.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagramOperationValidationTest {
    @Mock DiagramRepository diagrams;
    @Mock DiagramOperationRepository operations;
    @Mock AccessService access;
    private final ObjectMapper mapper = new ObjectMapper();
    private DiagramService service;
    private UUID diagramId;
    private UUID firstId;
    private UUID secondId;
    private DiagramEntity entity;

    @BeforeEach
    void setup() throws Exception {
        diagramId = UUID.randomUUID();
        firstId = UUID.randomUUID();
        secondId = UUID.randomUUID();
        var document = new DiagramDocument(diagramId, "Ventas", 0, List.of(
            new DiagramDocument.ClassElement(firstId, "Cliente", List.of(), new DiagramDocument.Position(0, 0), 1),
            new DiagramDocument.ClassElement(secondId, "Pedido", List.of(), new DiagramDocument.Position(200, 0), 1)
        ), List.of(), List.of(), List.of());
        entity = new DiagramEntity(diagramId, "Ventas", mapper.writeValueAsString(document), "owner");
        lenient().when(diagrams.findForUpdate(diagramId)).thenReturn(Optional.of(entity));
        lenient().when(operations.existsById(any())).thenReturn(false);
        service = new DiagramService(diagrams, operations, mapper, access);
    }

    @Test
    void staleOperationsOnDifferentElementsConverge() {
        service.apply(diagramId, rename(firstId, "Comprador", 0, 1), "u1", "Uno");
        DiagramDocument result = service.apply(diagramId, rename(secondId, "Orden", 0, 1), "u2", "Dos");
        assertEquals(2, result.revision());
        assertEquals(List.of("Comprador", "Orden"), result.classes().stream().map(DiagramDocument.ClassElement::name).toList());
    }

    @Test
    void staleChangeOnSameElementReturnsStructuredConflictData() {
        service.apply(diagramId, rename(firstId, "Comprador", 0, 1), "u1", "Uno");
        ConflictException conflict = assertThrows(ConflictException.class,
            () -> service.apply(diagramId, rename(firstId, "Persona", 0, 1), "u2", "Dos"));
        assertEquals("ELEMENT_VERSION_MISMATCH", conflict.getCode());
        assertEquals(firstId, conflict.getElementId());
        assertEquals(2, conflict.getActualElementVersion());
        assertEquals(1, conflict.getCurrentRevision());
    }

    @Test
    void classMoveRequiresAnExpectedVersion() {
        ObjectNode payload = mapper.createObjectNode().put("id", firstId.toString()).put("x", 3).put("y", 4);
        assertThrows(IllegalArgumentException.class, () -> service.apply(diagramId,
            new DiagramOperationRequest(UUID.randomUUID(), 0L, null, "CLASS_MOVED", payload), "u", "User"));
    }

    @Test
    void clientsCannotReplaceTheWholeModelAsAnUndoShortcut() {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("model", mapper.valueToTree(new DiagramDocument(diagramId, "Ventas", 0, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.apply(diagramId,
            new DiagramOperationRequest(UUID.randomUUID(), 0L, null, "MODEL_RESTORED", payload), "u", "User"));
        assertEquals(0, entity.getRevision());
    }

    @Test
    void batchIsAtomicWhenInheritanceWouldCreateACycle() {
        ObjectNode payload = mapper.createObjectNode();
        var children = payload.putArray("operations");
        children.add(operationNode("GENERALIZATION_CREATED", mapper.valueToTree(
            new DiagramDocument.Generalization(UUID.randomUUID(), firstId, secondId, 1))));
        children.add(operationNode("GENERALIZATION_CREATED", mapper.valueToTree(
            new DiagramDocument.Generalization(UUID.randomUUID(), secondId, firstId, 1))));

        assertThrows(IllegalArgumentException.class, () -> service.apply(diagramId,
            new DiagramOperationRequest(UUID.randomUUID(), 0L, null, "BATCH", payload), "u", "User"));
        assertEquals(0, entity.getRevision());
        verify(diagrams, never()).save(any());
        verify(operations, never()).save(any());
    }

    @Test
    void importedPackagesAreCommittedAndRemovedAsSingleVersionedBatches() {
        UUID packageId = UUID.randomUUID();
        ObjectNode createPayload = mapper.createObjectNode();
        ObjectNode packageValue = mapper.createObjectNode().put("id", packageId.toString()).put("name", "Ventas").put("version", 1);
        packageValue.putArray("memberIds").add(firstId.toString()).add(secondId.toString());
        createPayload.putArray("operations").add(operationNode("PACKAGE_CREATED", packageValue));

        DiagramDocument imported = service.apply(diagramId,
            new DiagramOperationRequest(UUID.randomUUID(), 0L, null, "BATCH", createPayload), "u", "User");

        assertEquals(1, imported.revision());
        assertEquals(List.of(firstId, secondId), imported.packages().getFirst().memberIds());

        ObjectNode undoPayload = mapper.createObjectNode();
        ObjectNode update = packageValue.deepCopy(); update.putArray("memberIds");
        ObjectNode updateOperation = operationNode("PACKAGE_UPDATED", update); updateOperation.put("expectedElementVersion", 1);
        ObjectNode deleteOperation = operationNode("PACKAGE_DELETED", mapper.createObjectNode().put("id", packageId.toString()));
        deleteOperation.put("expectedElementVersion", 2);
        undoPayload.putArray("operations").add(updateOperation).add(deleteOperation);
        DiagramDocument undone = service.apply(diagramId,
            new DiagramOperationRequest(UUID.randomUUID(), 1L, null, "BATCH", undoPayload), "u", "User");

        assertEquals(2, undone.revision());
        assertTrue(undone.packages().isEmpty());
        verify(operations, times(2)).save(any());
    }

    @Test
    void repeatedOperationIdIsNotAppliedTwice() throws Exception {
        UUID operationId = UUID.randomUUID();
        when(operations.existsById(operationId)).thenReturn(false, true);
        service.apply(diagramId, rename(firstId, "Comprador", 0, 1, operationId), "u", "User");
        DiagramDocument retried = service.apply(diagramId, rename(firstId, "Comprador", 0, 1, operationId), "u", "User");
        assertEquals(1, retried.revision());
        verify(operations, times(1)).save(any());
    }

    @Test
    void supportsTheCompleteCrudOperationSet() {
        UUID enumerationId = UUID.randomUUID();
        UUID enumValueId = UUID.randomUUID();
        ObjectNode enumeration = mapper.createObjectNode()
            .put("id", enumerationId.toString()).put("name", "Estado").put("version", 1);
        enumeration.putObject("position").put("x", 400).put("y", 0);
        enumeration.putArray("values").addObject().put("id", enumValueId.toString()).put("name", "ACTIVO").put("version", 1);
        DiagramDocument result = apply("ENUMERATION_CREATED", enumeration, null);
        enumeration.withArray("values").addObject().put("id", UUID.randomUUID().toString()).put("name", "INACTIVO").put("version", 1);
        result = apply("ENUMERATION_UPDATED", enumeration, 1L);

        UUID attributeId = UUID.randomUUID();
        ObjectNode createAttribute = mapper.createObjectNode().put("classId", firstId.toString());
        createAttribute.putObject("attribute").put("id", attributeId.toString()).put("name", "estado")
            .put("type", "Estado").put("primaryKey", false).put("required", true).put("unique", false).put("version", 1);
        result = apply("ATTRIBUTE_CREATED", createAttribute, 1L);

        ObjectNode updateAttribute = mapper.createObjectNode().put("classId", firstId.toString());
        updateAttribute.putObject("attribute").put("id", attributeId.toString()).put("name", "situacion")
            .put("type", "Estado").put("primaryKey", false).put("required", true).put("unique", true).put("version", 1);
        result = apply("ATTRIBUTE_UPDATED", updateAttribute, 1L);
        result = apply("ATTRIBUTE_REORDERED", mapper.createObjectNode().put("classId", firstId.toString())
            .put("attributeId", attributeId.toString()).put("newIndex", 0), 3L);

        UUID associationId = UUID.randomUUID();
        ObjectNode association = mapper.createObjectNode().put("id", associationId.toString())
            .put("sourceId", firstId.toString()).put("targetId", secondId.toString())
            .put("sourceCardinality", "1").put("targetCardinality", "0..*")
            .put("name", "pedidos").put("sourceRole", "cliente").put("targetRole", "ordenes")
            .put("owningSide", "TARGET").put("version", 1);
        result = apply("ASSOCIATION_CREATED", association, null);
        association.put("targetCardinality", "1..*");
        result = apply("ASSOCIATION_UPDATED", association, 1L);

        UUID generalizationId = UUID.randomUUID();
        result = apply("GENERALIZATION_CREATED", mapper.createObjectNode().put("id", generalizationId.toString())
            .put("parentId", firstId.toString()).put("childId", secondId.toString()).put("version", 1), null);
        result = apply("GENERALIZATION_DELETED", mapper.createObjectNode().put("id", generalizationId.toString()), 1L);
        result = apply("ASSOCIATION_DELETED", mapper.createObjectNode().put("id", associationId.toString()), 2L);
        result = apply("ATTRIBUTE_DELETED", mapper.createObjectNode().put("classId", firstId.toString())
            .put("id", attributeId.toString()), 2L);
        result = apply("ENUMERATION_DELETED", mapper.createObjectNode().put("id", enumerationId.toString()), 2L);

        assertEquals(12, result.revision());
        assertTrue(result.enumerations().isEmpty());
        assertTrue(result.classes().getFirst().attributes().isEmpty());
        assertTrue(result.associations().isEmpty());
        assertTrue(result.generalizations().isEmpty());
    }

    @Test
    void supportsGranularEnumerationValueOperations() {
        UUID enumerationId = UUID.randomUUID(); UUID valueId = UUID.randomUUID();
        ObjectNode enumeration = mapper.createObjectNode().put("id", enumerationId.toString()).put("name", "Estado").put("version", 1);
        enumeration.putObject("position").put("x", 400).put("y", 0); enumeration.putArray("values");
        apply("ENUMERATION_CREATED", enumeration, null);

        ObjectNode create = mapper.createObjectNode().put("enumerationId", enumerationId.toString());
        create.putObject("value").put("id", valueId.toString()).put("name", "ACTIVO").put("version", 1);
        DiagramDocument result = apply("ENUMERATION_VALUE_CREATED", create, 1L);
        ObjectNode update = mapper.createObjectNode().put("enumerationId", enumerationId.toString());
        update.putObject("value").put("id", valueId.toString()).put("name", "HABILITADO").put("version", 1);
        result = apply("ENUMERATION_VALUE_UPDATED", update, 1L);
        result = apply("ENUMERATION_VALUE_DELETED", mapper.createObjectNode().put("enumerationId", enumerationId.toString()).put("id", valueId.toString()), 2L);

        assertEquals(4, result.revision());
        assertTrue(result.enumerations().getFirst().values().isEmpty());
    }

    @Test
    void rejectsAMissingBaseRevisionInsteadOfDefaultingToZero() {
        var request = new DiagramOperationRequest(UUID.randomUUID(), null, null, "CLASS_CREATED", mapper.createObjectNode());
        assertThrows(IllegalArgumentException.class, () -> service.apply(diagramId, request, "u", "User"));
    }

    private DiagramOperationRequest rename(UUID id, String name, long revision, long version) {
        return rename(id, name, revision, version, UUID.randomUUID());
    }

    private DiagramOperationRequest rename(UUID id, String name, long revision, long version, UUID operationId) {
        return new DiagramOperationRequest(operationId, revision, version, "CLASS_RENAMED",
            mapper.createObjectNode().put("id", id.toString()).put("name", name));
    }

    private ObjectNode operationNode(String type, ObjectNode payload) {
        ObjectNode node = mapper.createObjectNode();
        node.put("operationId", UUID.randomUUID().toString()).put("baseRevision", 0).put("type", type);
        node.set("payload", payload);
        return node;
    }

    private DiagramDocument apply(String type, ObjectNode payload, Long expectedVersion) {
        return service.apply(diagramId, new DiagramOperationRequest(UUID.randomUUID(), entity.getRevision(),
            expectedVersion, type, payload), "u", "User");
    }
}
