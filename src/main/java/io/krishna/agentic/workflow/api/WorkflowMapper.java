package io.krishna.agentic.workflow.api;

import io.krishna.agentic.workflow.domain.Workflow;
import io.krishna.agentic.workflow.domain.TaskKind;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkflowMapper {
    public EngineeringSummary toSummary(Workflow workflow) {
        String plan = workflow.tasks().stream().filter(task -> task.kind() == TaskKind.DESIGN && task.output() != null)
                .map(task -> task.output().summary()).findFirst().orElse("Planning has not completed");
        List<String> files = workflow.tasks().stream().filter(task -> task.output() != null)
                .filter(task -> List.of(TaskKind.IMPLEMENT, TaskKind.TEST, TaskKind.DOCUMENT, TaskKind.REPAIR).contains(task.kind()))
                .flatMap(task -> task.output().files().keySet().stream()).distinct().sorted().toList();
        List<String> validation = workflow.tasks().stream().filter(task -> task.kind() == TaskKind.VALIDATE && task.output() != null)
                .map(task -> task.id() + ": " + task.output().summary()).toList();
        return new EngineeringSummary(workflow.id(), workflow.revision(), workflow.status(), workflow.requirements(),
                plan, files, validation, List.of("COMPLETED means human-reviewed workspace, not deployed software",
                        "Demo provider generates a fixed scaffold; inspect evidence before treating a run as application implementation",
                        "Only supported text sources and Gradle validation are handled in this version"));
    }

    public WorkflowResponse toResponse(Workflow workflow) {
        return new WorkflowResponse(workflow.id(), workflow.revision(), workflow.requirements(), workflow.repository(),
                workflow.status(), workflow.tasks().stream().map(task -> new WorkflowResponse.TaskView(task.id(),
                        task.kind(), task.dependencies(), task.status(), task.attempts(), task.output(), task.error())).toList(),
                workflow.createdAt(), workflow.updatedAt());
    }
}
