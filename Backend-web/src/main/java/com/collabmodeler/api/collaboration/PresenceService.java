package com.collabmodeler.api.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final CollaborationEventPublisher events;
    private final boolean redisEnabled;
    private final Duration ttl;
    private final Map<String, Participant> local = new ConcurrentHashMap<>();

    public PresenceService(StringRedisTemplate redis, ObjectMapper mapper, CollaborationEventPublisher events,
                           @Value("${app.collaboration.redis-enabled:true}") boolean redisEnabled,
                           @Value("${app.collaboration.presence-ttl-seconds:45}") long ttlSeconds) {
        this.redis = redis; this.mapper = mapper; this.events = events; this.redisEnabled = redisEnabled;
        this.ttl = Duration.ofSeconds(Math.max(15, ttlSeconds));
    }

    public void touch(UUID diagramId, String sessionId, String subject, String displayName,
                      Cursor cursor, List<String> selection, String activity) {
        Participant participant = new Participant(sessionId, subject, displayName, cursor,
            selection == null ? List.of() : selection.stream().limit(100).toList(), activity, Instant.now());
        local.put(localKey(diagramId, sessionId), participant);
        if (redisEnabled) {
            try {
                String key = presenceKey(diagramId, sessionId);
                redis.opsForValue().set(key, mapper.writeValueAsString(participant), ttl);
                redis.opsForSet().add(indexKey(diagramId), sessionId);
                redis.expire(indexKey(diagramId), ttl.multipliedBy(2));
                redis.opsForSet().add(sessionKey(sessionId), diagramId.toString());
                redis.expire(sessionKey(sessionId), ttl.multipliedBy(2));
            } catch (Exception ignored) { }
        }
        broadcast(diagramId);
    }

    public void leave(UUID diagramId, String sessionId) {
        local.remove(localKey(diagramId, sessionId));
        if (redisEnabled) {
            try {
                redis.delete(presenceKey(diagramId, sessionId));
                redis.opsForSet().remove(indexKey(diagramId), sessionId);
                redis.opsForSet().remove(sessionKey(sessionId), diagramId.toString());
            } catch (Exception ignored) { }
        }
        broadcast(diagramId);
    }

    public void leaveSession(String sessionId) {
        Set<UUID> diagrams = new HashSet<>();
        local.keySet().stream().filter(key -> key.endsWith(":" + sessionId)).forEach(key -> {
            try { diagrams.add(UUID.fromString(key.substring(0, key.indexOf(':')))); } catch (Exception ignored) { }
        });
        if (redisEnabled) {
            try {
                Set<String> values = redis.opsForSet().members(sessionKey(sessionId));
                if (values != null) values.forEach(value -> {
                    try { diagrams.add(UUID.fromString(value)); } catch (Exception ignored) { }
                });
                redis.delete(sessionKey(sessionId));
            } catch (Exception ignored) { }
        }
        diagrams.forEach(id -> leave(id, sessionId));
    }

    public List<Participant> list(UUID diagramId) {
        Map<String, Participant> result = new HashMap<>();
        Instant cutoff = Instant.now().minus(ttl);
        local.entrySet().removeIf(entry -> entry.getValue().lastSeen().isBefore(cutoff));
        local.forEach((key, value) -> { if (key.startsWith(diagramId + ":")) result.put(value.sessionId(), value); });
        if (redisEnabled) {
            try {
                Set<String> sessions = redis.opsForSet().members(indexKey(diagramId));
                if (sessions != null) for (String session : sessions) {
                    String json = redis.opsForValue().get(presenceKey(diagramId, session));
                    if (json == null) redis.opsForSet().remove(indexKey(diagramId), session);
                    else result.put(session, mapper.readValue(json, Participant.class));
                }
            } catch (Exception ignored) { }
        }
        return result.values().stream().sorted(Comparator.comparing(Participant::displayName)).toList();
    }

    private void broadcast(UUID diagramId) { events.publish(diagramId, "PRESENCE", Map.of("participants", list(diagramId))); }
    private String localKey(UUID id, String session) { return id + ":" + session; }
    private String presenceKey(UUID id, String session) { return "presence:" + id + ":" + session; }
    private String indexKey(UUID id) { return "presence:index:" + id; }
    private String sessionKey(String session) { return "presence:session:" + session; }

    public record Cursor(double x, double y) {}
    public record Participant(String sessionId, String userId, String displayName, Cursor cursor,
                              List<String> selection, String activity, Instant lastSeen) {}
}
