package com.collabmodeler.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
public class BootstrapInvitationInitializer implements ApplicationRunner {
    private final AuthStore store; private final AuthMailService mail; private final String email; private final Duration ttl;
    private final SecureRandom random = new SecureRandom();
    public BootstrapInvitationInitializer(AuthStore store, AuthMailService mail,
            @Value("${app.bootstrap-admin-email:}") String email,
            @Value("${app.auth.invitation-hours:72}") long hours) {
        this.store = store; this.mail = mail; this.email = email; this.ttl = Duration.ofHours(hours);
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) return;
        String normalized = AuthService.normalizeEmail(email);
        if (store.accountByEmail(normalized).isPresent() || store.hasActiveBootstrapInvitation(normalized, Instant.now())) return;
        byte[] bytes = new byte[32]; random.nextBytes(bytes); String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.createInvitation(AuthService.hash(token), normalized, null, true, Instant.now().plus(ttl)); mail.invitation(normalized, token);
    }
}
