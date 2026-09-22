package com.collabmodeler.api.generation.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.UUID;

@Component
@Profile("prod")
class SqsGenerationQueue {
    private final SqsClient sqs; private final String queueUrl; private final GenerationJobProcessor processor; private final boolean workerEnabled;
    SqsGenerationQueue(@Value("${app.generation.sqs-queue-url}") String queueUrl,
                       @Value("${AWS_REGION:us-east-1}") String region, @Value("${app.generation.worker-enabled:false}") boolean workerEnabled, GenerationJobProcessor processor) {
        this.queueUrl = queueUrl; this.processor = processor; this.workerEnabled = workerEnabled;
        this.sqs = SqsClient.builder().region(software.amazon.awssdk.regions.Region.of(region)).build();
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enqueue(GenerationJobQueuedEvent event) { sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(event.jobId().toString()).build()); }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${app.generation.sqs-poll-ms:3000}")
    public void poll() {
        if (!workerEnabled) return;
        for (Message message : sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).waitTimeSeconds(10).maxNumberOfMessages(5).build()).messages()) {
            try { processor.process(UUID.fromString(message.body())); sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build()); }
            catch (RuntimeException ignored) { /* visibility timeout schedules a retry */ }
        }
    }
}
