package com.collabmodeler.api.version;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagram_versions")
public class DiagramVersionEntity {
    @Id private UUID id;
    @Column(name = "diagram_id", nullable = false) private UUID diagramId;
    @Column(name = "source_revision", nullable = false) private long sourceRevision;
    @Column(nullable = false, length = 180) private String label;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text") private String snapshotJson;
    @Column(name = "author_subject", nullable = false) private String authorSubject;
    @Column(name = "author_name", nullable = false) private String authorName;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected DiagramVersionEntity() {}
    public DiagramVersionEntity(UUID diagramId, long sourceRevision, String label, String snapshotJson, String authorSubject, String authorName) {
        this.id = UUID.randomUUID(); this.diagramId = diagramId; this.sourceRevision = sourceRevision; this.label = label;
        this.snapshotJson = snapshotJson; this.authorSubject = authorSubject; this.authorName = authorName; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; } public UUID getDiagramId() { return diagramId; }
    public long getSourceRevision() { return sourceRevision; } public String getLabel() { return label; }
    public String getSnapshotJson() { return snapshotJson; } public String getAuthorSubject() { return authorSubject; }
    public String getAuthorName() { return authorName; } public Instant getCreatedAt() { return createdAt; }
}
