package com.collabmodeler.api.diagram;

import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.support.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagramServiceTest {
    @Mock DiagramRepository diagrams;
    @Mock DiagramOperationRepository operations;
    @Mock AccessService access;

    @Test
    void createsClassAndAdvancesRevision() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID diagramId = UUID.randomUUID();
        var document = new DiagramDocument(diagramId, "Ventas", 0, null, null);
        var entity = new DiagramEntity(diagramId, "Ventas", mapper.writeValueAsString(document), "owner");
        when(diagrams.findForUpdate(diagramId)).thenReturn(Optional.of(entity));
        when(operations.existsById(any())).thenReturn(false);
        var service = new DiagramService(diagrams, operations, mapper, access);

        UUID classId = UUID.randomUUID();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("id", classId.toString()).put("name", "Producto").put("version", 1);
        payload.putObject("position").put("x", 10).put("y", 20);
        payload.putArray("attributes");
        var result = service.apply(diagramId,
            new DiagramOperationRequest(UUID.randomUUID(), 0L, null, "CLASS_CREATED", payload), "user", "User");

        assertEquals(1, result.revision());
        assertEquals("Producto", result.classes().getFirst().name());
        verify(operations).save(any());
    }

    @Test
    void rejectsClientRevisionAheadOfServer() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID diagramId = UUID.randomUUID();
        var document = new DiagramDocument(diagramId, "Ventas", 0, null, null);
        var entity = new DiagramEntity(diagramId, "Ventas", mapper.writeValueAsString(document), "owner");
        when(diagrams.findForUpdate(diagramId)).thenReturn(Optional.of(entity));
        var service = new DiagramService(diagrams, operations, mapper, access);

        assertThrows(ConflictException.class, () -> service.apply(diagramId,
            new DiagramOperationRequest(UUID.randomUUID(), 3L, null, "CLASS_CREATED", mapper.createObjectNode()), "user", "User"));
    }
}
