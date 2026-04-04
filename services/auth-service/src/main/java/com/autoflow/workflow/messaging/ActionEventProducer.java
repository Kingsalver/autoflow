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

    private final KafkaTemplate<String, ActionEvent> kafkaTemplate;

    @Value("${autoflow.kafka.topics.action-events:workflow.action.events}")
    private String actionEventsTopic;

    @Value("${autoflow.kafka.topics.action-retries:workflow.action.retries}")
    private String actionRetriesTopic;

    public ActionEventProducer(KafkaTemplate<String, ActionEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
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
     */
    public CompletableFuture<SendResult<String, ActionEvent>> publishRetry(ActionEvent originalEvent) {
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