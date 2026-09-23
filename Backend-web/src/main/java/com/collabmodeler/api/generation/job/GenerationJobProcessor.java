package com.collabmodeler.api.generation.job;

import com.collabmodeler.api.generation.*;
import com.collabmodeler.api.version.DiagramVersionService;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

@Service
public class GenerationJobProcessor {
    private static final Logger log = LoggerFactory.getLogger(GenerationJobProcessor.class);
    private final GenerationJobRepository jobs; private final DiagramVersionService versions;
    private final BackendGenerator backendGenerator; private final OpenApiGenerator openApiGenerator;
    private final MobileSpecService mobileSpecService; private final ArtifactStorage storage;
    private final Duration retention;

    public GenerationJobProcessor(GenerationJobRepository jobs, DiagramVersionService versions,
                                  BackendGenerator backendGenerator, OpenApiGenerator openApiGenerator,
                                  MobileSpecService mobileSpecService, ArtifactStorage storage,
                                  @Value("${app.generation.retention-hours:168}") long retentionHours) {
        this.jobs = jobs; this.versions = versions; this.backendGenerator = backendGenerator;
        this.openApiGenerator = openApiGenerator; this.mobileSpecService = mobileSpecService; this.storage = storage;
        this.retention = Duration.ofHours(Math.max(1, retentionHours));
    }

    @Transactional
    public void process(UUID jobId) {
        GenerationJobEntity claimed = claim(jobId); if (claimed == null) return;
        String backendKey = null; String specKey = null;
        try {
            var diagram = versions.preview(claimed.getDiagramId(), claimed.getVersionId());
            String openapi = openApiGenerator.generateYaml(diagram, claimed.getArtifactId());
            byte[] backend = backendGenerator.generate(diagram, claimed.getGroupId(), claimed.getArtifactId());
            byte[] spec = mobileSpecService.generateSpecJson(diagram, claimed.getVersionId(), openapi).getBytes(StandardCharsets.UTF_8);
            String prefix = claimed.getDiagramId() + "/" + claimed.getId() + "/" + UUID.randomUUID();
            backendKey = prefix + ".zip.enc"; specKey = prefix + ".mobile-spec.json.enc";
            storage.put(backendKey, backend, "application/zip"); storage.put(specKey, spec, "application/json");
            succeed(jobId, backendKey, specKey, claimed.getArtifactId() + "-r" + claimed.getSourceRevision() + ".zip",
                "modeler-mobile-spec-r" + claimed.getSourceRevision() + ".json", Instant.now().plus(retention));
        } catch (Exception e) {
            if (backendKey != null) storage.delete(backendKey); if (specKey != null) storage.delete(specKey);
            String code = e instanceof ModelValidationException validation ? validation.getCode() : "GENERATION_FAILED";
            String message = e instanceof ModelValidationException ? safe(e.getMessage()) : "No se pudieron generar los artefactos";
            fail(jobId, code, message); log.error("Generation job {} failed", jobId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GenerationJobEntity claim(UUID id) {
        var job = jobs.findForUpdate(id).orElse(null);
        if (job == null || job.getStatus() != GenerationJobStatus.QUEUED) return null;
        job.markRunning(); return jobs.save(job);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(UUID id, String backendKey, String specKey, String backendName, String specName, Instant expires) {
        var job = jobs.findForUpdate(id).orElseThrow(); job.markSucceeded(backendKey, specKey, backendName, specName, expires);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id, String code, String message) { jobs.findForUpdate(id).ifPresent(job -> job.markFailed(code, message)); }
    private String safe(String value) { return value == null ? "El modelo no es válido" : value.substring(0, Math.min(500, value.length())); }
}
