package com.collabmodeler.api.collaboration;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiagramChannelInterceptor implements ChannelInterceptor {
    private static final Pattern TOPIC = Pattern.compile("^/topic/diagrams/([0-9a-fA-F-]{36})$");
    private static final Pattern APPLICATION = Pattern.compile("^/app/diagrams/([0-9a-fA-F-]{36})/presence$");
    private final AccessService access;

    public DiagramChannelInterceptor(AccessService access) { this.access = access; }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
        StompCommand command = headers.getCommand();
        if (command != StompCommand.SUBSCRIBE && command != StompCommand.SEND) return message;
        String destination = headers.getDestination();
        Matcher matcher = (command == StompCommand.SUBSCRIBE ? TOPIC : APPLICATION).matcher(destination == null ? "" : destination);
        if (!matcher.matches()) throw new AccessDeniedException("Canal WebSocket no permitido");
        Principal principal = headers.getUser();
        access.requireMember(UUID.fromString(matcher.group(1)), AccessController.subject(principal));
        return message;
    }
}
