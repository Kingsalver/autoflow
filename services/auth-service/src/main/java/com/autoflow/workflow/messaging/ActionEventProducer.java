package com.autoflow.workflow.messaging;

import com.autoflow.workflow.messaging.event.ActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ActionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ActionEventProducer.class);

    /** Maximum delivery attempts before an event is dropped instead of re-queued. */
    static final int MAX_ATTEMPTS = 5;

    private final KafkaTemplate<String, ActionEvent> kafkaTemplate;
    private final String actionEventsTopic;
    private final String actionRetriesTopic;

    public ActionEventProducer(
            KafkaTemplate<String, ActionEvent> kafkaTemplate,
            @Value("${autoflow.kafka.topics.action-events:workflow.action.events}")
            String actionEventsTopic,
            @Value("${autoflow.kafka.topics.action-retries:workflow.action.retries}")
            String actionRetriesTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.actionEventsTopic = actionEventsTopic;
        this.actionRetriesTopic = actionRetriesTopic;
    }

    /**
     * Publishes an action event to {@code workflow.action.events}.
     * The Kafka partition key is the user ID to preserve per-user ordering.
     */
    public CompletableFuture<SendResult<String, ActionEvent>> publishAction(ActionEvent event) {
        log.debug(
            "Publishing action event [correlationId={}, actionType={}, workflowId={}]",
            event.correlationId(), event.actionType(), event.workflowId()
        );

        return kafkaTemplate
            .send(actionEventsTopic, event.userId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error(
                        "Failed to publish action event [correlationId={}, topic={}]",
                        event.correlationId(), actionEventsTopic, ex
                    );
                } else {
                    log.info(
                        "Action event published [correlationId={}, partition={}, offset={}]",
                        event.correlationId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                    );
                }
            });
    }

    /**
     * Publishes a failed action to {@code workflow.action.retries} with an
     * incremented attempt counter for exponential backoff processing.
     *
     * If the event has already reached {@link #MAX_ATTEMPTS}, it is dropped
     * and a warning is logged rather than re-enqueued indefinitely.
     */
    public CompletableFuture<SendResult<String, ActionEvent>> publishRetry(ActionEvent originalEvent) {
        if (originalEvent.attempt() >= MAX_ATTEMPTS - 1) {
            log.warn(
                "Max retry attempts ({}) reached — dropping event to avoid infinite retry " +
                "[correlationId={}, actionType={}]",
                MAX_ATTEMPTS, originalEvent.correlationId(), originalEvent.actionType()
            );
            return CompletableFuture.completedFuture(null);
        }

        ActionEvent retryEvent = originalEvent.withIncrementedAttempt();

        log.warn(
            "Scheduling action retry [correlationId={}, attempt={}, actionType={}]",
            retryEvent.correlationId(), retryEvent.attempt(), retryEvent.actionType()
        );

        return kafkaTemplate
            .send(actionRetriesTopic, retryEvent.userId().toString(), retryEvent)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error(
                        "Failed to publish retry event [correlationId={}, attempt={}]",
                        retryEvent.correlationId(), retryEvent.attempt(), ex
                    );
                } else {
                    log.info(
                        "Retry event published [correlationId={}, attempt={}, partition={}, offset={}]",
                        retryEvent.correlationId(), retryEvent.attempt(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                    );
                }
            });
    }
}
