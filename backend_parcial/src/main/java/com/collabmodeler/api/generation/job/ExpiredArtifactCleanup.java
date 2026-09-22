package com.collabmodeler.api.generation.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
class ExpiredArtifactCleanup {
    private final GenerationJobRepository jobs; private final ArtifactStorage storage;
    ExpiredArtifactCleanup(GenerationJobRepository jobs, ArtifactStorage storage) { this.jobs = jobs; this.storage = storage; }
    @Scheduled(fixedDelayString = "${app.generation.cleanup-ms:3600000}")
    public void deleteExpired() {
        for (GenerationJobEntity job : jobs.findByExpiresAtBefore(Instant.now())) {
            if (job.getBackendObjectKey() != null) storage.delete(job.getBackendObjectKey());
            if (job.getMobileSpecObjectKey() != null) storage.delete(job.getMobileSpecObjectKey());
        }
    }
}
