package io.krishna.agentic.execution;

import io.krishna.agentic.agent.AgentRequest;
import io.krishna.agentic.agent.AgentOutputPolicy;
import io.krishna.agentic.agent.EngineeringModel;
import io.krishna.agentic.workflow.domain.Task;
import io.krishna.agentic.workflow.domain.TaskKind;
import io.krishna.agentic.workflow.domain.TaskOutput;
import io.krishna.agentic.workflow.domain.Workflow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class EngineeringTaskHandler {
    private final EngineeringModel model;
    private final WorkspaceService workspaces;
    private final BuildValidator validator;
    private final AgentOutputPolicy policy;

    public EngineeringTaskHandler(EngineeringModel model, WorkspaceService workspaces, BuildValidator validator, AgentOutputPolicy policy) {
        this.model = model;
        this.workspaces = workspaces;
        this.validator = validator;
        this.policy = policy;
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
        TaskOutput output = normalize(task.kind(), model.generate(new AgentRequest(task.kind(), workflow.requirements(), context)));
        policy.validate(task.kind(), output);
        return output;
    }

    private static TaskOutput normalize(TaskKind kind, TaskOutput output) {
        boolean generatesFiles = List.of(TaskKind.IMPLEMENT, TaskKind.TEST, TaskKind.DOCUMENT,
                TaskKind.REPAIR).contains(kind);
        Map<String, String> files = generatesFiles ? output.files() : Map.of();
        List<String> questions = kind == TaskKind.ANALYZE ? output.questions() : List.of();
        if (files.equals(output.files()) && questions.equals(output.questions())) {
            return output;
        }
        return new TaskOutput(output.summary(), files, questions, output.passed());
    }

    private static Map<String, String> proposals(Workflow workflow) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Task task : workflow.tasks()) {
            if (task.output() == null || !List.of(TaskKind.IMPLEMENT, TaskKind.TEST,
                    TaskKind.DOCUMENT, TaskKind.REPAIR).contains(task.kind())) {
                continue;
            }
            for (Map.Entry<String, String> file : task.output().files().entrySet()) {
                if (task.kind() == TaskKind.IMPLEMENT && file.getKey().startsWith("src/test/")) {
                    continue;
                }
                if (task.kind() != TaskKind.REPAIR && files.containsKey(file.getKey())) {
                    if (Objects.equals(files.get(file.getKey()), file.getValue())) {
                        continue;
                    }
                    throw new IllegalArgumentException("Agents proposed conflicting file: " + file.getKey());
                }
                files.put(file.getKey(), file.getValue());
            }
        }
        return files;
    }
}
