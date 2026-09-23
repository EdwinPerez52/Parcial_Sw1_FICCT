package com.collabmodeler.api.activity;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagrams/{diagramId}/activity")
public class DiagramActivityController {
    private final DiagramActivityService activity; private final AccessService access;
    public DiagramActivityController(DiagramActivityService activity, AccessService access) { this.activity = activity; this.access = access; }
    @GetMapping
    List<DiagramActivityEntity> list(@PathVariable UUID diagramId, @RequestParam(defaultValue = "30") int limit,
                                     Principal principal) {
        access.requireMember(diagramId, AccessController.subject(principal));
        return activity.recent(diagramId, limit);
    }
}
