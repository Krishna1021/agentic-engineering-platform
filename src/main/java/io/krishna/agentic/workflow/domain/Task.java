package io.krishna.agentic.workflow.domain;

import java.util.Set;

public record Task(String id, TaskKind kind, Set<String> dependencies, TaskStatus status,
                   int attempts, TaskOutput output, String error) {
    public Task {
        dependencies = Set.copyOf(dependencies);
        if (id == null || id.isBlank() || kind == null || status == null || attempts < 0) {
            throw new IllegalArgumentException("Invalid task");
        }
    }

    public static Task pending(String id, TaskKind kind, String... dependencies) {
        return new Task(id, kind, Set.of(dependencies), TaskStatus.PENDING, 0, null, null);
    }

    public Task start() {
        if (status != TaskStatus.PENDING) {
            throw new IllegalStateException("Only pending tasks can start");
        }
        return new Task(id, kind, dependencies, TaskStatus.RUNNING, attempts + 1, null, null);
    }

    public Task succeed(TaskOutput result) {
        return new Task(id, kind, dependencies, TaskStatus.SUCCEEDED, attempts, result, null);
    }

    public Task fail(String reason, boolean retry) {
        return new Task(id, kind, dependencies, retry ? TaskStatus.PENDING : TaskStatus.FAILED,
                attempts, null, reason);
    }
}
