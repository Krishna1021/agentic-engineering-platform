package io.krishna.agentic.workflow.application;

import io.krishna.agentic.execution.WorkspaceService;
import io.krishna.agentic.workflow.domain.DependencyGraph;
import io.krishna.agentic.workflow.domain.Task;
import io.krishna.agentic.workflow.domain.TaskOutput;
import io.krishna.agentic.workflow.domain.TaskStatus;
import io.krishna.agentic.workflow.domain.Workflow;
import io.krishna.agentic.workflow.domain.WorkflowEngine;
import io.krishna.agentic.workflow.domain.WorkflowStatus;
import io.krishna.agentic.workflow.persistence.WorkflowStore;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {
    private final WorkflowStore store;
    private final WorkflowEngine engine;
    private final WorkspaceService workspaces;
    private final Clock clock;

    public WorkflowService(WorkflowStore store, WorkflowEngine engine, WorkspaceService workspaces, Clock clock) {
        this.store = store;
        this.engine = engine;
        this.workspaces = workspaces;
        this.clock = clock;
    }

    public Workflow submit(String requirements, String repository, String actor) {
        workspaces.validateRepository(repository);
        return store.create(Workflow.create(requirements, repository, actor, clock.instant()));
    }

    public Workflow revise(UUID id, int revision, String requirements, String actor) {
        return store.change(id, "REPLANNED", actor, "Prior artifacts and approval invalidated",
                workflow -> {
                    requireRevision(workflow, revision);
                    if (workflow.status() == WorkflowStatus.COMPLETED) {
                        throw new WorkflowConflictException("Submit a new workflow for a completed outcome");
                    }
                    return workflow.revise(requirements, clock.instant());
                });
    }

    public Workflow clarify(UUID id, int revision, String answer, String actor) {
        return store.change(id, "CLARIFIED", actor, "Clarification accepted; new plan revision", workflow -> {
            requireRevision(workflow, revision);
            requireStatus(workflow, WorkflowStatus.AWAITING_CLARIFICATION);
            return workflow.revise(workflow.requirements() + "\nClarification: " + answer, clock.instant());
        });
    }

    public Workflow approve(UUID id, int revision, String actor, String reason) {
        return store.change(id, "APPROVED", actor, reason, workflow -> {
            requireRevision(workflow, revision);
            requireStatus(workflow, WorkflowStatus.AWAITING_APPROVAL);
            if (workflow.createdBy().equals(actor)) {
                throw new WorkflowConflictException("Approval requires a different principal");
            }
            try {
                if (!workspaces.validationUnchanged(workflow)) {
                    throw new WorkflowConflictException("Workspace changed after validation; create a new revision");
                }
            } catch (IOException exception) {
                throw new WorkflowConflictException("Validation evidence is unavailable; recover before approval");
            }
            return workflow.transition(WorkflowStatus.COMPLETED, workflow.tasks(), clock.instant());
        });
    }

    public Workflow stop(UUID id, int revision, String actor, String reason) {
        return store.change(id, "SAFE_STOPPED", actor, reason, workflow -> {
            requireRevision(workflow, revision);
            if (workflow.status() == WorkflowStatus.COMPLETED) {
                throw new WorkflowConflictException("Completed workflows cannot be stopped");
            }
            return workflow.transition(WorkflowStatus.SAFE_STOPPED, workflow.tasks(), clock.instant());
        });
    }

    public Workflow recover(UUID id, int revision, String actor) {
        return store.change(id, "RECOVERED", actor, "Fresh revision; interrupted effects will not be replayed", workflow -> {
            requireRevision(workflow, revision);
            requireStatus(workflow, WorkflowStatus.SAFE_STOPPED);
            return workflow.revise(workflow.requirements(), clock.instant());
        });
    }

    public record TaskClaim(Workflow workflow, Task task) { }

    public List<TaskClaim> claim(UUID id, int capacity) {
        List<Task> claimed = new ArrayList<>();
        Workflow snapshot = store.change(id, "TASKS_STARTED", "orchestrator", "Dependency and entry gates satisfied", workflow -> {
            if (!List.of(WorkflowStatus.QUEUED, WorkflowStatus.RUNNING).contains(workflow.status())) {
                return workflow;
            }
            List<Task> tasks = new ArrayList<>(workflow.tasks());
            for (Task ready : DependencyGraph.ready(tasks).stream().limit(capacity).toList()) {
                Task running = ready.start();
                tasks = WorkflowEngine.replace(tasks, running);
                claimed.add(running);
            }
            return claimed.isEmpty() ? workflow : workflow.transition(WorkflowStatus.RUNNING, tasks, clock.instant());
        });
        return claimed.stream().map(task -> new TaskClaim(snapshot, task)).toList();
    }

    public void complete(UUID id, int revision, Task task, TaskOutput output) {
        store.change(id, "TASK_COMPLETED", "orchestrator", task.id(), workflow -> {
            if (!active(workflow, revision, task)) {
                return workflow;
            }
            return engine.complete(workflow, task.id(), output, clock.instant());
        });
    }

    public void fail(UUID id, int revision, Task task, String error) {
        store.change(id, "TASK_FAILED", "orchestrator", task.id() + ": " + error, workflow -> {
            if (!active(workflow, revision, task)) {
                return workflow;
            }
            return engine.fail(workflow, task.id(), error, clock.instant());
        });
    }

    public void interruptOnRestart(UUID id) {
        store.change(id, "RESTART_INTERRUPTED", "orchestrator", "Uncertain task effects require explicit recovery", workflow -> {
            boolean interrupted = workflow.tasks().stream().anyMatch(task -> task.status() == TaskStatus.RUNNING);
            return interrupted ? workflow.transition(WorkflowStatus.SAFE_STOPPED, workflow.tasks(), clock.instant()) : workflow;
        });
    }

    private static boolean active(Workflow workflow, int revision, Task task) {
        return workflow.revision() == revision && workflow.status() == WorkflowStatus.RUNNING
                && workflow.task(task.id()).status() == TaskStatus.RUNNING
                && workflow.task(task.id()).attempts() == task.attempts();
    }

    private static void requireRevision(Workflow workflow, int revision) {
        if (workflow.revision() != revision) {
            throw new WorkflowConflictException("Stale workflow revision");
        }
    }

    private static void requireStatus(Workflow workflow, WorkflowStatus expected) {
        if (workflow.status() != expected) {
            throw new WorkflowConflictException("Expected workflow status " + expected);
        }
    }
}
