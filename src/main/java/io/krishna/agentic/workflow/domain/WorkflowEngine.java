package io.krishna.agentic.workflow.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class WorkflowEngine {
    private final int maxAttempts;
    private final int maxRepairs;

    public WorkflowEngine(int maxAttempts, int maxRepairs) {
        if (maxAttempts < 1 || maxRepairs < 0) {
            throw new IllegalArgumentException("Invalid retry policy");
        }
        this.maxAttempts = maxAttempts;
        this.maxRepairs = maxRepairs;
    }

    public Workflow complete(Workflow workflow, String taskId, TaskOutput output, Instant now) {
        Task task = workflow.task(taskId);
        requireRunning(workflow, task);
        List<Task> tasks = replace(workflow.tasks(), task.succeed(output));
        if (task.kind() == TaskKind.ANALYZE) {
            if (!output.questions().isEmpty()) {
                return workflow.transition(WorkflowStatus.AWAITING_CLARIFICATION, tasks, now);
            }
            tasks.addAll(engineeringTasks());
        }
        if (task.kind() == TaskKind.VALIDATE && !output.passed()) {
            long repairs = tasks.stream().filter(t -> t.kind() == TaskKind.REPAIR).count();
            if (repairs >= maxRepairs) {
                return workflow.transition(WorkflowStatus.FAILED, tasks, now);
            }
            String suffix = "-" + (repairs + 1);
            tasks.add(Task.pending("repair" + suffix, TaskKind.REPAIR, taskId));
            tasks.add(Task.pending("apply" + suffix, TaskKind.APPLY, "repair" + suffix));
            tasks.add(Task.pending("validate" + suffix, TaskKind.VALIDATE, "apply" + suffix));
        }
        DependencyGraph.validate(tasks);
        boolean finished = tasks.stream().allMatch(t -> t.status() == TaskStatus.SUCCEEDED);
        return workflow.transition(finished ? WorkflowStatus.AWAITING_APPROVAL : WorkflowStatus.RUNNING, tasks, now);
    }

    public Workflow fail(Workflow workflow, String taskId, String error, Instant now) {
        Task task = workflow.task(taskId);
        requireRunning(workflow, task);
        // Filesystem and build failures are uncertain effects; recovery must use a fresh revision.
        boolean effect = task.kind() == TaskKind.APPLY || task.kind() == TaskKind.INSPECT
                || task.kind() == TaskKind.VALIDATE;
        boolean retry = !effect && task.attempts() < maxAttempts;
        WorkflowStatus status = retry ? WorkflowStatus.RUNNING
                : effect ? WorkflowStatus.SAFE_STOPPED : WorkflowStatus.FAILED;
        return workflow.transition(status, replace(workflow.tasks(), task.fail(error, retry)), now);
    }

    public static List<Task> replace(List<Task> tasks, Task replacement) {
        List<Task> result = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            result.add(task.id().equals(replacement.id()) ? replacement : task);
        }
        return result;
    }

    private static void requireRunning(Workflow workflow, Task task) {
        if (workflow.status() != WorkflowStatus.RUNNING || task.status() != TaskStatus.RUNNING) {
            throw new IllegalStateException("Task is not active");
        }
    }

    private static List<Task> engineeringTasks() {
        return List.of(
                Task.pending("inspect", TaskKind.INSPECT, "analyze"),
                Task.pending("design", TaskKind.DESIGN, "inspect"),
                Task.pending("implement", TaskKind.IMPLEMENT, "design"),
                Task.pending("test", TaskKind.TEST, "implement"),
                Task.pending("document", TaskKind.DOCUMENT, "implement"),
                Task.pending("apply", TaskKind.APPLY, "test", "document"),
                Task.pending("validate", TaskKind.VALIDATE, "apply"));
    }
}
