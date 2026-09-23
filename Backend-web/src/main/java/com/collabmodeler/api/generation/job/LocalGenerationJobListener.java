package com.collabmodeler.api.generation.job;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@Profile("!prod")
class LocalGenerationJobListener {
    private final GenerationJobProcessor processor;
    LocalGenerationJobListener(GenerationJobProcessor processor) { this.processor = processor; }
    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void queued(GenerationJobQueuedEvent event) { processor.process(event.jobId()); }
}
