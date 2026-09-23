package com.collabmodeler.api.ai;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.collabmodeler.api.diagram.DiagramOperationRequest;
import com.collabmodeler.api.diagram.DiagramService;
import com.collabmodeler.api.support.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssistantCommandServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void invalidProviderResponseCannotCreateAProposalOrModifyTheDiagram() {
        DiagramService diagrams = mock(DiagramService.class); AssistantProposalRepository repository = mock(AssistantProposalRepository.class);
        AiProperties properties = new AiProperties(); properties.setProvider("fake");
        UUID id = UUID.randomUUID(); DiagramDocument current = empty(id);
        when(diagrams.get(id)).thenReturn(current);
        TextCommandProvider invalid = new TextCommandProvider() {
            public String id() { return "fake"; }
            public DiagramOperationRequest interpret(String instruction, DiagramDocument diagram) {
                return new DiagramOperationRequest(UUID.randomUUID(), 0L, null, "DROP_TABLE", mapper.createObjectNode());
            }
        };
        when(diagrams.preview(eq(id), any())).thenThrow(new IllegalArgumentException("Tipo no soportado"));
        var service = new AssistantCommandService(diagrams, new LocalCommandParser(mapper), List.of(invalid), repository, properties, mapper);

        assertThrows(IllegalArgumentException.class, () -> service.interpret(id, "haz un cambio complejo", "account:1"));
        verify(repository, never()).save(any());
        verify(diagrams, never()).apply(any(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test void destructiveLocalCommandRequiresConfirmationAndStoresNoRawInstruction() {
        DiagramService diagrams = mock(DiagramService.class); AssistantProposalRepository repository = mock(AssistantProposalRepository.class);
        UUID id = UUID.randomUUID(); UUID classId = UUID.randomUUID();
        DiagramDocument current = new DiagramDocument(id, "M", 2, List.of(new DiagramDocument.ClassElement(classId, "Persona", List.of(), new DiagramDocument.Position(0, 0), 1)), List.of(), List.of(), List.of(), List.of());
        when(diagrams.get(id)).thenReturn(current); when(diagrams.preview(eq(id), any())).thenReturn(empty(id));
        var service = new AssistantCommandService(diagrams, new LocalCommandParser(mapper), List.of(), repository, new AiProperties(), mapper);

        var result = service.interpret(id, "elimina la clase Persona", "account:1");

        assertTrue(result.requiresConfirmation()); assertEquals("local-deterministic", result.provider());
        verify(repository).save(argThat(value -> !value.getOperationJson().contains("elimina la clase Persona")));
        verify(diagrams, never()).apply(any(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test void aProposalCanOnlyBeAppliedOnce() {
        DiagramService diagrams = mock(DiagramService.class); AssistantProposalRepository repository = mock(AssistantProposalRepository.class);
        UUID id = UUID.randomUUID(); UUID proposalId = UUID.randomUUID();
        var operation = new DiagramOperationRequest(UUID.randomUUID(), 0L, null, "CLASS_CREATED", mapper.createObjectNode());
        var entity = new AssistantProposalEntity(proposalId, id, "account:1", "local-deterministic", "hash", write(operation), false, java.time.Instant.now().plusSeconds(60));
        when(repository.findForUpdate(proposalId)).thenReturn(java.util.Optional.of(entity)); when(diagrams.apply(eq(id), any(), anyString(), anyString(), eq("ASSISTANT"), eq("local-deterministic"))).thenReturn(empty(id));
        var service = new AssistantCommandService(diagrams, new LocalCommandParser(mapper), List.of(), repository, new AiProperties(), mapper);

        service.apply(id, proposalId, "account:1", "Ana", false);
        assertThrows(ConflictException.class, () -> service.apply(id, proposalId, "account:1", "Ana", false));
        verify(diagrams, times(1)).apply(eq(id), any(), anyString(), anyString(), eq("ASSISTANT"), eq("local-deterministic"));
    }

    private DiagramDocument empty(UUID id) { return new DiagramDocument(id, "M", 0, List.of(), List.of(), List.of(), List.of(), List.of()); }
    private String write(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw new RuntimeException(exception); } }
}
