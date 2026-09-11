package io.krishna.agentic.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.krishna.agentic.execution.EngineeringTaskHandler;
import io.krishna.agentic.execution.WorkspaceService;
import io.krishna.agentic.workflow.application.WorkflowConflictException;
import io.krishna.agentic.workflow.application.WorkflowService;
import io.krishna.agentic.workflow.domain.TaskOutput;
import io.krishna.agentic.workflow.domain.Workflow;
import io.krishna.agentic.workflow.domain.WorkflowStatus;
import io.krishna.agentic.workflow.persistence.WorkflowStore;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowIntegrationTest {
    @Autowired WorkflowService service;
    @Autowired WorkflowStore store;
    @Autowired EngineeringTaskHandler handler;
    @Autowired MockMvc mvc;
    @Autowired WorkspaceService workspaces;

    @Test
    void rejectsApprovalWhenSourceChangesAfterValidation() throws Exception {
        Workflow initial = service.submit("Build a Java application with executable tests", null, "operator");
        Workflow ready = run(initial.id());
        Files.writeString(workspaces.repository(ready).resolve("src/main/java/generated/Application.java"), "changed");
        assertThatThrownBy(() -> service.approve(ready.id(), ready.revision(), "approver", "review"))
                .isInstanceOf(WorkflowConflictException.class).hasMessageContaining("changed after validation");
        assertThat(store.get(ready.id()).status()).isEqualTo(WorkflowStatus.AWAITING_APPROVAL);
    }

    @Test
    void safeStopDiscardsInFlightOutput() {
        Workflow initial = service.submit("Build a Java application with executable tests", null, "operator");
        var claim = service.claim(initial.id(), 1).get(0);
        service.stop(initial.id(), 1, "operator", "Review required");
        service.complete(initial.id(), 1, claim.task(), TaskOutput.note("late result"));
        assertThat(store.get(initial.id()).status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(store.get(initial.id()).task("analyze").output()).isNull();
    }

    @Test
    void executesRequirementsOnlyToReviewThenApprovesExactRevision() throws Exception {
        Workflow initial = service.submit("Build a Java application with executable tests", null, "operator");
        Workflow ready = run(initial.id());
        assertThat(ready.status()).isEqualTo(WorkflowStatus.AWAITING_APPROVAL);
        assertThat(ready.task("validate").output().summary()).contains("DEMO_ONLY");
        assertThat(service.approve(ready.id(), 1, "approver", "Reviewed demo evidence").status())
                .isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(store.events(initial.id(), 0)).extracting(event -> event.type()).contains("CREATED", "TASKS_STARTED", "APPROVED");
    }

    @Test
    void pausesForClarificationAndPreservesDecisionHistory() throws Exception {
        Workflow initial = service.submit("Improve analytics", null, "operator");
        assertThat(run(initial.id()).status()).isEqualTo(WorkflowStatus.AWAITING_CLARIFICATION);
        Workflow clarified = service.clarify(initial.id(), 1, "Track daily counts and expose a read-only API", "operator");
        assertThat(clarified.revision()).isEqualTo(2);
        assertThat(run(initial.id()).status()).isEqualTo(WorkflowStatus.AWAITING_APPROVAL);
        assertThat(store.events(initial.id(), 0)).extracting(event -> event.revision()).contains(1, 2);
    }

    @Test
    void replanningInvalidatesApprovalAndDiscardsLateTaskResults() throws Exception {
        Workflow initial = service.submit("Build a Java application with executable tests", null, "operator");
        var claim = service.claim(initial.id(), 1).get(0);
        Workflow revised = service.revise(initial.id(), 1, "Build a revised application with acceptance tests", "operator");
        service.complete(initial.id(), 1, claim.task(), TaskOutput.note("stale"));
        assertThat(store.get(initial.id())).isEqualTo(revised);
        run(initial.id());
        assertThatThrownBy(() -> service.approve(initial.id(), 1, "approver", "stale"))
                .isInstanceOf(WorkflowConflictException.class);
    }

    @Test
    void restartRequiresExplicitRecoveryOfInterruptedTasks() {
        Workflow initial = service.submit("Build a Java application with executable tests", null, "operator");
        service.claim(initial.id(), 1);
        service.interruptOnRestart(initial.id());
        assertThat(store.get(initial.id()).status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(service.claim(initial.id(), 2)).isEmpty();
        Workflow recovered = service.recover(initial.id(), 1, "operator");
        assertThat(recovered.revision()).isEqualTo(2);
        assertThat(recovered.status()).isEqualTo(WorkflowStatus.QUEUED);
    }

    @Test
    void rejectsPrematureApprovalAndRollsBackRejectedMutation() {
        Workflow initial = service.submit("Build a Java application with executable tests", null, "operator");
        assertThatThrownBy(() -> service.approve(initial.id(), 1, "approver", "premature"))
                .isInstanceOf(WorkflowConflictException.class);
        assertThat(store.events(initial.id(), 0)).hasSize(1);
        assertThat(store.get(initial.id())).isEqualTo(initial);
    }

    @Test
    void enforcesAuthenticationRolesAndInputValidation() throws Exception {
        mvc.perform(get("/api/v1/workflows/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/workflows").with(httpBasic("operator", "operator-test-password"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"requirements\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/workflows").with(httpBasic("approver", "approver-test-password"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"requirements\":\"Build an application with tests\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/workflows/" + UUID.randomUUID() + "/approvals")
                .with(httpBasic("operator", "operator-test-password"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"reason\":\"reviewed\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workflows/" + UUID.randomUUID()).with(httpBasic("operator", "operator-test-password")))
                .andExpect(status().isNotFound());
    }

    private Workflow run(UUID id) throws Exception {
        for (int step = 0; step < 30; step++) {
            List<WorkflowService.TaskClaim> claimed = service.claim(id, 4);
            if (claimed.isEmpty()) {
                return store.get(id);
            }
            for (var claim : claimed) {
                service.complete(id, claim.workflow().revision(), claim.task(), handler.execute(claim.workflow(), claim.task()));
            }
        }
        throw new AssertionError("Workflow did not terminate within bounded steps");
    }
}
