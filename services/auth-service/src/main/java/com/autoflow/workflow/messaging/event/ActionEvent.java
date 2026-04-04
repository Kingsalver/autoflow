package com.autoflow.workflow.messaging.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published to {@code workflow.action.events} (and {@code workflow.action.retries}
 * on failure) by the workflow engine after a matching workflow is found.
 */
public record ActionEvent(

    @JsonProperty("event_id")
    String eventId,

    /** Back-reference to the originating trigger for end-to-end tracing. */
    @JsonProperty("correlation_id")
    String correlationId,

    @JsonProperty("workflow_id")
    UUID workflowId,

    @JsonProperty("execution_id")
    UUID executionId,

    @JsonProperty("user_id")
    UUID userId,

    /**
     * Action type the receiving worker should execute.
     * Examples: SEND_EMAIL, CREATE_GITHUB_ISSUE, RUN_AI_PIPELINE
     */
    @JsonProperty("action_type")
    String actionType,

    /** Merged view: action config from the workflow + trigger payload context. */
    @JsonProperty("action_config")
    Map<String, Object> actionConfig,

    /** Zero-based delivery attempt number — incremented on each retry. */
    @JsonProperty("attempt")
    int attempt,

    @JsonProperty("created_at")
    Instant createdAt
) {
    public static ActionEvent of(
        String correlationId,
        UUID workflowId,
        UUID executionId,
        UUID userId,
        String actionType,
        Map<String, Object> actionConfig
    ) {
        return new ActionEvent(
            UUID.randomUUID().toString(),
            correlationId,
            workflowId,
            executionId,
            userId,
            actionType,
            actionConfig,
            0,
            Instant.now()
        );
    }

    public ActionEvent withIncrementedAttempt() {
        return new ActionEvent(
            eventId, correlationId, workflowId, executionId,
            userId, actionType, actionConfig,
            attempt + 1, createdAt
        );
    }
}