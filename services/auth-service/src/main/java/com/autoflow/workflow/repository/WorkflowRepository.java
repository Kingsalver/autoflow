package com.autoflow.workflow.repository;

import com.autoflow.workflow.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    /** All workflows belonging to a user — used for list endpoint and UI. */
    List<Workflow> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Single workflow scoped to a user — prevents cross-tenant reads. */
    Optional<Workflow> findByIdAndUserId(UUID id, UUID userId);

    /**
     * All enabled workflows for a user — called by the trigger evaluator on
     * every inbound TriggerEvent to find candidate workflows.
     */
    List<Workflow> findByUserIdAndEnabledTrue(UUID userId);

    /**
     * Enabled workflows filtered by trigger type — avoids loading every workflow
     * when the source already knows what kind of event it fired.
     *
     * Uses a JSONB containment operator: triggerConfig @> '{"type": <value>}'
     */
    @Query(value = """
        SELECT * FROM workflows
        WHERE user_id = :userId
        AND enabled = true
        AND trigger_config @> jsonb_build_object('type', :triggerType)
        """, nativeQuery = true)
    List<Workflow> findEnabledByUserAndTriggerType(
        @Param("userId") UUID userId,
        @Param("triggerType") String triggerType
    );

    /** Hard-delete scoped to owner — prevents cross-tenant deletes. */
    void deleteByIdAndUserId(UUID id, UUID userId);

    /** Soft-delete-style: flip enabled flag without touching the record. */
    @Query("UPDATE Workflow w SET w.enabled = false WHERE w.id = :id AND w.userId = :userId")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int disableWorkflow(@Param("id") UUID id, @Param("userId") UUID userId);
}