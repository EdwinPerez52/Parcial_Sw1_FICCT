package com.collabmodeler.api.ai;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_proposals")
public class AssistantProposalEntity {
    @Id private UUID id;
    @Column(name = "diagram_id", nullable = false) private UUID diagramId;
    @Column(name = "author_subject", nullable = false) private String authorSubject;
    @Column(nullable = false, length = 80) private String provider;
    @Column(name = "instruction_hash", nullable = false, length = 64) private String instructionHash;
    @Column(name = "operation_json", nullable = false, columnDefinition = "text") private String operationJson;
    @Column(name = "requires_confirmation", nullable = false) private boolean requiresConfirmation;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "applied_at") private Instant appliedAt;

    protected AssistantProposalEntity() {}
    public AssistantProposalEntity(UUID id, UUID diagramId, String authorSubject, String provider,
                                   String instructionHash, String operationJson, boolean requiresConfirmation,
                                   Instant expiresAt) {
        this.id = id; this.diagramId = diagramId; this.authorSubject = authorSubject; this.provider = provider;
        this.instructionHash = instructionHash; this.operationJson = operationJson;
        this.requiresConfirmation = requiresConfirmation; this.createdAt = Instant.now(); this.expiresAt = expiresAt;
    }
    public UUID getId() { return id; } public UUID getDiagramId() { return diagramId; }
    public String getAuthorSubject() { return authorSubject; } public String getProvider() { return provider; }
    public String getOperationJson() { return operationJson; } public boolean isRequiresConfirmation() { return requiresConfirmation; }
    public Instant getExpiresAt() { return expiresAt; } public Instant getAppliedAt() { return appliedAt; }
    public void markApplied() { appliedAt = Instant.now(); }
}
