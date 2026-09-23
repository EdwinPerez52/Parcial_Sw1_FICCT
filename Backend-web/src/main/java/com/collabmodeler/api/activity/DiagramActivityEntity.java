package com.collabmodeler.api.activity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagram_activity")
public class DiagramActivityEntity {
    @Id private UUID id;
    @Column(name = "diagram_id", nullable = false) private UUID diagramId;
    @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @Column(nullable = false, length = 500) private String summary;
    @Column(name = "actor_subject", nullable = false) private String actorSubject;
    @Column(name = "actor_name", nullable = false) private String actorName;
    @Column(name = "element_id") private UUID elementId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected DiagramActivityEntity() {}
    public DiagramActivityEntity(UUID diagramId, String eventType, String summary, String actorSubject,
                                 String actorName, UUID elementId) {
        this.id = UUID.randomUUID(); this.diagramId = diagramId; this.eventType = eventType;
        this.summary = summary; this.actorSubject = actorSubject; this.actorName = actorName;
        this.elementId = elementId; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getDiagramId() { return diagramId; }
    public String getEventType() { return eventType; }
    public String getSummary() { return summary; }
    public String getActorName() { return actorName; }
    public UUID getElementId() { return elementId; }
    public Instant getCreatedAt() { return createdAt; }
}
