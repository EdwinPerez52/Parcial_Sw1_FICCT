package com.collabmodeler.api.access;

import com.collabmodeler.api.diagram.DiagramRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {
    @Mock DiagramRepository diagrams;
    @Mock DiagramMemberRepository members;

    @Test
    void onlyOwnerAndEditorCanApplyOperations() {
        UUID diagramId = UUID.randomUUID();
        AccessService service = new AccessService(diagrams, members);
        when(members.findByDiagramIdAndSubject(diagramId, "reader"))
            .thenReturn(Optional.of(new DiagramMemberEntity(diagramId, "reader", "Reader", "READER")));
        when(members.findByDiagramIdAndSubject(diagramId, "editor"))
            .thenReturn(Optional.of(new DiagramMemberEntity(diagramId, "editor", "Editor", "EDITOR")));
        when(members.findByDiagramIdAndSubject(diagramId, "pending"))
            .thenReturn(Optional.of(new DiagramMemberEntity(diagramId, "pending", "Pending", "PENDING")));
        when(members.findByDiagramIdAndSubject(diagramId, "outsider")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.requireEditor(diagramId, "reader"));
        assertDoesNotThrow(() -> service.requireEditor(diagramId, "editor"));
        assertThrows(AccessDeniedException.class, () -> service.requireEditor(diagramId, "outsider"));
        assertThrows(AccessDeniedException.class, () -> service.requireMember(diagramId, "pending"));
    }
}
