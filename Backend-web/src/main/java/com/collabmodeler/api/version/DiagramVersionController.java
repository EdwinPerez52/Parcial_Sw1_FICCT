package com.collabmodeler.api.version;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.activity.DiagramActivityService;
import com.collabmodeler.api.collaboration.CollaborationEventPublisher;
import com.collabmodeler.api.diagram.DiagramDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagrams/{diagramId}/versions")
public class DiagramVersionController {
    private final DiagramVersionService versions; private final AccessService access;
    private final CollaborationEventPublisher events; private final DiagramActivityService activity;
    public DiagramVersionController(DiagramVersionService versions, AccessService access,
                                    CollaborationEventPublisher events, DiagramActivityService activity) {
        this.versions = versions; this.access = access; this.events = events; this.activity = activity;
    }
    public record CreateVersionRequest(@NotBlank String label) {}
    public record RestoreVersionRequest(@PositiveOrZero long expectedRevision) {}
    public record VersionResponse(UUID id, long sourceRevision, String label, String authorName,
                                  java.time.Instant createdAt, DiagramDocument snapshot) {}

    @GetMapping
    List<VersionResponse> list(@PathVariable UUID diagramId, Principal principal) {
        access.requireMember(diagramId, AccessController.subject(principal));
        return versions.list(diagramId).stream().map(value -> response(diagramId, value)).toList();
    }
    @GetMapping("/{versionId}")
    VersionResponse preview(@PathVariable UUID diagramId, @PathVariable UUID versionId, Principal principal) {
        access.requireMember(diagramId, AccessController.subject(principal));
        DiagramVersionEntity entity = versions.list(diagramId).stream().filter(value -> value.getId().equals(versionId)).findFirst()
            .orElseThrow(() -> new com.collabmodeler.api.support.NotFoundException("Versión no encontrada"));
        return response(diagramId, entity);
    }
    @PostMapping
    DiagramVersionEntity create(@PathVariable UUID diagramId, @Valid @RequestBody CreateVersionRequest request, Principal principal) {
        AccessController.requireVerified(principal); access.requireEditor(diagramId, AccessController.subject(principal));
        DiagramVersionEntity saved = versions.create(diagramId, request.label(), AccessController.subject(principal), AccessController.displayName(principal));
        activity.record(diagramId, "VERSION_CREATED", "Creó el hito " + saved.getLabel(),
            AccessController.subject(principal), AccessController.displayName(principal), null);
        return saved;
    }
    @PostMapping("/{versionId}/restore")
    DiagramDocument restore(@PathVariable UUID diagramId, @PathVariable UUID versionId,
                            @Valid @RequestBody RestoreVersionRequest request, Principal principal) {
        AccessController.requireVerified(principal); access.requireEditor(diagramId, AccessController.subject(principal));
        DiagramDocument restored = versions.restore(diagramId, versionId, request.expectedRevision(),
            AccessController.subject(principal), AccessController.displayName(principal));
        events.publish(diagramId, "MODEL_RESTORED", restored);
        activity.record(diagramId, "VERSION_RESTORED", "Restauró un hito como una nueva revisión",
            AccessController.subject(principal), AccessController.displayName(principal), null);
        return restored;
    }
    private VersionResponse response(UUID diagramId, DiagramVersionEntity value) {
        return new VersionResponse(value.getId(), value.getSourceRevision(), value.getLabel(), value.getAuthorName(),
            value.getCreatedAt(), versions.preview(diagramId, value.getId()));
    }
}
