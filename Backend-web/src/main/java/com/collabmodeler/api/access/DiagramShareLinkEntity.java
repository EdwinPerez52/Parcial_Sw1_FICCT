package com.collabmodeler.api.access;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagram_share_links")
public class DiagramShareLinkEntity {
    @Id private UUID id;
    @Column(name = "diagram_id", nullable = false) private UUID diagramId;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;

    protected DiagramShareLinkEntity() {}
    public DiagramShareLinkEntity(UUID diagramId, String tokenHash) {
        this.id = UUID.randomUUID();
        this.diagramId = diagramId;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDiagramId() { return diagramId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
