package com.collabmodeler.api.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthStore {
    public record Account(UUID id, String email, String fullName, boolean verified, boolean admin,
                          int failedAttempts, Instant lockedUntil) {
        public AccountPrincipal principal() { return new AccountPrincipal(id, email, fullName, verified, admin); }
    }
    public record Identity(UUID id, UUID accountId, String provider, String subject, String passwordHash) {}
    public record Invitation(UUID id, String email, UUID diagramId, boolean bootstrapAdmin,
                             Instant expiresAt, UUID acceptedBy, Instant usedAt, Instant revokedAt) {
        public boolean active(Instant now) { return acceptedBy == null && usedAt == null && revokedAt == null && expiresAt.isAfter(now); }
    }
    public record OneTimeToken(UUID id, UUID accountId, Instant expiresAt, Instant usedAt) {
        public boolean active(Instant now) { return usedAt == null && expiresAt.isAfter(now); }
    }

    private final JdbcTemplate jdbc;
    public AuthStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Account> accountByEmail(String email) {
        return jdbc.query("select * from user_accounts where email=?", (rs, n) -> account(rs), email).stream().findFirst();
    }
    public Optional<Account> accountById(UUID id) {
        return jdbc.query("select * from user_accounts where id=?", (rs, n) -> account(rs), id).stream().findFirst();
    }
    public Optional<Identity> identity(String provider, String subject) {
        return jdbc.query("select * from auth_identities where provider=? and provider_subject=?", (rs, n) ->
            new Identity(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class), rs.getString("provider"),
                rs.getString("provider_subject"), rs.getString("password_hash")), provider, subject).stream().findFirst();
    }
    public Account createAccount(String email, String fullName, boolean verified) {
        UUID id = UUID.randomUUID(); Instant now = Instant.now();
        jdbc.update("insert into user_accounts(id,email,full_name,email_verified,platform_admin,failed_login_attempts,created_at,updated_at) values (?,?,?,?,false,0,?,?)",
            id, email, fullName, verified, Timestamp.from(now), Timestamp.from(now));
        return new Account(id, email, fullName, verified, false, 0, null);
    }
    public void createIdentity(UUID accountId, String provider, String subject, String passwordHash) {
        Instant now = Instant.now();
        jdbc.update("insert into auth_identities(id,account_id,provider,provider_subject,password_hash,created_at,updated_at) values (?,?,?,?,?,?,?)",
            UUID.randomUUID(), accountId, provider, subject, passwordHash, Timestamp.from(now), Timestamp.from(now));
    }
    public void updatePassword(UUID accountId, String passwordHash) {
        jdbc.update("update auth_identities set password_hash=?, updated_at=? where account_id=? and provider='LOCAL'",
            passwordHash, Timestamp.from(Instant.now()), accountId);
    }
    public void recordLoginFailure(UUID accountId, int failures, Instant lockedUntil) {
        jdbc.update("update user_accounts set failed_login_attempts=?, locked_until=?, updated_at=? where id=?",
            failures, lockedUntil == null ? null : Timestamp.from(lockedUntil), Timestamp.from(Instant.now()), accountId);
    }
    public void clearLoginFailures(UUID accountId) {
        jdbc.update("update user_accounts set failed_login_attempts=0, locked_until=null, updated_at=? where id=?", Timestamp.from(Instant.now()), accountId);
    }
    public void verify(UUID accountId) {
        jdbc.update("update user_accounts set email_verified=true, updated_at=? where id=?", Timestamp.from(Instant.now()), accountId);
    }
    public void makeAdmin(UUID accountId) {
        jdbc.update("update user_accounts set platform_admin=true, updated_at=? where id=?", Timestamp.from(Instant.now()), accountId);
    }
    public void updateName(UUID accountId, String name) {
        jdbc.update("update user_accounts set full_name=?, updated_at=? where id=?", name, Timestamp.from(Instant.now()), accountId);
    }

    public Optional<Invitation> invitation(String hash) {
        return jdbc.query("select * from invitations where token_hash=?", (rs, n) -> new Invitation(
            rs.getObject("id", UUID.class), rs.getString("email"), rs.getObject("diagram_id", UUID.class),
            rs.getBoolean("bootstrap_admin"), rs.getTimestamp("expires_at").toInstant(),
            rs.getObject("accepted_by_account_id", UUID.class), timestamp(rs.getTimestamp("used_at")),
            timestamp(rs.getTimestamp("revoked_at"))), hash).stream().findFirst();
    }
    public boolean hasActiveBootstrapInvitation(String email, Instant now) {
        Integer count = jdbc.queryForObject("select count(*) from invitations where email=? and bootstrap_admin=true and used_at is null and revoked_at is null and expires_at>?",
            Integer.class, email, Timestamp.from(now));
        return count != null && count > 0;
    }
    public void createInvitation(String hash, String email, UUID diagramId, boolean admin, Instant expiresAt) {
        jdbc.update("insert into invitations(id,token_hash,email,diagram_id,bootstrap_admin,expires_at,created_at) values (?,?,?,?,?,?,?)",
            UUID.randomUUID(), hash, email, diagramId, admin, Timestamp.from(expiresAt), Timestamp.from(Instant.now()));
    }
    public void acceptInvitation(UUID invitationId, UUID accountId) {
        jdbc.update("update invitations set accepted_by_account_id=? where id=? and accepted_by_account_id is null", accountId, invitationId);
    }
    public void consumeInvitation(UUID invitationId, UUID accountId) {
        jdbc.update("update invitations set used_at=? where id=? and accepted_by_account_id=? and used_at is null",
            Timestamp.from(Instant.now()), invitationId, accountId);
    }

    public void invalidateVerificationTokens(UUID accountId) {
        jdbc.update("update email_verification_tokens set used_at=? where account_id=? and used_at is null", Timestamp.from(Instant.now()), accountId);
    }
    public void createVerificationToken(UUID accountId, String hash, Instant expiresAt) {
        jdbc.update("insert into email_verification_tokens(id,account_id,token_hash,expires_at,created_at) values (?,?,?,?,?)",
            UUID.randomUUID(), accountId, hash, Timestamp.from(expiresAt), Timestamp.from(Instant.now()));
    }
    public Optional<OneTimeToken> verificationToken(String hash) { return token("email_verification_tokens", hash); }
    public boolean useVerificationToken(UUID id) { return jdbc.update("update email_verification_tokens set used_at=? where id=? and used_at is null", Timestamp.from(Instant.now()), id) == 1; }

    public void invalidateResetTokens(UUID accountId) {
        jdbc.update("update password_reset_tokens set used_at=? where account_id=? and used_at is null", Timestamp.from(Instant.now()), accountId);
    }
    public void createResetToken(UUID accountId, String hash, Instant expiresAt) {
        jdbc.update("insert into password_reset_tokens(id,account_id,token_hash,expires_at,created_at) values (?,?,?,?,?)",
            UUID.randomUUID(), accountId, hash, Timestamp.from(expiresAt), Timestamp.from(Instant.now()));
    }
    public Optional<OneTimeToken> resetToken(String hash) { return token("password_reset_tokens", hash); }
    public boolean useResetToken(UUID id) { return jdbc.update("update password_reset_tokens set used_at=? where id=? and used_at is null", Timestamp.from(Instant.now()), id) == 1; }

    public void addPendingJoin(UUID accountId, UUID diagramId) {
        jdbc.update("insert into pending_diagram_joins(account_id,diagram_id,created_at) values (?,?,?) on conflict do nothing",
            accountId, diagramId, Timestamp.from(Instant.now()));
    }
    public List<UUID> pendingJoins(UUID accountId) {
        return jdbc.query("select diagram_id from pending_diagram_joins where account_id=?", (rs, n) -> rs.getObject(1, UUID.class), accountId);
    }
    public void deletePendingJoins(UUID accountId) { jdbc.update("delete from pending_diagram_joins where account_id=?", accountId); }
    public void activatePendingJoins(Account account) {
        String subject = account.principal().getName();
        for (UUID diagramId : pendingJoins(account.id())) {
            jdbc.update("insert into diagram_members(id,diagram_id,subject,display_name,role,joined_at,account_id) values (?,?,?,?, 'EDITOR', ?,?) on conflict (diagram_id,subject) do nothing",
                UUID.randomUUID(), diagramId, subject, account.fullName(), Timestamp.from(Instant.now()), account.id());
        }
        deletePendingJoins(account.id());
    }
    public List<Invitation> acceptedInvitations(UUID accountId) {
        return jdbc.query("select * from invitations where accepted_by_account_id=? and used_at is null", (rs, n) -> new Invitation(
            rs.getObject("id", UUID.class), rs.getString("email"), rs.getObject("diagram_id", UUID.class),
            rs.getBoolean("bootstrap_admin"), rs.getTimestamp("expires_at").toInstant(), accountId,
            timestamp(rs.getTimestamp("used_at")), timestamp(rs.getTimestamp("revoked_at"))), accountId);
    }
    public void linkLegacyMemberships(Account account, String... legacySubjects) {
        String stable = account.principal().getName();
        for (String legacy : legacySubjects) {
            if (legacy == null || legacy.isBlank() || stable.equals(legacy)) continue;
            jdbc.update("update diagram_members set subject=?, display_name=?, account_id=? where subject=? and not exists (select 1 from diagram_members m2 where m2.diagram_id=diagram_members.diagram_id and m2.subject=?)",
                stable, account.fullName(), account.id(), legacy, stable);
            jdbc.update("delete from diagram_members where subject=? and exists (select 1 from diagram_members m2 where m2.diagram_id=diagram_members.diagram_id and m2.subject=?)", legacy, stable);
            jdbc.update("update diagrams set owner_subject=?, owner_account_id=? where owner_subject=?", stable, account.id(), legacy);
        }
    }

    private Optional<OneTimeToken> token(String table, String hash) {
        if (!table.equals("email_verification_tokens") && !table.equals("password_reset_tokens")) throw new IllegalArgumentException("Tabla inválida");
        return jdbc.query("select * from " + table + " where token_hash=?", (rs, n) -> new OneTimeToken(
            rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
            rs.getTimestamp("expires_at").toInstant(), timestamp(rs.getTimestamp("used_at"))), hash).stream().findFirst();
    }
    private static Account account(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Account(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("full_name"),
            rs.getBoolean("email_verified"), rs.getBoolean("platform_admin"), rs.getInt("failed_login_attempts"),
            timestamp(rs.getTimestamp("locked_until")));
    }
    private static Instant timestamp(Timestamp value) { return value == null ? null : value.toInstant(); }
}
