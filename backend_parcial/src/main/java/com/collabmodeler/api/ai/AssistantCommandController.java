package com.collabmodeler.api.ai;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.activity.DiagramActivityService;
import com.collabmodeler.api.collaboration.CollaborationEventPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagrams/{diagramId}/assistant")
public class AssistantCommandController {
    private final AssistantCommandService assistant; private final AccessService access;
    private final CollaborationEventPublisher events; private final DiagramActivityService activity;
    public AssistantCommandController(AssistantCommandService assistant, AccessService access,
                                      CollaborationEventPublisher events, DiagramActivityService activity) {
        this.assistant = assistant; this.access = access; this.events = events; this.activity = activity;
    }
    public record InterpretRequest(@NotBlank String instruction) {}
    public record ApplyRequest(boolean confirmed) {}

    @PostMapping("/proposals")
    AssistantCommandService.Proposal interpret(@PathVariable UUID diagramId, @Valid @RequestBody InterpretRequest request, Principal principal) {
        AccessController.requireVerified(principal); String subject = AccessController.subject(principal); access.requireEditor(diagramId, subject);
        return assistant.interpret(diagramId, request.instruction(), subject);
    }

    @PostMapping("/proposals/{proposalId}/apply")
    AssistantCommandService.AppliedProposal apply(@PathVariable UUID diagramId, @PathVariable UUID proposalId,
                                                   @RequestBody(required = false) ApplyRequest request, Principal principal) {
        AccessController.requireVerified(principal); String subject = AccessController.subject(principal); access.requireEditor(diagramId, subject);
        var result = assistant.apply(diagramId, proposalId, subject, AccessController.displayName(principal), request != null && request.confirmed());
        events.publish(diagramId, "OPERATION_APPLIED", Map.of("operation", result.operation(), "diagram", result.diagram()));
        activity.record(diagramId, "ASSISTANT_OPERATION_APPLIED", "Aplicó " + result.operation().type() + " mediante " + result.provider(), subject, AccessController.displayName(principal), null);
        return result;
    }
}
