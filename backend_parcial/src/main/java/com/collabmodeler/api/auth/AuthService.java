package com.collabmodeler.api.auth;

import com.collabmodeler.api.diagram.DiagramRepository;
import com.collabmodeler.api.support.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {
    public record InvitationInfo(boolean valid, String email, UUID diagramId, boolean registrationAllowed) {}
    public record Completion(AccountPrincipal principal, UUID diagramId) {}
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{10,128}$");
    private final AuthStore store;
    private final DiagramRepository diagrams;
    private final PasswordEncoder passwords;
    private final AuthMailService mail;
    private final SecureRandom random = new SecureRandom();
    private final Duration verificationTtl;
    private final Duration resetTtl;

    public AuthService(AuthStore store, DiagramRepository diagrams, PasswordEncoder passwords, AuthMailService mail,
                       @Value("${app.auth.verification-minutes:60}") long verificationMinutes,
                       @Value("${app.auth.reset-minutes:30}") long resetMinutes) {
        this.store = store; this.diagrams = diagrams; this.passwords = passwords; this.mail = mail;
        this.verificationTtl = Duration.ofMinutes(verificationMinutes); this.resetTtl = Duration.ofMinutes(resetMinutes);
    }

    public InvitationInfo invitation(String token) {
        if (token == null || token.isBlank()) return new InvitationInfo(false, null, null, false);
        String hash = hash(token); Instant now = Instant.now();
        Optional<AuthStore.Invitation> invitation = store.invitation(hash).filter(value -> value.active(now));
        if (invitation.isPresent()) {
            var value = invitation.get();
            return new InvitationInfo(true, value.email(), value.diagramId(), true);
        }
        return diagrams.findByShareTokenHash(hash)
            .map(diagram -> new InvitationInfo(true, null, diagram.getId(), true))
            .orElseGet(() -> new InvitationInfo(false, null, null, false));
    }

    @Transactional
    public void register(String fullName, String emailValue, String password, String confirmation, String invitationToken) {
        String email = normalizeEmail(emailValue); validatePassword(password, confirmation);
        String tokenHash = hash(invitationToken == null ? "" : invitationToken); Instant now = Instant.now();
        boolean hasToken = invitationToken != null && !invitationToken.isBlank();
        AuthStore.Invitation formal = hasToken ? store.invitation(tokenHash).filter(value -> value.active(now)).orElse(null) : null;
        UUID diagramId = hasToken ? (formal == null ? diagrams.findByShareTokenHash(tokenHash).map(value -> value.getId()).orElse(null) : formal.diagramId()) : null;
        if (hasToken && formal == null && diagramId == null) throw new AuthException(HttpStatus.FORBIDDEN, "INVITATION_REQUIRED", "Necesitas una invitación válida para registrarte.");
        if (formal != null && formal.email() != null && !formal.email().equals(email)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "INVITATION_EMAIL_MISMATCH", "La invitación pertenece a otro correo.");
        }
        AuthStore.Account existing = store.accountByEmail(email).orElse(null);
        if (existing != null) {
            if (!existing.verified()) issueVerification(existing);
            return;
        }
        try {
            boolean autoVerify = !hasToken;
            AuthStore.Account account = store.createAccount(email, cleanName(fullName), autoVerify);
            store.createIdentity(account.id(), "LOCAL", email, passwords.encode(password));
            if (formal != null) store.acceptInvitation(formal.id(), account.id());
            if (diagramId != null) store.addPendingJoin(account.id(), diagramId);
            if (!autoVerify) {
                issueVerification(account);
            }
        } catch (DataIntegrityViolationException exception) {
            // Una carrera con otro registro conserva la respuesta genérica para no enumerar cuentas.
            return;
        }
    }

    @Transactional
    public Completion verify(String rawToken) {
        AuthStore.OneTimeToken token = store.verificationToken(hash(rawToken == null ? "" : rawToken))
            .filter(value -> value.active(Instant.now()))
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "El enlace de verificación no es válido o ha vencido."));
        if (!store.useVerificationToken(token.id())) throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "El enlace de verificación ya fue utilizado.");
        store.verify(token.accountId());
        AuthStore.Account account = store.accountById(token.accountId()).orElseThrow();
        UUID destination = store.pendingJoins(account.id()).stream().findFirst().orElse(null);
        store.activatePendingJoins(account);
        for (AuthStore.Invitation invitation : store.acceptedInvitations(account.id())) {
            if (invitation.bootstrapAdmin()) store.makeAdmin(account.id());
            store.consumeInvitation(invitation.id(), account.id());
        }
        account = store.accountById(account.id()).orElseThrow();
        store.linkLegacyMemberships(account, account.email());
        return new Completion(account.principal(), destination);
    }

    @Transactional
    public AccountPrincipal login(String emailValue, String password) {
        String email = normalizeEmail(emailValue);
        AuthStore.Account account = store.accountByEmail(email).orElse(null);
        AuthStore.Identity identity = store.identity("LOCAL", email).orElse(null);
        if (account == null || identity == null || (account.lockedUntil() != null && account.lockedUntil().isAfter(Instant.now()))
            || !passwords.matches(password == null ? "" : password, identity.passwordHash())) {
            if (account != null) {
                int failures = account.failedAttempts() + 1;
                store.recordLoginFailure(account.id(), failures, failures >= 5 ? Instant.now().plus(Duration.ofMinutes(15)) : null);
            }
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Correo o contraseña inválidos.");
        }
        store.clearLoginFailures(account.id()); store.linkLegacyMemberships(account, email);
        return store.accountById(account.id()).orElseThrow().principal();
    }

    @Transactional
    public void resendVerification(String emailValue) {
        store.accountByEmail(normalizeEmail(emailValue)).filter(account -> !account.verified()).ifPresent(this::issueVerification);
    }
    @Transactional
    public void requestReset(String emailValue) {
        store.accountByEmail(normalizeEmail(emailValue)).filter(AuthStore.Account::verified).ifPresent(account -> {
            String token = randomToken(); store.invalidateResetTokens(account.id());
            store.createResetToken(account.id(), hash(token), Instant.now().plus(resetTtl)); mail.reset(account.email(), token);
        });
    }
    @Transactional
    public void resetPassword(String rawToken, String password, String confirmation) {
        validatePassword(password, confirmation);
        AuthStore.OneTimeToken token = store.resetToken(hash(rawToken == null ? "" : rawToken)).filter(value -> value.active(Instant.now()))
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "El enlace de recuperación no es válido o ha vencido."));
        if (!store.useResetToken(token.id())) throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "El enlace de recuperación ya fue utilizado.");
        store.updatePassword(token.accountId(), passwords.encode(password));
        store.invalidateResetTokens(token.accountId()); store.clearLoginFailures(token.accountId());
    }

    private void acceptInvitation(String rawToken, AuthStore.Account account, UUID diagramId) {
        store.invitation(hash(rawToken)).ifPresent(value -> store.acceptInvitation(value.id(), account.id()));
        if (diagramId != null) store.addPendingJoin(account.id(), diagramId);
    }
    private void issueVerification(AuthStore.Account account) {
        String token = randomToken(); store.invalidateVerificationTokens(account.id());
        store.createVerificationToken(account.id(), hash(token), Instant.now().plus(verificationTtl));
        try {
            mail.verification(account.email(), token);
        } catch (Exception ignored) {
            // Ignorar si el servicio de correo no está disponible localmente
        }
    }
    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String normalizeEmail(String email) {
        if (email == null || !email.contains("@")) throw new IllegalArgumentException("El correo no es válido.");
        return email.trim().toLowerCase(Locale.ROOT);
    }
    private static String cleanName(String name) {
        String value = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (value.length() < 2 || value.length() > 180) throw new IllegalArgumentException("El nombre completo debe tener entre 2 y 180 caracteres.");
        return value;
    }
    private static void validatePassword(String password, String confirmation) {
        if (password == null || !password.equals(confirmation)) throw new IllegalArgumentException("Las contraseñas no coinciden.");
        if (!PASSWORD.matcher(password).matches()) throw new IllegalArgumentException("La contraseña debe tener 10 caracteres, mayúscula, minúscula y número.");
    }
    public static String hash(String value) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
