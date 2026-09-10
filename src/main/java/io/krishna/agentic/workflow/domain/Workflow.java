package io.krishna.agentic.workflow.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Workflow(UUID id, String requirements, String repository, int revision,
                       WorkflowStatus status, List<Task> tasks, String createdBy,
                       Instant createdAt, Instant updatedAt) {
    public Workflow {
        if (requirements == null || requirements.isBlank() || requirements.length() > 20000) {
            throw new IllegalArgumentException("Requirements must contain 1 to 20000 characters");
        }
        tasks = List.copyOf(tasks);
    }

    public static Workflow create(String requirements, String repository, String actor, Instant now) {
        return new Workflow(UUID.randomUUID(), requirements.trim(), repository, 1, WorkflowStatus.QUEUED,
                List.of(Task.pending("analyze", TaskKind.ANALYZE)), actor, now, now);
    }

    public Workflow transition(WorkflowStatus next, List<Task> nextTasks, Instant now) {
        return new Workflow(id, requirements, repository, revision, next, nextTasks, createdBy, createdAt, now);
    }

    public Workflow revise(String updatedRequirements, Instant now) {
        return new Workflow(id, updatedRequirements, repository, revision + 1, WorkflowStatus.QUEUED,
                List.of(Task.pending("analyze", TaskKind.ANALYZE)), createdBy, createdAt, now);
    }

    public Task task(String taskId) {
        return tasks.stream().filter(task -> task.id().equals(taskId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown task: " + taskId));
    }
}
