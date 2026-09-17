package com.collabmodeler.api.access;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagram_members", uniqueConstraints = @UniqueConstraint(columnNames = {"diagram_id", "subject"}))
public class DiagramMemberEntity {
    @Id private UUID id;
    @Column(name = "diagram_id", nullable = false) private UUID diagramId;
    @Column(name = "account_id") private UUID accountId;
    @Column(nullable = false) private String subject;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(nullable = false, length = 24) private String role;
    @Column(name = "joined_at", nullable = false) private Instant joinedAt;

    protected DiagramMemberEntity() {}
    public DiagramMemberEntity(UUID diagramId, String subject, String displayName, String role) {
        this.id = UUID.randomUUID(); this.diagramId = diagramId; this.subject = subject;
        this.displayName = displayName; this.role = role; this.joinedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getDiagramId() { return diagramId; }
    public UUID getAccountId() { return accountId; }
    public String getSubject() { return subject; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
}
