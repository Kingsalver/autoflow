package com.autoflow.workflow.kafka;

import com.autoflow.workflow.entity.Workflow;
import com.autoflow.workflow.entity.WorkflowExecution;
import com.autoflow.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import com.autoflow.workflow.messaging.ActionEventProducer;
import com.autoflow.workflow.messaging.event.TriggerEvent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Consumes events from `workflow.trigger.events`.
 *
 * Current behaviour (Phase 1):
 *   - Receives events and logs them
 *   - Deserialises the userId and loads the user's active workflows
 *   - Calls evaluateMatch() — STUBBED, always returns false
 *   - Records a PENDING execution for any matched workflow
 *   - Publishes an ActionEvent for matched workflows
 *
 * Phase 5 work:
 *   Replace the stub in evaluateMatch() with real trigger config evaluation.
 *   The method signature and surrounding plumbing are already in place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerEventConsumer {

    private final WorkflowService workflowService;
    private final ActionEventProducer actionEventProducer;

    @KafkaListener(
            topics    = "#{@appProperties.kafka.topics.triggerEvents}",
            groupId   = "workflow-engine",
            // Manual ack gives us control — don't commit the offset until we've
            // finished processing. If we crash mid-execution we'll reprocess,
            // which is safe because WorkflowExecution has a correlationId.
            containerFactory = "manualAckListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, TriggerEvent> record, Acknowledgment ack) {
        TriggerEvent event = record.value();

        log.info("Received trigger event [type={}, userId={}, source={}]",
                event.getEventType(), event.getUserId(), event.getSource());

        try {
            processEvent(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process trigger event [type={}, userId={}]: {}",
                    event.getEventType(), event.getUserId(), e.getMessage(), e);
            // Do NOT ack — let Kafka retry on the next poll.
            // Dead-letter handling will be added in Phase 5 alongside retry logic.
        }
    }

    private void processEvent(TriggerEvent event) {
        UUID userId;
        try {
            userId = UUID.fromString(event.getUserId());
        } catch (IllegalArgumentException e) {
            log.warn("Dropping trigger event with invalid userId: {}", event.getUserId());
            return;
        }

        List<Workflow> candidates = workflowService.getActiveWorkflowsForUser(userId);

        if (candidates.isEmpty()) {
            log.debug("No active workflows for user {} — skipping", userId);
            return;
        }

        for (Workflow workflow : candidates) {
            if (!evaluateMatch(workflow, event)) {
                continue;
            }

            String correlationId = UUID.randomUUID().toString();
            log.info("Workflow {} matched event {} [correlationId={}]",
                    workflow.getId(), event.getEventType(), correlationId);

            // Persist execution record before publishing the action event.
            // If the publish fails we still have a record of the match attempt.
            WorkflowExecution execution = workflowService.saveExecution(
                    WorkflowExecution.builder()
                            .workflowId(workflow.getId())
                            .userId(userId)
                            .status(WorkflowExecution.Status.PENDING)
                            .triggerPayload(event.getPayload())
                            .correlationId(correlationId)
                            .startedAt(OffsetDateTime.now())
                            .build()
            );

            actionEventProducer.publish(
                    ActionEvent.builder()
                            .correlationId(correlationId)
                            .workflowId(workflow.getId().toString())
                            .userId(event.getUserId())
                            .actionType(extractActionType(workflow))
                            .actionConfig(workflow.getActionConfig())
                            .triggerPayload(event.getPayload())
                            .createdAt(OffsetDateTime.now())
                            .build()
            );
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     * PHASE 5 STUB — trigger evaluation logic lives here.
     * ═══════════════════════════════════════════════════════════════════════
     *
     * When this returns true the engine treats the workflow as matched and
     * publishes an ActionEvent. Currently always returns false so no
     * executions fire during development.
     *
     * To implement in Phase 5:
     *   1. Read workflow.getTriggerConfig() — a JsonNode with at minimum a
     *      "type" field (e.g. "github.push").
     *   2. Check event.getEventType() matches the trigger type.
     *   3. Apply any filter predicates in the trigger config
     *      (e.g. branch filter, repo filter, label filter).
     *
     * Example trigger config that Phase 5 should handle:
     *   { "type": "github.push", "repo": "org/repo", "branch": "main" }
     *
     * Matching rule: eventType == triggerConfig.type AND all filter keys
     * in triggerConfig match the corresponding fields in event.payload.
     */
    private boolean evaluateMatch(Workflow workflow, TriggerEvent event) {
        // TODO (Phase 5): implement trigger config evaluation
        log.debug("evaluateMatch — stubbed, returning false [workflowId={}, eventType={}]",
                workflow.getId(), event.getEventType());
        return false;
    }

    /**
     * Pulls the "type" field from actionConfig.
     * Defaults to "unknown" if not present — the consuming worker will reject it gracefully.
     */
    private String extractActionType(Workflow workflow) {
        var typeNode = workflow.getActionConfig().get("type");
        return typeNode != null ? typeNode.asText() : "unknown";
    }
}