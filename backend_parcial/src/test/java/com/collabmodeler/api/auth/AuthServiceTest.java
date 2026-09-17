package com.collabmodeler.api.auth;

import com.collabmodeler.api.diagram.DiagramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private AuthStore store; private DiagramRepository diagrams; private PasswordEncoder passwords;
    private AuthMailService mail; private AuthService service;
    @BeforeEach void setUp() {
        store = mock(AuthStore.class); diagrams = mock(DiagramRepository.class); passwords = mock(PasswordEncoder.class); mail = mock(AuthMailService.class);
        service = new AuthService(store, diagrams, passwords, mail, 60, 30);
    }
    @Test void registrationRequiresAValidInvitation() {
        when(store.invitation(anyString())).thenReturn(Optional.empty()); when(diagrams.findByShareTokenHash(anyString())).thenReturn(Optional.empty());
        AuthException error = assertThrows(AuthException.class, () -> service.register("Ana Pérez", "ANA@EXAMPLE.COM", "ClaveSegura1", "ClaveSegura1", "revoked"));
        assertEquals("INVITATION_REQUIRED", error.code()); verify(store, never()).createAccount(anyString(), anyString(), anyBoolean());
    }
    @Test void localLoginUsesGenericFailureAndRecordsAttempts() {
        UUID id = UUID.randomUUID(); var account = new AuthStore.Account(id, "ana@example.com", "Ana Pérez", true, false, 1, null);
        var identity = new AuthStore.Identity(UUID.randomUUID(), id, "LOCAL", "ana@example.com", "hash");
        when(store.accountByEmail("ana@example.com")).thenReturn(Optional.of(account)); when(store.identity("LOCAL", "ana@example.com")).thenReturn(Optional.of(identity)); when(passwords.matches("incorrecta", "hash")).thenReturn(false);
        AuthException error = assertThrows(AuthException.class, () -> service.login("ana@example.com", "incorrecta"));
        assertEquals("Correo o contraseña inválidos.", error.getMessage()); verify(store).recordLoginFailure(id, 2, null);
    }
    @Test void registrationDoesNotRevealAnExistingAccount() {
        UUID id = UUID.randomUUID();
        var account = new AuthStore.Account(id, "ana@example.com", "Ana", true, false, 0, null);
        when(store.invitation(anyString())).thenReturn(Optional.of(new AuthStore.Invitation(UUID.randomUUID(),
            "ana@example.com", null, false, java.time.Instant.now().plusSeconds(60), null, null, null)));
        when(store.accountByEmail("ana@example.com")).thenReturn(Optional.of(account));
        assertDoesNotThrow(() -> service.register("Ana", "ANA@example.com", "ClaveSegura1", "ClaveSegura1", "token"));
        verify(store, never()).createAccount(anyString(), anyString(), anyBoolean());
    }
    @Test void googleLinksToTheExistingEmailAccount() {
        UUID id = UUID.randomUUID(); var before = new AuthStore.Account(id, "ana@example.com", "Ana", true, false, 0, null); var after = new AuthStore.Account(id, "ana@example.com", "Ana Pérez", true, false, 0, null);
        when(store.identity("GOOGLE", "google-subject")).thenReturn(Optional.empty()); when(store.accountByEmail("ana@example.com")).thenReturn(Optional.of(before)); when(store.accountById(id)).thenReturn(Optional.of(after)); when(store.pendingJoins(id)).thenReturn(java.util.List.of()); when(store.acceptedInvitations(id)).thenReturn(java.util.List.of());
        AuthService.Completion completion = service.googleLogin("ANA@example.com", "google-subject", "Ana Pérez", true, null);
        assertEquals(id, completion.principal().accountId()); verify(store).createIdentity(id, "GOOGLE", "google-subject", null); verify(store).linkLegacyMemberships(after, "ana@example.com", "google-subject");
    }
}
