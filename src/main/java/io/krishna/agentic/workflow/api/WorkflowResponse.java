package io.krishna.agentic.workflow.api;

import io.krishna.agentic.workflow.domain.TaskKind;
import io.krishna.agentic.workflow.domain.TaskOutput;
import io.krishna.agentic.workflow.domain.TaskStatus;
import io.krishna.agentic.workflow.domain.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record WorkflowResponse(UUID id, int revision, String requirements, String repository,
                               WorkflowStatus status, List<TaskView> tasks, Instant createdAt, Instant updatedAt) {
    public record TaskView(String id, TaskKind kind, Set<String> dependencies, TaskStatus status,
                           int attempts, TaskOutput output, String error) { }
}
