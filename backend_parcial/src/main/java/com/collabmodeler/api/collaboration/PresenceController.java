package com.collabmodeler.api.collaboration;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Controller
public class PresenceController {
    private final PresenceService presence;
    private final AccessService access;
    public PresenceController(PresenceService presence, AccessService access) { this.presence = presence; this.access = access; }

    @MessageMapping("/diagrams/{diagramId}/presence")
    public void update(@DestinationVariable UUID diagramId, PresenceMessage message, Principal principal,
                       StompHeaderAccessor headers) {
        String subject = AccessController.subject(principal);
        access.requireMember(diagramId, subject);
        String sessionId = headers.getSessionId();
        if (sessionId == null) throw new IllegalArgumentException("Sesión WebSocket inválida");
        if ("LEAVE".equals(message.kind())) presence.leave(diagramId, sessionId);
        else presence.touch(diagramId, sessionId, subject, AccessController.displayName(principal),
            safeCursor(message.cursor()), message.selection(), safeActivity(message.activity()));
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) { presence.leaveSession(event.getSessionId()); }

    private String safeActivity(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(160, trimmed.length()));
    }

    private PresenceService.Cursor safeCursor(PresenceService.Cursor value) {
        if (value == null) return null;
        if (!Double.isFinite(value.x()) || !Double.isFinite(value.y())) throw new IllegalArgumentException("Cursor inválido");
        return new PresenceService.Cursor(Math.max(0, Math.min(1, value.x())), Math.max(0, Math.min(1, value.y())));
    }

    public record PresenceMessage(String kind, PresenceService.Cursor cursor, List<String> selection, String activity) {}
}
