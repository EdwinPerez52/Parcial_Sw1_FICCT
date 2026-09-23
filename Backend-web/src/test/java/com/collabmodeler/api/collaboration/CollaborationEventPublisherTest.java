package com.collabmodeler.api.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollaborationEventPublisherTest {
    @Test void aRedisEnvelopeFromOneInstanceIsDeliveredByAnotherInstance() {
        ObjectMapper mapper = new ObjectMapper(); UUID diagramId = UUID.randomUUID();
        SimpMessagingTemplate localMessages = mock(SimpMessagingTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        CollaborationEventPublisher first = new CollaborationEventPublisher(localMessages, redis, mapper, true);
        first.publish(diagramId, "COMMENT_CREATED", Map.of("id", "c1"));
        var body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CollaborationEventPublisher.REDIS_CHANNEL), body.capture());

        SimpMessagingTemplate remoteMessages = mock(SimpMessagingTemplate.class);
        CollaborationEventPublisher second = new CollaborationEventPublisher(remoteMessages, mock(StringRedisTemplate.class), mapper, true);
        second.receive(body.getValue());
        verify(remoteMessages).convertAndSend(eq("/topic/diagrams/" + diagramId), any(CollaborationEventPublisher.RealtimeEvent.class));
    }
}
