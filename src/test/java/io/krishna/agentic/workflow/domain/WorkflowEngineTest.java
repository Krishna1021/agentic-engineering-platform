package io.krishna.agentic.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowEngineTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final WorkflowEngine engine = new WorkflowEngine(2, 1);

    @Test
    void rejectsCyclesMissingDependenciesAndDuplicateIds() {
        assertThatThrownBy(() -> DependencyGraph.validate(List.of(
                Task.pending("a", TaskKind.DESIGN, "b"), Task.pending("b", TaskKind.TEST, "a"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cycle");
        assertThatThrownBy(() -> DependencyGraph.validate(List.of(Task.pending("a", TaskKind.TEST, "missing"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing");
        assertThatThrownBy(() -> DependencyGraph.validate(List.of(Task.pending("a", TaskKind.TEST), Task.pending("a", TaskKind.TEST))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void synchronizesIndependentBranches() {
        Task implementation = Task.pending("implementation", TaskKind.IMPLEMENT).start().succeed(TaskOutput.note("done"));
        Task tests = Task.pending("tests", TaskKind.TEST, "implementation");
        Task docs = Task.pending("docs", TaskKind.DOCUMENT, "implementation");
        Task apply = Task.pending("apply", TaskKind.APPLY, "tests", "docs");
        assertThat(DependencyGraph.ready(List.of(implementation, tests, docs, apply)))
                .extracting(Task::id).containsExactly("tests", "docs");
        assertThat(DependencyGraph.ready(List.of(implementation, tests.start().succeed(TaskOutput.note("done")), docs, apply)))
                .extracting(Task::id).containsExactly("docs");
    }

    @Test
    void clarificationPreventsDownstreamTasks() {
        Workflow workflow = running(Task.pending("analyze", TaskKind.ANALYZE).start());
        Workflow result = engine.complete(workflow, "analyze", new TaskOutput("Ambiguous", Map.of(), List.of("Which API?"), true), now);
        assertThat(result.status()).isEqualTo(WorkflowStatus.AWAITING_CLARIFICATION);
        assertThat(result.tasks()).hasSize(1);
    }

    @Test
    void clearRequirementsExpandTheGraph() {
        Workflow result = engine.complete(running(Task.pending("analyze", TaskKind.ANALYZE).start()),
                "analyze", TaskOutput.note("clear"), now);
        assertThat(result.tasks()).extracting(Task::id)
                .containsExactly("analyze", "inspect", "design", "implement", "test", "document", "apply", "validate");
        assertThat(DependencyGraph.ready(result.tasks())).extracting(Task::id).containsExactly("inspect");
    }

    @Test
    void validationFailureAddsRepairAndRevalidation() {
        Workflow result = engine.complete(running(Task.pending("validate", TaskKind.VALIDATE).start()),
                "validate", new TaskOutput("compiler failure", Map.of(), List.of(), false), now);
        assertThat(result.status()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(result.tasks()).extracting(Task::id).contains("repair-1", "apply-1", "validate-1");
        assertThat(DependencyGraph.ready(result.tasks())).extracting(Task::id).containsExactly("repair-1");
    }

    @Test
    void exhaustedValidationBlocksApproval() {
        Workflow result = new WorkflowEngine(2, 0).complete(running(Task.pending("validate", TaskKind.VALIDATE).start()),
                "validate", new TaskOutput("failed", Map.of(), List.of(), false), now);
        assertThat(result.status()).isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void retriesPureTasksButSafeStopsUncertainEffects() {
        Workflow first = engine.fail(running(Task.pending("design", TaskKind.DESIGN).start()), "design", "Unavailable", now);
        assertThat(first.task("design").status()).isEqualTo(TaskStatus.PENDING);
        Workflow second = engine.fail(running(first.task("design").start()), "design", "Unavailable", now);
        assertThat(second.status()).isEqualTo(WorkflowStatus.FAILED);
        Workflow effect = engine.fail(running(Task.pending("apply", TaskKind.APPLY).start()), "apply", "IOException", now);
        assertThat(effect.status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
    }

    @Test
    void rejectsCompletionForInactiveTasks() {
        assertThatThrownBy(() -> engine.complete(running(Task.pending("design", TaskKind.DESIGN)),
                "design", TaskOutput.note("done"), now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsBlankRequirementsAndInvalidPolicies() {
        assertThatThrownBy(() -> Workflow.create(" ", null, "operator", now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowEngine(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    private Workflow running(Task task) {
        return Workflow.create("Build an application with documented behavior", null, "operator", now)
                .transition(WorkflowStatus.RUNNING, List.of(task), now);
    }
}
