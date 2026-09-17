package com.collabmodeler.api.comment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class CommentEntity {
    @Id private UUID id;
    @Column(name = "diagram_id", nullable = false) private UUID diagramId;
    @Column(name = "target_type", nullable = false, length = 32) private String targetType;
    @Column(name = "target_id") private UUID targetId;
    @Column(name = "parent_comment_id") private UUID parentCommentId;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(name = "author_subject", nullable = false) private String authorSubject;
    @Column(name = "author_name", nullable = false) private String authorName;
    @Column(nullable = false) private boolean resolved;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolved_by_subject") private String resolvedBySubject;
    @Version @Column(nullable = false) private long version;

    protected CommentEntity() {}
    public CommentEntity(UUID diagramId, String targetType, UUID targetId, String body, String authorSubject, String authorName) {
        this(diagramId, targetType, targetId, null, body, authorSubject, authorName);
    }
    public CommentEntity(UUID diagramId, String targetType, UUID targetId, UUID parentCommentId,
                         String body, String authorSubject, String authorName) {
        this.id = UUID.randomUUID(); this.diagramId = diagramId; this.targetType = targetType; this.targetId = targetId;
        this.parentCommentId = parentCommentId; this.body = body; this.authorSubject = authorSubject; this.authorName = authorName;
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public UUID getId() { return id; }
    public UUID getDiagramId() { return diagramId; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public UUID getParentCommentId() { return parentCommentId; }
    public String getBody() { return body; }
    public String getAuthorSubject() { return authorSubject; }
    public String getAuthorName() { return authorName; }
    public boolean isResolved() { return resolved; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolvedBySubject() { return resolvedBySubject; }
    public long getVersion() { return version; }
    public void setResolved(boolean resolved, String subject) {
        this.resolved = resolved; this.updatedAt = Instant.now();
        this.resolvedAt = resolved ? updatedAt : null; this.resolvedBySubject = resolved ? subject : null;
    }
}
