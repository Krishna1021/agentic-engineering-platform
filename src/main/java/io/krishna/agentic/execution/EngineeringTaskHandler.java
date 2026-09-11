package io.krishna.agentic.execution;

import io.krishna.agentic.agent.AgentRequest;
import io.krishna.agentic.agent.EngineeringModel;
import io.krishna.agentic.workflow.domain.Task;
import io.krishna.agentic.workflow.domain.TaskKind;
import io.krishna.agentic.workflow.domain.TaskOutput;
import io.krishna.agentic.workflow.domain.Workflow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EngineeringTaskHandler {
    private final EngineeringModel model;
    private final WorkspaceService workspaces;
    private final BuildValidator validator;

    public EngineeringTaskHandler(EngineeringModel model, WorkspaceService workspaces, BuildValidator validator) {
        this.model = model;
        this.workspaces = workspaces;
        this.validator = validator;
    }

    public TaskOutput execute(Workflow workflow, Task task) throws Exception {
        return switch (task.kind()) {
            case INSPECT -> new TaskOutput("Bounded source context and baseline snapshot",
                    workspaces.prepare(workflow), List.of(), true);
            case APPLY -> TaskOutput.note(workspaces.apply(workflow, proposals(workflow)));
            case VALIDATE -> validator.validate(workflow);
            default -> generate(workflow, task);
        };
    }

    private TaskOutput generate(Workflow workflow, Task task) throws Exception {
        Map<String, TaskOutput> context = new LinkedHashMap<>();
        for (Task completed : workflow.tasks()) {
            if (completed.output() != null) {
                context.put(completed.id(), completed.output());
            }
        }
        TaskOutput output = model.generate(new AgentRequest(task.kind(), workflow.requirements(), context));
        if (output.summary().isBlank() || output.summary().length() > 64000
                || output.files().size() > 300 || output.questions().size() > 10) {
            throw new IllegalArgumentException("Agent output violates size limits");
        }
        if (task.kind() != TaskKind.ANALYZE && !output.questions().isEmpty()) {
            throw new IllegalArgumentException("Only requirement analysis can ask for clarification");
        }
        if (List.of(TaskKind.IMPLEMENT, TaskKind.TEST, TaskKind.DOCUMENT, TaskKind.REPAIR).contains(task.kind())
                && output.files().isEmpty()) {
            throw new IllegalArgumentException("Generation stage must produce files");
        }
        return output;
    }

    private static Map<String, String> proposals(Workflow workflow) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Task task : workflow.tasks()) {
            if (task.output() == null || !List.of(TaskKind.IMPLEMENT, TaskKind.TEST,
                    TaskKind.DOCUMENT, TaskKind.REPAIR).contains(task.kind())) {
                continue;
            }
            for (Map.Entry<String, String> file : task.output().files().entrySet()) {
                if (task.kind() != TaskKind.REPAIR && files.containsKey(file.getKey())) {
                    throw new IllegalArgumentException("Agents proposed conflicting file: " + file.getKey());
                }
                files.put(file.getKey(), file.getValue());
            }
        }
        return files;
    }
}
