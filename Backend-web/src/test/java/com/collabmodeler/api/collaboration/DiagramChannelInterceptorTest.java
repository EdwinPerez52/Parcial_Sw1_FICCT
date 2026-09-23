package com.collabmodeler.api.collaboration;

import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.auth.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DiagramChannelInterceptorTest {
    @Mock AccessService access;

    @Test void authorizesEveryDiagramSubscriptionAgainstMembership() {
        UUID diagramId = UUID.randomUUID(); UUID accountId = UUID.randomUUID();
        var principal = new AccountPrincipal(accountId, "ana@example.com", "Ana", true, false);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/diagrams/" + diagramId); headers.setUser(authentication); headers.setLeaveMutable(true);
        new DiagramChannelInterceptor(access).preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), new ExecutorSubscribableChannel());
        verify(access).requireMember(diagramId, principal.getName());
    }

    @Test void rejectsSubscriptionsOutsideTheAuthorizedDiagramNamespace() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/admin"); headers.setLeaveMutable(true);
        assertThrows(AccessDeniedException.class, () -> new DiagramChannelInterceptor(access).preSend(
            MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), new ExecutorSubscribableChannel()));
    }
}
