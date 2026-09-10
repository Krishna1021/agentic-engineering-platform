package io.krishna.agentic.workflow.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TaskOutput(String summary, Map<String, String> files, List<String> questions, boolean passed) {
    public TaskOutput {
        Objects.requireNonNull(summary, "summary");
        files = Map.copyOf(files);
        questions = List.copyOf(questions);
    }

    public static TaskOutput note(String summary) {
        return new TaskOutput(summary, Map.of(), List.of(), true);
    }
}
