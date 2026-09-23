package com.collabmodeler.api.ai;

import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.activity.DiagramActivityService;
import com.collabmodeler.api.auth.AccountPrincipal;
import com.collabmodeler.api.collaboration.CollaborationEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AssistantCommandControllerTest {
    @Test void readerCannotInterpretCommands() {
        AccessService access = mock(AccessService.class); AssistantCommandService assistant = mock(AssistantCommandService.class);
        UUID diagramId = UUID.randomUUID(); UUID accountId = UUID.randomUUID(); String subject = "account:" + accountId;
        var principal = UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(accountId, "reader@example.com", "Reader", true, false), null, List.of());
        doThrow(new AccessDeniedException("Se requiere rol de editor")).when(access).requireEditor(diagramId, subject);
        var controller = new AssistantCommandController(assistant, access, mock(CollaborationEventPublisher.class), mock(DiagramActivityService.class));

        assertThrows(AccessDeniedException.class, () -> controller.interpret(diagramId,
            new AssistantCommandController.InterpretRequest("crea una clase Producto"), principal));
        verifyNoInteractions(assistant);
    }

    @Test void unverifiedAccountCannotApplyAProposal() {
        AccessService access = mock(AccessService.class); AssistantCommandService assistant = mock(AssistantCommandService.class);
        UUID accountId = UUID.randomUUID();
        var principal = UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(accountId, "pending@example.com", "Pending", false, false), null, List.of());
        var controller = new AssistantCommandController(assistant, access, mock(CollaborationEventPublisher.class), mock(DiagramActivityService.class));

        assertThrows(AccessDeniedException.class, () -> controller.apply(UUID.randomUUID(), UUID.randomUUID(),
            new AssistantCommandController.ApplyRequest(true), principal));
        verifyNoInteractions(access, assistant);
    }
}
