package com.collabmodeler.api.comment;

import com.collabmodeler.api.activity.DiagramActivityService;
import com.collabmodeler.api.collaboration.CollaborationEventPublisher;
import com.collabmodeler.api.diagram.DiagramDocument;
import com.collabmodeler.api.diagram.DiagramService;
import com.collabmodeler.api.support.NotFoundException;
import com.collabmodeler.api.support.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CommentService {
    private static final Set<String> TARGETS = Set.of("DIAGRAM", "CLASS", "ATTRIBUTE", "ASSOCIATION", "ENUMERATION", "GENERALIZATION");
    private final CommentRepository comments; private final DiagramService diagrams;
    private final CollaborationEventPublisher events; private final DiagramActivityService activity;
    public CommentService(CommentRepository comments, DiagramService diagrams, CollaborationEventPublisher events,
                          DiagramActivityService activity) {
        this.comments = comments; this.diagrams = diagrams; this.events = events; this.activity = activity;
    }
    public List<CommentEntity> list(UUID diagramId) { return comments.findByDiagramIdOrderByCreatedAt(diagramId); }
    @Transactional
    public CommentEntity create(UUID diagramId, String targetType, UUID targetId, UUID parentCommentId,
                                String body, String subject, String name) {
        String normalizedBody = body == null ? "" : body.trim();
        if (normalizedBody.isEmpty() || normalizedBody.length() > 4000) throw new IllegalArgumentException("El comentario admite entre 1 y 4000 caracteres");
        if (!TARGETS.contains(targetType)) throw new IllegalArgumentException("Tipo de destino de comentario inválido");
        CommentEntity parent = null;
        if (parentCommentId != null) {
            parent = comments.findById(parentCommentId).filter(value -> value.getDiagramId().equals(diagramId))
                .orElseThrow(() -> new NotFoundException("Conversación no encontrada"));
            targetType = parent.getTargetType(); targetId = parent.getTargetId();
        }
        validateTarget(diagrams.get(diagramId), targetType, targetId);
        CommentEntity saved = comments.save(new CommentEntity(diagramId, targetType, targetId,
            parent == null ? null : parent.getId(), normalizedBody, subject, name));
        events.publish(diagramId, "COMMENT_CREATED", saved);
        activity.record(diagramId, "COMMENT_CREATED", parent == null ? "Agregó un comentario" : "Respondió una conversación",
            subject, name, targetId);
        return saved;
    }
    @Transactional
    public CommentEntity resolve(UUID diagramId, UUID commentId, boolean resolved, long expectedVersion, String subject, String name) {
        CommentEntity comment = comments.findById(commentId).filter(value -> value.getDiagramId().equals(diagramId))
            .orElseThrow(() -> new NotFoundException("Comentario no encontrado"));
        if (comment.getVersion() != expectedVersion) {
            throw new ConflictException("COMMENT_VERSION_MISMATCH", "La conversación cambió en otra sesión",
                null, commentId, expectedVersion, comment.getVersion());
        }
        comment.setResolved(resolved, subject);
        CommentEntity saved = comments.save(comment);
        events.publish(diagramId, "COMMENT_UPDATED", saved);
        activity.record(diagramId, resolved ? "COMMENT_RESOLVED" : "COMMENT_REOPENED",
            resolved ? "Resolvió una conversación" : "Reabrió una conversación", subject, name, comment.getTargetId());
        return saved;
    }
    private void validateTarget(DiagramDocument diagram, String type, UUID id) {
        if ("DIAGRAM".equals(type)) { if (id != null) throw new IllegalArgumentException("El comentario general no admite targetId"); return; }
        if (id == null) throw new IllegalArgumentException("El comentario anclado requiere targetId");
        boolean exists = switch (type) {
            case "CLASS" -> diagram.classes().stream().anyMatch(value -> value.id().equals(id));
            case "ATTRIBUTE" -> diagram.classes().stream().flatMap(value -> value.attributes().stream()).anyMatch(value -> value.id().equals(id));
            case "ASSOCIATION" -> diagram.associations().stream().anyMatch(value -> value.id().equals(id));
            case "ENUMERATION" -> diagram.enumerations().stream().anyMatch(value -> value.id().equals(id));
            case "GENERALIZATION" -> diagram.generalizations().stream().anyMatch(value -> value.id().equals(id));
            default -> false;
        };
        if (!exists) throw new NotFoundException("El elemento comentado ya no existe");
    }
}
