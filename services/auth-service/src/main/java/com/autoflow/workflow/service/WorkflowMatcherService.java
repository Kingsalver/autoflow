package com.autoflow.workflow.service;

import com.autoflow.workflow.entity.Workflow;
import com.autoflow.workflow.messaging.event.TriggerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
public class WorkflowMatcherService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowMatcherService.class);

    /**
     * Returns true if all conditions in the workflow's triggerConfig are satisfied
     * by the inbound event payload.
     *
     * <p>Matching rules:
     * <ul>
     *   <li>{@code type} — always present; already filtered at the DB query level,
     *       but re-checked here as a safety net.</li>
     *   <li>Any other key in {@code triggerConfig} is treated as a required field
     *       match against the event payload.  Missing or non-equal values fail the
     *       check.</li>
     * </ul>
     *
     * <p>Example triggerConfig that would match a push to main on a specific repo:
     * <pre>{@code
     * {
     *   "type": "GITHUB_PUSH",
     *   "repo": "org/autoflow",
     *   "branch": "main"
     * }
     * }</pre>
     */
    public boolean matches(Workflow workflow, TriggerEvent event) {
        Map<String, Object> triggerConfig = workflow.getTriggerConfig();
        Map<String, Object> payload = event.payload();

        if (payload == null) return false;

        for (Map.Entry<String, Object> condition : triggerConfig.entrySet()) {
            String key = condition.getKey();

            // "type" was the DB-level filter — skip re-checking it here
            if ("type".equals(key)) continue;

            Object expected = condition.getValue();
            Object actual   = payload.get(key);

            if (!Objects.equals(expected, actual)) {
                log.debug(
                    "Condition mismatch [workflowId={}, key={}, expected={}, actual={}]",
                    workflow.getId(), key, expected, actual
                );
                return false;
            }
        }

        return true;
    }
}