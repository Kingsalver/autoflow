package com.autoflow.workflow.dto;

import com.autoflow.workflow.entity.Workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * API response DTO for Workflow. Decouples the HTTP contract from the JPA entity
 * so schema changes don't accidentally alter the API surface.
 *
 * userId is intentionally omitted — it is always the authenticated user's own ID.
 */
public record WorkflowResponse(
    UUID id,
    String name,
    String description,
    Map<String, Object> triggerConfig,
    Map<String, Object> actionConfig,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
    public static WorkflowResponse from(Workflow w) {
        return new WorkflowResponse(
            w.getId(),
            w.getName(),
            w.getDescription(),
            w.getTriggerConfig(),
            w.getActionConfig(),
            w.isEnabled(),
            w.getCreatedAt(),
            w.getUpdatedAt()
        );
    }
}
