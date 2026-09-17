package com.collabmodeler.api.access;

import com.collabmodeler.api.auth.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AccessControllerTest {
    @Test void extractsVerifiedAccountFromSpringAuthentication() {
        var account = new AccountPrincipal(UUID.randomUUID(), "ana@example.com", "Ana Pérez", true, false);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(account, null, List.of());
        assertEquals(account.getName(), AccessController.subject(authentication));
        assertEquals("Ana Pérez", AccessController.displayName(authentication));
        assertDoesNotThrow(() -> AccessController.requireVerified(authentication));
    }
    @Test void blocksAnUnverifiedAccountFromEditing() {
        var account = new AccountPrincipal(UUID.randomUUID(), "ana@example.com", "Ana Pérez", false, false);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(account, null, List.of());
        assertThrows(AccessDeniedException.class, () -> AccessController.requireVerified(authentication));
    }
}
