package com.collabmodeler.api.comment;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagrams/{diagramId}/comments")
public class CommentController {
    private final CommentService comments; private final AccessService access;
    public CommentController(CommentService comments, AccessService access) { this.comments = comments; this.access = access; }
    public record CreateCommentRequest(@NotBlank String targetType, UUID targetId, UUID parentCommentId, @NotBlank String body) {}
    public record ResolveCommentRequest(@NotNull Boolean resolved) {}

    @GetMapping
    List<CommentEntity> list(@PathVariable UUID diagramId, Principal principal) {
        access.requireMember(diagramId, AccessController.subject(principal));
        return comments.list(diagramId);
    }
    @PostMapping
    CommentEntity create(@PathVariable UUID diagramId, @Valid @RequestBody CreateCommentRequest request, Principal principal) {
        AccessController.requireVerified(principal); access.requireMember(diagramId, AccessController.subject(principal));
        return comments.create(diagramId, request.targetType(), request.targetId(), request.parentCommentId(), request.body(),
            AccessController.subject(principal), AccessController.displayName(principal));
    }
    @PatchMapping("/{commentId}")
    CommentEntity resolve(@PathVariable UUID diagramId, @PathVariable UUID commentId,
                          @Valid @RequestBody ResolveCommentRequest request, Principal principal) {
        AccessController.requireVerified(principal); access.requireEditor(diagramId, AccessController.subject(principal));
        return comments.resolve(diagramId, commentId, request.resolved(), AccessController.subject(principal),
            AccessController.displayName(principal));
    }
}
