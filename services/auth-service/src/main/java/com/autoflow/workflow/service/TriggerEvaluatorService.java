package com.autoflow.workflow.service;

import com.autoflow.workflow.entity.Workflow;
import com.autoflow.workflow.entity.WorkflowExecution;
import com.autoflow.workflow.messaging.ActionEventProducer;
import com.autoflow.workflow.messaging.event.ActionEvent;
import com.autoflow.workflow.messaging.event.TriggerEvent;
import com.autoflow.workflow.repository.WorkflowExecutionRepository;
import com.autoflow.workflow.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TriggerEvaluatorService {

    private static final Logger log = LoggerFactory.getLogger(TriggerEvaluatorService.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final ActionEventProducer actionEventProducer;
    private final WorkflowMatcherService matcherService;

    public TriggerEvaluatorService(
        WorkflowRepository workflowRepository,
        WorkflowExecutionRepository executionRepository,
        ActionEventProducer actionEventProducer,
        WorkflowMatcherService matcherService
    ) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.actionEventProducer = actionEventProducer;
        this.matcherService = matcherService;
    }

    /**
     * Core evaluation loop.
     *
     * <ol>
     *   <li>Load all enabled workflows for the user that match the event's trigger type.</li>
     *   <li>For each, run fine-grained condition matching (field-level checks).</li>
     *   <li>For each match, persist a {@link WorkflowExecution} row and publish an
     *       {@link ActionEvent} to {@code workflow.action.events}.</li>
     * </ol>
     *
     * The whole method runs in a single transaction so that the execution row and the
     * Kafka publish either both succeed or both roll back.  (Kafka publish happens
     * inside the transaction via a {@code KafkaTransactionManager} — configure one if
     * you need exactly-once semantics; at-least-once is the default here.)
     *
     * @return the number of workflows that matched and were dispatched.
     */
    @Transactional
    public int evaluate(TriggerEvent event) {
        String triggerType = event.eventType();

        List<Workflow> candidates = workflowRepository
            .findEnabledByUserAndTriggerType(event.userId(), triggerType);

        log.debug(
            "Found {} candidate workflow(s) for [userId={}, triggerType={}]",
            candidates.size(), event.userId(), triggerType
        );

        int dispatched = 0;

        for (Workflow workflow : candidates) {
            if (!matcherService.matches(workflow, event)) {
                log.debug(
                    "Workflow did not match conditions [workflowId={}, eventId={}]",
                    workflow.getId(), event.eventId()
                );
                continue;
            }

            WorkflowExecution execution = createAndPersistExecution(workflow, event);
            publishActionEvent(workflow, execution, event);
            dispatched++;
        }

        return dispatched;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private WorkflowExecution createAndPersistExecution(Workflow workflow, TriggerEvent event) {
        var execution = new WorkflowExecution(
            workflow,
            event.userId(),
            event.eventId(),   // correlationId == the originating trigger event ID
            event.payload()
        );
        execution.markRunning();

        WorkflowExecution saved = executionRepository.save(execution);

        log.info(
            "WorkflowExecution created [executionId={}, workflowId={}, correlationId={}]",
            saved.getId(), workflow.getId(), saved.getCorrelationId()
        );

        return saved;
    }

    private void publishActionEvent(Workflow workflow, WorkflowExecution execution, TriggerEvent event) {
        /*
         * Merge the static action config from the workflow definition with the
         * live payload from the trigger event so the action worker has full context.
         */
        Map<String, Object> mergedConfig = new HashMap<>(workflow.getActionConfig());
        mergedConfig.put("triggerPayload", event.payload());
        mergedConfig.put("triggerSource", event.source());

        ActionEvent actionEvent = ActionEvent.of(
            execution.getCorrelationId(),
            workflow.getId(),
            execution.getId(),
            event.userId(),
            (String) workflow.getActionConfig().get("type"),
            mergedConfig
        );

        // Block on the future so a Kafka publish failure throws CompletionException,
        // propagates out of evaluate(), prevents ack.acknowledge() in the consumer,
        // and lets Kafka redeliver the trigger event.
        actionEventProducer.publishAction(actionEvent)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error(
                        "Action event publish failed [executionId={}, correlationId={}]",
                        execution.getId(), execution.getCorrelationId(), ex
                    );
                } else {
                    log.info(
                        "Action event published [executionId={}, correlationId={}]",
                        execution.getId(), execution.getCorrelationId()
                    );
                }
            })
            .join();
    }
}
