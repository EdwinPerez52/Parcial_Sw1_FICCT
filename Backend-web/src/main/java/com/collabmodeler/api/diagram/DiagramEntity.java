package com.collabmodeler.api.diagram;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagrams")
public class DiagramEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 180) private String name;
    @Column(nullable = false) private long revision;
    @Column(name = "model_json", nullable = false, columnDefinition = "text") private String modelJson;
    @Column(name = "owner_subject", nullable = false) private String ownerSubject;
    @Column(name = "share_token_hash") private String shareTokenHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected DiagramEntity() {}

    public DiagramEntity(UUID id, String name, String modelJson, String ownerSubject) {
        this.id = id; this.name = name; this.modelJson = modelJson; this.ownerSubject = ownerSubject;
        this.revision = 0; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public long getRevision() { return revision; }
    public String getModelJson() { return modelJson; }
    public String getOwnerSubject() { return ownerSubject; }
    public String getShareTokenHash() { return shareTokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void updateModel(long revision, String modelJson) { this.revision = revision; this.modelJson = modelJson; this.updatedAt = Instant.now(); }
    public void setShareTokenHash(String shareTokenHash) { this.shareTokenHash = shareTokenHash; this.updatedAt = Instant.now(); }
}
