package com.autoflow.workflow.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workflow_executions")
public class WorkflowExecution {

    public enum Status {
        PENDING, RUNNING, SUCCESS, FAILED, RETRYING
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Stable ID shared across all steps of a single end-to-end trigger → action
     * chain, used for distributed tracing and log correlation.
     */
    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    /**
     * The raw event payload that triggered this execution.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_payload", columnDefinition = "jsonb")
    private Map<String, Object> triggerPayload;

    /**
     * The outcome written back by the action worker on success.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** Number of action delivery attempts so far (starts at 0). */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    protected WorkflowExecution() {}

    public WorkflowExecution(Workflow workflow, UUID userId, String correlationId, Map<String, Object> triggerPayload) {
        this.workflow = workflow;
        this.userId = userId;
        this.correlationId = correlationId;
        this.triggerPayload = triggerPayload;
    }

    // ── Convenience mutators ──────────────────────────────────────────────────

    public void markRunning() {
        this.status = Status.RUNNING;
        this.attemptCount++;
    }

    public void markSuccess(Map<String, Object> result) {
        this.status = Status.SUCCESS;
        this.result = result;
    }

    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage;
    }

    public void markRetrying(String errorMessage) {
        this.status = Status.RETRYING;
        this.errorMessage = errorMessage;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public Workflow getWorkflow() { return workflow; }

    public UUID getUserId() { return userId; }

    public String getCorrelationId() { return correlationId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Map<String, Object> getTriggerPayload() { return triggerPayload; }
    public void setTriggerPayload(Map<String, Object> triggerPayload) { this.triggerPayload = triggerPayload; }

    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getAttemptCount() { return attemptCount; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
