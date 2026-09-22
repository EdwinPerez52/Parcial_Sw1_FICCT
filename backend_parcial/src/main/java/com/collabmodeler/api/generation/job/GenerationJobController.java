package com.collabmodeler.api.generation.job;

import com.collabmodeler.api.access.*;
import com.collabmodeler.api.generation.MobileSpecService;
import com.collabmodeler.api.version.DiagramVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/diagrams/{diagramId}/generation-jobs")
public class GenerationJobController {
    private final GenerationJobService service; private final AccessService access; private final ArtifactStorage storage;
    private final ArtifactLinkService links; private final DiagramVersionService versions; private final MobileSpecService mobileSpecs;
    private final ObjectMapper mapper;
    public GenerationJobController(GenerationJobService service, AccessService access, ArtifactStorage storage,
                                   ArtifactLinkService links, DiagramVersionService versions, MobileSpecService mobileSpecs, ObjectMapper mapper) {
        this.service = service; this.access = access; this.storage = storage; this.links = links;
        this.versions = versions; this.mobileSpecs = mobileSpecs; this.mapper = mapper;
    }
    public record CreateRequest(UUID versionId,
        @Pattern(regexp = "^[a-z][a-z0-9_.]*$", message = "groupId inválido") String groupId,
        @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "artifactId inválido") String artifactId) {}

    @PostMapping
    ResponseEntity<GenerationJobService.JobResponse> create(@PathVariable UUID diagramId,
        @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody(required = false) CreateRequest request, Principal principal) {
        AccessController.requireVerified(principal); String subject = AccessController.subject(principal); access.requireMember(diagramId, subject);
        CreateRequest input = request == null ? new CreateRequest(null, null, null) : request;
        var job = service.create(diagramId, input.versionId(), input.groupId(), input.artifactId(), subject,
            AccessController.displayName(principal), idempotencyKey);
        return ResponseEntity.accepted().body(service.response(job));
    }
    @GetMapping
    List<GenerationJobService.JobResponse> list(@PathVariable UUID diagramId, Principal principal) {
        access.requireMember(diagramId, AccessController.subject(principal)); return service.list(diagramId).stream().map(service::response).toList();
    }
    @GetMapping("/{jobId}")
    GenerationJobService.JobResponse get(@PathVariable UUID diagramId, @PathVariable UUID jobId, Principal principal) {
        access.requireMember(diagramId, AccessController.subject(principal)); return service.response(service.get(diagramId, jobId));
    }
    @PostMapping("/{jobId}/retry")
    ResponseEntity<GenerationJobService.JobResponse> retry(@PathVariable UUID diagramId, @PathVariable UUID jobId, Principal principal) {
        AccessController.requireVerified(principal); access.requireMember(diagramId, AccessController.subject(principal));
        return ResponseEntity.accepted().body(service.response(service.retry(diagramId, jobId)));
    }
    @GetMapping("/{jobId}/artifacts/{kind}")
    ResponseEntity<byte[]> download(@PathVariable UUID diagramId, @PathVariable UUID jobId, @PathVariable String kind,
                                    @RequestParam long expires, @RequestParam String signature, Principal principal) {
        access.requireMember(diagramId, AccessController.subject(principal));
        if (!links.valid(diagramId, jobId, kind, expires, signature)) throw new AccessDeniedException("El enlace de descarga expiró o no es válido");
        var job = service.get(diagramId, jobId);
        if (job.getStatus() != GenerationJobStatus.SUCCEEDED || job.getExpiresAt() == null || job.getExpiresAt().isBefore(Instant.now()))
            throw new com.collabmodeler.api.support.NotFoundException("El artefacto todavía no está disponible o expiró");
        String key; String filename;
        if ("backend".equals(kind)) { key = job.getBackendObjectKey(); filename = job.getBackendFilename(); }
        else if ("mobile-spec".equals(kind)) { key = job.getMobileSpecObjectKey(); filename = job.getMobileSpecFilename(); }
        else throw new com.collabmodeler.api.support.NotFoundException("Artefacto no encontrado");
        var artifact = storage.get(key);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(artifact.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION).body(artifact.contents());
    }
    @PostMapping("/{jobId}/agent-spec")
    Map<String, Object> agentSpec(@PathVariable UUID diagramId, @PathVariable UUID jobId, Principal principal) {
        access.requireMember(diagramId, AccessController.subject(principal)); var job = service.get(diagramId, jobId);
        if (job.getStatus() != GenerationJobStatus.SUCCEEDED) throw new IllegalArgumentException("El trabajo todavía no terminó correctamente");
        try {
            var stored = mapper.readValue(storage.get(job.getMobileSpecObjectKey()).contents(), Map.class);
            stored.remove("signature"); stored.put("nonce", UUID.randomUUID().toString());
            String resigned = mobileSpecs.signSpec(stored);
            // The agent is provisioned locally with the HMAC key.  Returning it here
            // would let any browser user forge arbitrary specifications.
            return Map.of("spec", mapper.readValue(resigned, Map.class));
        } catch (Exception e) { throw new IllegalStateException("No se pudo preparar la especificación para el agente", e); }
    }
}
