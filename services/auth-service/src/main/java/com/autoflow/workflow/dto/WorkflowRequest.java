package com.autoflow.workflow.dto;

import java.util.Map;

/**
 * Request body for workflow create and update endpoints.
 * All fields are optional on update — null means "don't change this field".
 */
public record WorkflowRequest(
    String name,
    Map<String, Object> triggerConfig,
    Map<String, Object> actionConfig,
    Boolean active
) {}
