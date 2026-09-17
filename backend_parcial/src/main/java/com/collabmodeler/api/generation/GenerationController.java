package com.collabmodeler.api.generation;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.diagram.DiagramService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagrams/{id}/generation")
public class GenerationController {
    private final BackendGenerator generator;
    private final DiagramService diagrams;
    private final AccessService access;
    public GenerationController(BackendGenerator generator, DiagramService diagrams, AccessService access) {
        this.generator = generator; this.diagrams = diagrams; this.access = access;
    }

    @PostMapping(produces = "application/zip")
    ResponseEntity<byte[]> generate(@PathVariable UUID id,
                                    @RequestParam(defaultValue = "com.generated") String groupId,
                                    @RequestParam(defaultValue = "generated-api") String artifactId,
                                    Principal principal) {
        access.requireMember(id, AccessController.subject(principal));
        var diagram = diagrams.get(id);
        byte[] body = generator.generate(diagram, groupId, artifactId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(artifactId + "-r" + diagram.revision() + ".zip").build().toString())
            .contentType(MediaType.parseMediaType("application/zip")).body(body);
    }
}

