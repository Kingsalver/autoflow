package com.autoflow.workflow.controller;

import com.autoflow.workflow.dto.WorkflowRequest;
import com.autoflow.workflow.entity.Workflow;
import com.autoflow.workflow.entity.WorkflowExecution;
import com.autoflow.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    // GET /workflows
    @GetMapping
    public ResponseEntity<List<Workflow>> list(Authentication auth) {
        return ResponseEntity.ok(workflowService.listWorkflows(userId(auth)));
    }

    // POST /workflows
    @PostMapping
    public ResponseEntity<Workflow> create(@RequestBody WorkflowRequest req, Authentication auth) {
        Workflow created = workflowService.createWorkflow(req, userId(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // GET /workflows/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Workflow> get(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(workflowService.getWorkflow(id, userId(auth)));
    }

    // PUT /workflows/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Workflow> update(@PathVariable UUID id,
                                    @RequestBody WorkflowRequest req,
                                    Authentication auth) {
        return ResponseEntity.ok(workflowService.updateWorkflow(id, req, userId(auth)));
    }

    // DELETE /workflows/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        workflowService.deleteWorkflow(id, userId(auth));
        return ResponseEntity.noContent().build();
    }

    // GET /workflows/{id}/executions?page=0&size=20
    @GetMapping("/{id}/executions")
    public ResponseEntity<Page<WorkflowExecution>> executions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return ResponseEntity.ok(workflowService.listExecutions(id, userId(auth), page, size));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID userId(Authentication auth) {
        // Principal is the userId string set by JwtAuthFilter
        return UUID.fromString((String) auth.getPrincipal());
    }
}