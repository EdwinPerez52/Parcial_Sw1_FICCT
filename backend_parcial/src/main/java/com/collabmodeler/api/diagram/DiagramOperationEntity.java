package com.collabmodeler.api.diagram;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagram_operations")
public class DiagramOperationEntity {
    @Id private UUID id;
    @Column(name = "diagram_id", nullable = false) private UUID diagramId;
    @Column(name = "base_revision", nullable = false) private long baseRevision;
    @Column(name = "result_revision", nullable = false) private long resultRevision;
    @Column(nullable = false, length = 80) private String type;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text") private String payloadJson;
    @Column(name = "author_subject", nullable = false) private String authorSubject;
    @Column(name = "author_name", nullable = false) private String authorName;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected DiagramOperationEntity() {}

    public DiagramOperationEntity(UUID id, UUID diagramId, long baseRevision, long resultRevision,
                                  String type, String payloadJson, String authorSubject, String authorName) {
        this.id = id; this.diagramId = diagramId; this.baseRevision = baseRevision; this.resultRevision = resultRevision;
        this.type = type; this.payloadJson = payloadJson; this.authorSubject = authorSubject;
        this.authorName = authorName; this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getDiagramId() { return diagramId; }
    public long getBaseRevision() { return baseRevision; }
    public long getResultRevision() { return resultRevision; }
    public String getType() { return type; }
    public String getPayloadJson() { return payloadJson; }
    public String getAuthorSubject() { return authorSubject; }
    public String getAuthorName() { return authorName; }
    public Instant getCreatedAt() { return createdAt; }
}
