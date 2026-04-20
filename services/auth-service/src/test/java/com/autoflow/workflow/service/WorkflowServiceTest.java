package com.autoflow.workflow.service;

import com.autoflow.workflow.dto.WorkflowRequest;
import com.autoflow.workflow.entity.Workflow;
import com.autoflow.workflow.entity.WorkflowExecution;
import com.autoflow.workflow.repository.WorkflowExecutionRepository;
import com.autoflow.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock WorkflowRepository workflowRepo;
    @Mock WorkflowExecutionRepository executionRepo;

    @InjectMocks WorkflowService service;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        userId      = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Workflow buildWorkflow(UUID id, UUID owner) {
        Map<String, Object> trigger = Map.of("type", "GITHUB_PUSH");
        Map<String, Object> action  = Map.of("type", "SEND_EMAIL");
        Workflow w = new Workflow(owner, "Test workflow", trigger, action);
        ReflectionTestUtils.setField(w, "id", id);
        return w;
    }

    private WorkflowRequest buildRequest(String name) {
        Map<String, Object> trigger = Map.of("type", "GITHUB_PUSH");
        Map<String, Object> action  = Map.of("type", "SEND_EMAIL");
        return new WorkflowRequest(name, trigger, action, true);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listWorkflows")
    class ListWorkflows {

        @Test
        @DisplayName("returns workflows for the authenticated user")
        void returnsOwnWorkflows() {
            Workflow w = buildWorkflow(UUID.randomUUID(), userId);
            when(workflowRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(w));

            List<Workflow> result = service.listWorkflows(userId);

            assertThat(result).hasSize(1).containsExactly(w);
        }

        @Test
        @DisplayName("returns empty list when user has no workflows")
        void returnsEmptyList() {
            when(workflowRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
            assertThat(service.listWorkflows(userId)).isEmpty();
        }
    }

    // ── Get ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getWorkflow")
    class GetWorkflow {

        @Test
        @DisplayName("returns workflow when user owns it")
        void returnsOwnWorkflow() {
            UUID id = UUID.randomUUID();
            Workflow w = buildWorkflow(id, userId);
            when(workflowRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.of(w));

            assertThat(service.getWorkflow(id, userId)).isEqualTo(w);
        }

        @Test
        @DisplayName("throws 404 when workflow belongs to a different user")
        void throws404ForOtherUser() {
            UUID id = UUID.randomUUID();
            when(workflowRepo.findByIdAndUserId(id, otherUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getWorkflow(id, otherUserId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("throws 404 when workflow does not exist")
        void throws404ForMissingWorkflow() {
            UUID id = UUID.randomUUID();
            when(workflowRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getWorkflow(id, userId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createWorkflow")
    class CreateWorkflow {

        @Test
        @DisplayName("saves and returns the created workflow")
        void createsSuccessfully() {
            WorkflowRequest req = buildRequest("My workflow");
            Workflow saved = buildWorkflow(UUID.randomUUID(), userId);
            when(workflowRepo.save(any(Workflow.class))).thenReturn(saved);

            Workflow result = service.createWorkflow(req, userId);

            assertThat(result).isEqualTo(saved);
            verify(workflowRepo).save(any(Workflow.class));
        }

        @Test
        @DisplayName("throws 400 when name is blank")
        void rejectsBlankName() {
            WorkflowRequest req = buildRequest("  ");

            assertThatThrownBy(() -> service.createWorkflow(req, userId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));

            verifyNoInteractions(workflowRepo);
        }

        @Test
        @DisplayName("throws 400 when triggerConfig is null")
        void rejectsNullTriggerConfig() {
            WorkflowRequest req = new WorkflowRequest("name", null,
                    Map.of("type", "SEND_EMAIL"), true);

            assertThatThrownBy(() -> service.createWorkflow(req, userId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateWorkflow")
    class UpdateWorkflow {

        @Test
        @DisplayName("updates only provided fields")
        void partialUpdate() {
            UUID id = UUID.randomUUID();
            Workflow existing = buildWorkflow(id, userId);
            when(workflowRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.of(existing));
            when(workflowRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkflowRequest req = new WorkflowRequest("New name", null, null, null);
            Workflow result = service.updateWorkflow(id, req, userId);

            assertThat(result.getName()).isEqualTo("New name");
            assertThat(result.getTriggerConfig()).isEqualTo(existing.getTriggerConfig());
        }

        @Test
        @DisplayName("throws 404 when updating a workflow owned by another user")
        void throws404ForOtherUser() {
            UUID id = UUID.randomUUID();
            when(workflowRepo.findByIdAndUserId(id, otherUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateWorkflow(id, buildRequest("x"), otherUserId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteWorkflow")
    class DeleteWorkflow {

        @Test
        @DisplayName("deletes when user owns the workflow")
        void deletesSuccessfully() {
            UUID id = UUID.randomUUID();
            Workflow w = buildWorkflow(id, userId);
            when(workflowRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.of(w));

            service.deleteWorkflow(id, userId);

            verify(workflowRepo).deleteByIdAndUserId(id, userId);
        }

        @Test
        @DisplayName("throws 404 before attempting delete when workflow not owned by user")
        void throws404BeforeDelete() {
            UUID id = UUID.randomUUID();
            when(workflowRepo.findByIdAndUserId(id, otherUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteWorkflow(id, otherUserId))
                    .isInstanceOf(ResponseStatusException.class);

            verify(workflowRepo, never()).deleteByIdAndUserId(any(), any());
        }
    }

    // ── Executions ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listExecutions")
    class ListExecutions {

        @Test
        @DisplayName("returns paginated executions for owned workflow")
        void returnsPaginatedResults() {
            UUID workflowId = UUID.randomUUID();
            Workflow w = buildWorkflow(workflowId, userId);
            when(workflowRepo.findByIdAndUserId(workflowId, userId)).thenReturn(Optional.of(w));

            WorkflowExecution exec = new WorkflowExecution(w, userId, UUID.randomUUID().toString(),
                    Map.of("event", "push"));
            exec.markRunning();
            exec.markSuccess(Map.of("result", "ok"));

            var page = new PageImpl<>(List.of(exec), PageRequest.of(0, 20), 1);
            when(executionRepo.findByWorkflowIdAndUserIdOrderByCreatedAtDesc(
                    eq(workflowId), eq(userId), any())).thenReturn(page);

            var result = service.listExecutions(workflowId, userId, 0, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
