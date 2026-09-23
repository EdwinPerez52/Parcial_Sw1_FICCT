package com.collabmodeler.api.generation.job;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generation_jobs", uniqueConstraints = @UniqueConstraint(name = "uq_generation_job_idempotency", columnNames = {"diagram_id", "requester_subject", "idempotency_key"}))
public class GenerationJobEntity {
    @Id private UUID id;
    @Column(name = "diagram_id", nullable = false) private UUID diagramId;
    @Column(name = "version_id", nullable = false) private UUID versionId;
    @Column(name = "source_revision", nullable = false) private long sourceRevision;
    @Column(name = "requester_subject", nullable = false) private String requesterSubject;
    @Column(name = "requester_name", nullable = false) private String requesterName;
    @Column(name = "idempotency_key", nullable = false, length = 120) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private GenerationJobStatus status;
    @Column(nullable = false) private int attempt;
    @Column(name = "group_id", nullable = false, length = 180) private String groupId;
    @Column(name = "artifact_id", nullable = false, length = 180) private String artifactId;
    @Column(name = "backend_object_key", length = 500) private String backendObjectKey;
    @Column(name = "mobile_spec_object_key", length = 500) private String mobileSpecObjectKey;
    @Column(name = "backend_filename") private String backendFilename;
    @Column(name = "mobile_spec_filename") private String mobileSpecFilename;
    @Column(name = "error_code", length = 80) private String errorCode;
    @Column(name = "error_message", length = 500) private String errorMessage;
    @Column(name = "queued_at", nullable = false) private Instant queuedAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "expires_at") private Instant expiresAt;

    protected GenerationJobEntity() {}

    public GenerationJobEntity(UUID diagramId, UUID versionId, long sourceRevision, String requesterSubject,
                               String requesterName, String idempotencyKey, String groupId, String artifactId) {
        this.id = UUID.randomUUID(); this.diagramId = diagramId; this.versionId = versionId;
        this.sourceRevision = sourceRevision; this.requesterSubject = requesterSubject; this.requesterName = requesterName;
        this.idempotencyKey = idempotencyKey; this.groupId = groupId; this.artifactId = artifactId;
        this.status = GenerationJobStatus.QUEUED; this.queuedAt = Instant.now();
    }

    public void markRunning() { status = GenerationJobStatus.RUNNING; startedAt = Instant.now(); attempt++; errorCode = null; errorMessage = null; }
    public void markSucceeded(String backendKey, String specKey, String backendName, String specName, Instant expiresAt) {
        status = GenerationJobStatus.SUCCEEDED; backendObjectKey = backendKey; mobileSpecObjectKey = specKey;
        backendFilename = backendName; mobileSpecFilename = specName; completedAt = Instant.now(); this.expiresAt = expiresAt;
        errorCode = null; errorMessage = null;
    }
    public void markFailed(String code, String safeMessage) { status = GenerationJobStatus.FAILED; errorCode = code; errorMessage = safeMessage; completedAt = Instant.now(); }
    public void requeue() { status = GenerationJobStatus.QUEUED; queuedAt = Instant.now(); startedAt = null; completedAt = null; errorCode = null; errorMessage = null; }

    public UUID getId() { return id; } public UUID getDiagramId() { return diagramId; } public UUID getVersionId() { return versionId; }
    public long getSourceRevision() { return sourceRevision; } public String getRequesterSubject() { return requesterSubject; }
    public String getRequesterName() { return requesterName; } public String getIdempotencyKey() { return idempotencyKey; }
    public GenerationJobStatus getStatus() { return status; } public int getAttempt() { return attempt; }
    public String getGroupId() { return groupId; } public String getArtifactId() { return artifactId; }
    public String getBackendObjectKey() { return backendObjectKey; } public String getMobileSpecObjectKey() { return mobileSpecObjectKey; }
    public String getBackendFilename() { return backendFilename; } public String getMobileSpecFilename() { return mobileSpecFilename; }
    public String getErrorCode() { return errorCode; } public String getErrorMessage() { return errorMessage; }
    public Instant getQueuedAt() { return queuedAt; } public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; } public Instant getExpiresAt() { return expiresAt; }
}
