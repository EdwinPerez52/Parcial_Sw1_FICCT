package com.collabmodeler.api.generation;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.diagram.DiagramDocument;
import com.collabmodeler.api.diagram.DiagramService;
import com.collabmodeler.api.version.DiagramVersionEntity;
import com.collabmodeler.api.version.DiagramVersionService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagrams/{id}")
public class GenerationController {
    private final BackendGenerator generator;
    private final DiagramService diagrams;
    private final DiagramVersionService versions;
    private final AccessService access;
    private final MobileSpecService mobileSpecService;
    private final OpenApiGenerator openApiGenerator;
    private final FlutterGenerator flutterGenerator;

    public GenerationController(BackendGenerator generator,
                                DiagramService diagrams,
                                DiagramVersionService versions,
                                AccessService access,
                                MobileSpecService mobileSpecService,
                                OpenApiGenerator openApiGenerator,
                                FlutterGenerator flutterGenerator) {
        this.generator = generator;
        this.diagrams = diagrams;
        this.versions = versions;
        this.access = access;
        this.mobileSpecService = mobileSpecService;
        this.openApiGenerator = openApiGenerator;
        this.flutterGenerator = flutterGenerator;
    }

    @RequestMapping(value = "/generation", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/zip")
    public ResponseEntity<byte[]> generate(@PathVariable UUID id,
                                           @RequestParam(required = false) UUID versionId,
                                           @RequestParam(defaultValue = "com.generated") String groupId,
                                           @RequestParam(defaultValue = "generated-api") String artifactId,
                                           Principal principal) {
        access.requireMember(id, AccessController.subject(principal));
        DiagramDocument snapshot = resolveSnapshot(id, versionId, principal);
        String safeArtifactId = sanitizeArtifactId(artifactId);

        byte[] body = generator.generate(snapshot, groupId, safeArtifactId);
        String filename = safeArtifactId + "-r" + snapshot.revision() + ".zip";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(body);
    }

    @RequestMapping(value = "/versions/{versionId}/generation", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/zip")
    public ResponseEntity<byte[]> generateFromVersion(@PathVariable UUID id,
                                                      @PathVariable UUID versionId,
                                                      @RequestParam(defaultValue = "com.generated") String groupId,
                                                      @RequestParam(defaultValue = "generated-api") String artifactId,
                                                      Principal principal) {
        return generate(id, versionId, groupId, artifactId, principal);
    }

    @GetMapping(value = "/mobile-spec", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getMobileSpec(@PathVariable UUID id,
                                                @RequestParam(required = false) UUID versionId,
                                                Principal principal) {
        access.requireMember(id, AccessController.subject(principal));
        DiagramDocument snapshot = resolveSnapshot(id, versionId, principal);
        String openapiYaml = openApiGenerator.generateYaml(snapshot, "generated-api");
        String specJson = mobileSpecService.generateSpecJson(snapshot, versionId, openapiYaml);

        String filename = "modeler-mobile-spec-r" + snapshot.revision() + ".json";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .contentType(MediaType.APPLICATION_JSON)
            .body(specJson);
    }

    @PostMapping(value = "/mobile-spec", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> postMobileSpec(@PathVariable UUID id,
                                                 @RequestParam(required = false) UUID versionId,
                                                 Principal principal) {
        return getMobileSpec(id, versionId, principal);
    }

    @GetMapping(value = "/versions/{versionId}/mobile-spec", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getMobileSpecForVersion(@PathVariable UUID id,
                                                          @PathVariable UUID versionId,
                                                          Principal principal) {
        return getMobileSpec(id, versionId, principal);
    }

    @PostMapping(value = "/flutter-generation", produces = "application/zip")
    public ResponseEntity<byte[]> generateFlutter(@PathVariable UUID id,
                                                  @RequestParam(required = false) UUID versionId,
                                                  @RequestParam(defaultValue = "collab_mobile_app") String appName,
                                                  Principal principal) {
        access.requireMember(id, AccessController.subject(principal));
        DiagramDocument snapshot = resolveSnapshot(id, versionId, principal);
        String openapiYaml = openApiGenerator.generateYaml(snapshot, "generated-api");
        byte[] body = flutterGenerator.generateZip(snapshot, openapiYaml, snapshot.name() + " App");
        String filename = "collab-modeler-" + sanitizeArtifactId(snapshot.name().toLowerCase(Locale.ROOT)) + "-flutter-r" + snapshot.revision() + ".zip";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(body);
    }

    @GetMapping(value = "/flutter-generation", produces = "application/zip")
    public ResponseEntity<byte[]> getFlutterGeneration(@PathVariable UUID id,
                                                       @RequestParam(required = false) UUID versionId,
                                                       @RequestParam(defaultValue = "collab_mobile_app") String appName,
                                                       Principal principal) {
        return generateFlutter(id, versionId, appName, principal);
    }

    @PostMapping(value = "/versions/{versionId}/flutter-generation", produces = "application/zip")
    public ResponseEntity<byte[]> generateFlutterForVersion(@PathVariable UUID id,
                                                            @PathVariable UUID versionId,
                                                            @RequestParam(defaultValue = "collab_mobile_app") String appName,
                                                            Principal principal) {
        return generateFlutter(id, versionId, appName, principal);
    }

    @GetMapping(value = "/versions/{versionId}/flutter-generation", produces = "application/zip")
    public ResponseEntity<byte[]> getFlutterGenerationForVersion(@PathVariable UUID id,
                                                                 @PathVariable UUID versionId,
                                                                 @RequestParam(defaultValue = "collab_mobile_app") String appName,
                                                                 Principal principal) {
        return generateFlutter(id, versionId, appName, principal);
    }

    /**
     * Compatibility endpoint. The agent receives only a signed, one-use contract;
     * source code and the HMAC key must never traverse the browser.
     */
    @PostMapping(value = "/agent-spec", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAgentSpec(@PathVariable UUID id,
                                               @RequestParam(required = false) UUID versionId,
                                               Principal principal) {
        access.requireMember(id, AccessController.subject(principal));
        DiagramDocument snapshot = resolveSnapshot(id, versionId, principal);
        String openapiYaml = openApiGenerator.generateYaml(snapshot, "generated-api");

        String agentSpecJson = mobileSpecService.generateAgentSpecJson(snapshot, versionId, openapiYaml);
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = new java.util.LinkedHashMap<String, Object>();
            root.put("spec", mapper.readValue(agentSpecJson, java.util.Map.class));

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el paquete para el agente local", ex);
        }
    }


    private DiagramDocument resolveSnapshot(UUID diagramId, UUID versionId, Principal principal) {
        if (versionId != null) {
            return versions.preview(diagramId, versionId);
        }

        List<DiagramVersionEntity> savedVersions = versions.list(diagramId);
        var currentDiagram = diagrams.get(diagramId);

        // Find saved version matching current revision if available
        for (var v : savedVersions) {
            if (v.getSourceRevision() == currentDiagram.revision()) {
                return versions.preview(diagramId, v.getId());
            }
        }

        // If no saved version exists for current revision yet, create an immutable milestone so generation is ALWAYS from an identified saved version
        String subject = AccessController.subject(principal);
        String name = AccessController.displayName(principal);
        DiagramVersionEntity autoCreated = versions.create(diagramId, "Generación r" + currentDiagram.revision(), subject, name);
        return versions.preview(diagramId, autoCreated.getId());
    }

    private String sanitizeArtifactId(String artifactId) {
        if (artifactId == null || !artifactId.matches("^[a-z][a-z0-9-]*$") || artifactId.contains("..")) {
            return "generated-api";
        }
        return artifactId;
    }
}
