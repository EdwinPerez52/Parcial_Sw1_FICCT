package com.collabmodeler.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimiter {
    private record Window(long started, int attempts) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void check(String action, String key, int limit, long seconds) {
        long now = Instant.now().getEpochSecond();
        Window value = windows.compute(action + ":" + key, (ignored, old) ->
            old == null || now - old.started >= seconds ? new Window(now, 1) : new Window(old.started, old.attempts + 1));
        if (value.attempts > limit) throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Demasiados intentos. Inténtalo más tarde.");
    }
}
