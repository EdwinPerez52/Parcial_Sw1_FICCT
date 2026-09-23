package com.collabmodeler.api.collaboration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CollaborationEventPublisher {
    public static final String REDIS_CHANNEL = "collab:model-events";
    private final SimpMessagingTemplate messages;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean redisEnabled;
    private final String instanceId = UUID.randomUUID().toString();

    public CollaborationEventPublisher(SimpMessagingTemplate messages, StringRedisTemplate redis, ObjectMapper mapper,
                                       @Value("${app.collaboration.redis-enabled:true}") boolean redisEnabled) {
        this.messages = messages; this.redis = redis; this.mapper = mapper; this.redisEnabled = redisEnabled;
    }

    public void publish(UUID diagramId, String type, Object payload) {
        String destination = "/topic/diagrams/" + diagramId;
        RealtimeEvent event = new RealtimeEvent(type, payload);
        messages.convertAndSend(destination, event);
        if (!redisEnabled) return;
        try {
            String body = mapper.writeValueAsString(new RedisEnvelope(instanceId, destination, type, mapper.valueToTree(payload)));
            redis.convertAndSend(REDIS_CHANNEL, body);
        } catch (Exception ignored) {
            // Local delivery remains available; revision recovery handles a temporary Redis outage.
        }
    }

    void receive(String body) {
        try {
            RedisEnvelope envelope = mapper.readValue(body, RedisEnvelope.class);
            if (!instanceId.equals(envelope.instanceId())) {
                messages.convertAndSend(envelope.destination(), new RealtimeEvent(envelope.type(), envelope.payload()));
            }
        } catch (Exception ignored) { }
    }

    public record RealtimeEvent(String type, Object payload) {}
    record RedisEnvelope(String instanceId, String destination, String type, JsonNode payload) {}
}
