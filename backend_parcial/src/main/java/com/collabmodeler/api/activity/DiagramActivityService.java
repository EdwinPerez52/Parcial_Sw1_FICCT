package com.collabmodeler.api.activity;

import com.collabmodeler.api.collaboration.CollaborationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DiagramActivityService {
    private final DiagramActivityRepository repository;
    private final CollaborationEventPublisher events;
    public DiagramActivityService(DiagramActivityRepository repository, CollaborationEventPublisher events) {
        this.repository = repository; this.events = events;
    }
    public DiagramActivityEntity record(UUID diagramId, String type, String summary, String subject,
                                        String name, UUID elementId) {
        DiagramActivityEntity saved = repository.save(new DiagramActivityEntity(diagramId, type,
            summary.substring(0, Math.min(500, summary.length())), subject, name, elementId));
        events.publish(diagramId, "ACTIVITY", saved);
        return saved;
    }
    public List<DiagramActivityEntity> recent(UUID diagramId, int limit) {
        return repository.findByDiagramIdOrderByCreatedAtDesc(diagramId, PageRequest.of(0, Math.min(100, Math.max(1, limit))));
    }
}
