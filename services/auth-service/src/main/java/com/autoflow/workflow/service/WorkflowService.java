package com.autoflow.workflow.service;

import com.autoflow.workflow.dto.WorkflowRequest;
import com.autoflow.workflow.entity.Workflow;
import com.autoflow.workflow.entity.WorkflowExecution;
import com.autoflow.workflow.repository.WorkflowExecutionRepository;
import com.autoflow.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepo;
    private final WorkflowExecutionRepository executionRepo;

    // ── Workflow CRUD ─────────────────────────────────────────────────────────

    public List<Workflow> listWorkflows(UUID userId) {
        return workflowRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Workflow getWorkflow(UUID id, UUID userId) {
        return workflowRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
    }

    @Transactional
    public Workflow createWorkflow(WorkflowRequest req, UUID userId) {
        validate(req);
        Workflow workflow = new Workflow(userId, req.name(), req.triggerConfig(), req.actionConfig());
        if (req.active() != null) workflow.setEnabled(req.active());
        return workflowRepo.save(workflow);
    }

    @Transactional
    public Workflow updateWorkflow(UUID id, WorkflowRequest req, UUID userId) {
        Workflow workflow = getWorkflow(id, userId);

        if (req.name()          != null) workflow.setName(req.name());
        if (req.triggerConfig() != null) workflow.setTriggerConfig(req.triggerConfig());
        if (req.actionConfig()  != null) workflow.setActionConfig(req.actionConfig());
        if (req.active()        != null) workflow.setEnabled(req.active());

        return workflowRepo.save(workflow);
    }

    @Transactional
    public void deleteWorkflow(UUID id, UUID userId) {
        // Verify ownership before deleting
        getWorkflow(id, userId);
        workflowRepo.deleteByIdAndUserId(id, userId);
        log.info("Deleted workflow {} for user {}", id, userId);
    }

    // ── Execution History ─────────────────────────────────────────────────────

    public Page<WorkflowExecution> listExecutions(UUID workflowId, UUID userId,
                                                   int page, int size) {
        // Verify the workflow belongs to this user before exposing its executions
        getWorkflow(workflowId, userId);

        Pageable pageable = PageRequest.of(page, Math.max(1, Math.min(size, 100)));
        return executionRepo.findByWorkflowIdAndUserIdOrderByCreatedAtDesc(
                workflowId, userId, pageable);
    }

    // ── Internal (called by Kafka consumer) ───────────────────────────────────

    /**
     * Returns all active workflows for a given user.
     * Used by the trigger evaluator to find candidate workflows for an event.
     */
    public List<Workflow> getActiveWorkflowsForUser(UUID userId) {
        return workflowRepo.findByUserIdAndEnabledTrue(userId);
    }

    @Transactional
    public WorkflowExecution saveExecution(WorkflowExecution execution) {
        return executionRepo.save(execution);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validate(WorkflowRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow name is required");
        }
        if (req.triggerConfig() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "triggerConfig is required");
        }
        if (req.actionConfig() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actionConfig is required");
        }
    }
}