package com.collabmodeler.api.generation.job;

import com.collabmodeler.api.support.NotFoundException;
import com.collabmodeler.api.version.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class GenerationJobService {
    private final GenerationJobRepository jobs; private final DiagramVersionService versions;
    private final ApplicationEventPublisher events; private final ArtifactLinkService links;
    public GenerationJobService(GenerationJobRepository jobs, DiagramVersionService versions, ApplicationEventPublisher events, ArtifactLinkService links) {
        this.jobs = jobs; this.versions = versions; this.events = events; this.links = links;
    }
    @Transactional
    public GenerationJobEntity create(UUID diagramId, UUID requestedVersionId, String groupId, String artifactId,
                                      String subject, String name, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        versions.lockDiagram(diagramId);
        var existing = jobs.findByDiagramIdAndRequesterSubjectAndIdempotencyKey(diagramId, subject, idempotencyKey);
        if (existing.isPresent()) return existing.get();
        DiagramVersionEntity version = requestedVersionId == null
            ? versions.findOrCreateForCurrentRevision(diagramId, subject, name)
            : versions.require(diagramId, requestedVersionId);
        String safeGroup = groupId == null || groupId.isBlank() ? "com.generated" : groupId.trim();
        String safeArtifact = artifactId == null || artifactId.isBlank() ? "generated-api" : artifactId.trim();
        GenerationJobEntity job = jobs.save(new GenerationJobEntity(diagramId, version.getId(), version.getSourceRevision(),
            subject, name, idempotencyKey, safeGroup, safeArtifact));
        events.publishEvent(new GenerationJobQueuedEvent(job.getId()));
        return job;
    }
    public List<GenerationJobEntity> list(UUID diagramId) { return jobs.findByDiagramIdOrderByQueuedAtDesc(diagramId); }
    public GenerationJobEntity get(UUID diagramId, UUID jobId) { return jobs.findByIdAndDiagramId(jobId, diagramId).orElseThrow(() -> new NotFoundException("Trabajo de generación no encontrado")); }
    @Transactional
    public GenerationJobEntity retry(UUID diagramId, UUID jobId) {
        GenerationJobEntity job = jobs.findForUpdate(jobId).filter(value -> value.getDiagramId().equals(diagramId))
            .orElseThrow(() -> new NotFoundException("Trabajo de generación no encontrado"));
        if (job.getStatus() == GenerationJobStatus.FAILED) { job.requeue(); events.publishEvent(new GenerationJobQueuedEvent(job.getId())); }
        return job;
    }
    public JobResponse response(GenerationJobEntity job) {
        ArtifactLinkService.SignedLink backend = null, spec = null;
        if (job.getStatus() == GenerationJobStatus.SUCCEEDED && job.getExpiresAt() != null && job.getExpiresAt().isAfter(Instant.now())) {
            backend = links.create(job.getDiagramId(), job.getId(), "backend"); spec = links.create(job.getDiagramId(), job.getId(), "mobile-spec");
        }
        return new JobResponse(job.getId(), job.getDiagramId(), job.getVersionId(), job.getSourceRevision(), job.getRequesterName(),
            job.getStatus(), job.getAttempt(), job.getQueuedAt(), job.getStartedAt(), job.getCompletedAt(), job.getExpiresAt(),
            job.getErrorCode(), job.getErrorMessage(), backend, spec);
    }
    private void validateIdempotencyKey(String value) {
        if (value == null || !value.matches("^[A-Za-z0-9._:-]{8,120}$")) throw new IllegalArgumentException("Idempotency-Key debe tener entre 8 y 120 caracteres seguros");
    }
    public record JobResponse(UUID id, UUID diagramId, UUID versionId, long sourceRevision, String requesterName,
                              GenerationJobStatus status, int attempt, Instant queuedAt, Instant startedAt, Instant completedAt,
                              Instant expiresAt, String errorCode, String errorMessage,
                              ArtifactLinkService.SignedLink backend, ArtifactLinkService.SignedLink mobileSpec) {}
}
