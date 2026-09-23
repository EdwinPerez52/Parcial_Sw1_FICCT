package com.collabmodeler.api.generation.job;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class GenerationJobEntityTest {
    @Test void transitionsOnlyExposeArtifactsAfterSuccessAndCanBeRetried() {
        var job = new GenerationJobEntity(UUID.randomUUID(), UUID.randomUUID(), 12, "account:1", "Ana",
            "request-12345678", "com.generated", "ventas-api");
        assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.QUEUED);
        job.markRunning();
        assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.RUNNING);
        assertThat(job.getAttempt()).isEqualTo(1);
        job.markFailed("INVALID_PRIMARY_KEY", "Falta clave primaria");
        assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.FAILED);
        assertThat(job.getBackendObjectKey()).isNull();
        job.requeue();
        assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.QUEUED);
        job.markRunning();
        job.markSucceeded("random.zip.enc", "random.spec.enc", "api.zip", "spec.json", Instant.now().plusSeconds(60));
        assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.SUCCEEDED);
        assertThat(job.getBackendObjectKey()).isEqualTo("random.zip.enc");
        assertThat(job.getMobileSpecObjectKey()).isEqualTo("random.spec.enc");
    }
}
