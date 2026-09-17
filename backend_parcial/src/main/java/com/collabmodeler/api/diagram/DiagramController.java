package com.collabmodeler.api.diagram;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.activity.DiagramActivityService;
import com.collabmodeler.api.collaboration.CollaborationEventPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagrams")
public class DiagramController {
    private final DiagramService service;
    private final CollaborationEventPublisher events;
    private final AccessService access;
    private final DiagramActivityService activity;

    public DiagramController(DiagramService service, CollaborationEventPublisher events, AccessService access,
                             DiagramActivityService activity) {
        this.service = service; this.events = events; this.access = access; this.activity = activity;
    }

    public record CreateDiagramRequest(@NotBlank String name) {}

    @GetMapping
    List<AccessService.ProjectSummary> list(Principal principal) {
        return access.projects(subject(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DiagramDocument create(@Valid @RequestBody CreateDiagramRequest request, Principal principal) {
        AccessController.requireVerified(principal);
        return service.create(request.name(), subject(principal), displayName(principal));
    }

    @GetMapping("/{id}")
    DiagramDocument get(@PathVariable UUID id, Principal principal) {
        access.requireMember(id, subject(principal));
        return service.get(id);
    }

    @GetMapping("/{id}/operations")
    List<DiagramOperationEntity> operations(@PathVariable UUID id, @RequestParam(defaultValue = "0") long since, Principal principal) {
        access.requireMember(id, subject(principal));
        return service.operationsSince(id, since);
    }

    @PostMapping("/{id}/operations")
    DiagramDocument apply(@PathVariable UUID id, @Valid @RequestBody DiagramOperationRequest request, Principal principal) {
        AccessController.requireVerified(principal);
        access.requireEditor(id, subject(principal));
        DiagramDocument result = service.apply(id, request, subject(principal), displayName(principal));
        events.publish(id, "OPERATION_APPLIED", Map.of("operation", request, "diagram", result));
        activity.record(id, "OPERATION_APPLIED", summary(request.type()), subject(principal), displayName(principal), elementId(request));
        return result;
    }

    private String summary(String type) {
        return switch (type) {
            case "CLASS_CREATED" -> "Creó una clase"; case "CLASS_RENAMED" -> "Renombró una clase";
            case "CLASS_DELETED" -> "Eliminó una clase"; case "CLASS_MOVED" -> "Movió una clase";
            case "ATTRIBUTE_CREATED" -> "Agregó un atributo"; case "ATTRIBUTE_UPDATED" -> "Editó un atributo";
            case "ATTRIBUTE_DELETED" -> "Eliminó un atributo"; case "BATCH" -> "Aplicó un grupo de cambios";
            default -> "Modificó el diagrama";
        };
    }
    private UUID elementId(DiagramOperationRequest request) {
        String value = request.payload().path("id").asText(null);
        try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private String subject(Principal principal) { return AccessController.subject(principal); }
    private String displayName(Principal principal) {
        return AccessController.displayName(principal);
    }
}
