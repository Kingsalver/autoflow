package com.autoflow.workflow.repository;

import com.autoflow.workflow.entity.WorkflowExecution;
import com.autoflow.workflow.entity.WorkflowExecution.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, UUID> {

    /**
     * Paginated execution history for a single workflow — backs the
     * {@code GET /workflows/{id}/executions} endpoint.
     */
    Page<WorkflowExecution> findByWorkflowIdAndUserIdOrderByCreatedAtDesc(
        UUID workflowId, UUID userId, Pageable pageable
    );

    /** Look up a single execution scoped to a user (prevents cross-tenant reads). */
    Optional<WorkflowExecution> findByIdAndUserId(UUID id, UUID userId);

    /** Look up by correlation ID — used during action result write-back. */
    Optional<WorkflowExecution> findByCorrelationId(String correlationId);

    /**
     * All executions currently in RETRYING state — polled by the retry scheduler
     * to re-enqueue stale retries that were never redelivered.
     */
    List<WorkflowExecution> findByStatusAndUpdatedAtBefore(Status status, Instant cutoff);

    /**
     * Count executions per status for a workflow — used by the dashboard stats widget.
     */
    @Query("""
        SELECT e.status, COUNT(e)
        FROM WorkflowExecution e
        WHERE e.workflow.id = :workflowId AND e.userId = :userId
        GROUP BY e.status
        """)
    List<Object[]> countByStatusForWorkflow(
        @Param("workflowId") UUID workflowId,
        @Param("userId") UUID userId
    );
}