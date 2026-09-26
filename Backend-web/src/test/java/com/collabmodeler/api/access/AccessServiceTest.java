package com.collabmodeler.api.access;

import com.collabmodeler.api.diagram.DiagramRepository;
import com.collabmodeler.api.diagram.DiagramEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {
    @Mock DiagramRepository diagrams;
    @Mock DiagramMemberRepository members;
    @Mock DiagramShareLinkRepository shareLinks;

    @Test
    void onlyOwnerAndEditorCanApplyOperations() {
        UUID diagramId = UUID.randomUUID();
        AccessService service = new AccessService(diagrams, members, shareLinks);
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

    @Test
    void ownerCanChangeAndRemoveNonOwnerMembers() {
        UUID diagramId = UUID.randomUUID(); UUID memberId = UUID.randomUUID();
        AccessService service = new AccessService(diagrams, members, shareLinks);
        DiagramMemberEntity owner = new DiagramMemberEntity(diagramId, "owner", "Owner", "OWNER");
        DiagramMemberEntity reader = new DiagramMemberEntity(diagramId, "reader", "Reader", "READER");
        when(members.findByDiagramIdAndSubject(diagramId, "owner")).thenReturn(Optional.of(owner));
        when(members.findById(memberId)).thenReturn(Optional.of(reader));

        service.updateRole(diagramId, memberId, "EDITOR", "owner");
        assertDoesNotThrow(() -> service.removeMember(diagramId, memberId, "owner"));
        verify(members).save(reader);
        verify(members).delete(reader);
    }

    @Test
    void ownerCannotBeDemotedOrRemovedAndReaderCannotAdminister() {
        UUID diagramId = UUID.randomUUID(); UUID memberId = UUID.randomUUID();
        AccessService service = new AccessService(diagrams, members, shareLinks);
        DiagramMemberEntity owner = new DiagramMemberEntity(diagramId, "owner", "Owner", "OWNER");
        DiagramMemberEntity reader = new DiagramMemberEntity(diagramId, "reader", "Reader", "READER");
        when(members.findByDiagramIdAndSubject(diagramId, "owner")).thenReturn(Optional.of(owner));
        when(members.findByDiagramIdAndSubject(diagramId, "reader")).thenReturn(Optional.of(reader));
        when(members.findById(memberId)).thenReturn(Optional.of(owner));

        assertThrows(IllegalArgumentException.class, () -> service.updateRole(diagramId, memberId, "READER", "owner"));
        assertThrows(IllegalArgumentException.class, () -> service.removeMember(diagramId, memberId, "owner"));
        assertThrows(AccessDeniedException.class, () -> service.updateRole(diagramId, memberId, "EDITOR", "reader"));
    }

    @Test
    void creatingANewShareLinkDoesNotInvalidateEarlierLinks() {
        UUID diagramId = UUID.randomUUID();
        DiagramEntity diagram = new DiagramEntity(diagramId, "Compartido", "{}", "owner");
        DiagramMemberEntity owner = new DiagramMemberEntity(diagramId, "owner", "Owner", "OWNER");
        when(members.findByDiagramIdAndSubject(diagramId, "owner")).thenReturn(Optional.of(owner));
        when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
        AccessService service = new AccessService(diagrams, members, shareLinks);

        var first = service.rotateLink(diagramId, "owner");
        var second = service.rotateLink(diagramId, "owner");

        assertNotEquals(first.get("token"), second.get("token"));
        verify(shareLinks, times(2)).save(any(DiagramShareLinkEntity.class));
        assertNull(diagram.getShareTokenHash());
    }
}
