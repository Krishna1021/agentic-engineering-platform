package io.krishna.agentic.agent;

import io.krishna.agentic.workflow.domain.TaskKind;
import io.krishna.agentic.workflow.domain.TaskOutput;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentOutputPolicy {
    private static final int MAX_SUMMARY_CHARACTERS = 64_000;
    private static final int MAX_FILE_BYTES = 100_000;
    private static final int MAX_OUTPUT_BYTES = 2_000_000;

    public void validate(TaskKind role, TaskOutput output) {
        if (output.summary().isBlank() || output.summary().length() > MAX_SUMMARY_CHARACTERS
                || output.files().size() > 300 || output.questions().size() > 10) {
            throw new IllegalArgumentException("Agent output violates size limits");
        }
        if (role != TaskKind.ANALYZE && !output.questions().isEmpty()) {
            throw new IllegalArgumentException("Only requirement analysis can ask for clarification");
        }
        boolean generatesFiles = List.of(TaskKind.IMPLEMENT, TaskKind.TEST, TaskKind.DOCUMENT, TaskKind.REPAIR).contains(role);
        if (generatesFiles && output.files().isEmpty()) {
            throw new IllegalArgumentException("Generation stage must produce files");
        }
        if (!generatesFiles && !output.files().isEmpty()) {
            throw new IllegalArgumentException("Analysis and design stages cannot propose file changes");
        }
        int total = 0;
        for (var file : output.files().entrySet()) {
            int size = file.getValue().getBytes(StandardCharsets.UTF_8).length;
            total += size;
            if (size > MAX_FILE_BYTES || total > MAX_OUTPUT_BYTES) {
                throw new IllegalArgumentException("Agent files exceed permitted size");
            }
            if (role == TaskKind.DOCUMENT && (!file.getKey().startsWith("docs/") || !file.getKey().endsWith(".md"))) {
                throw new IllegalArgumentException("Documentation role may only write Markdown under docs/");
            }
            if (role == TaskKind.TEST && !file.getKey().startsWith("src/test/")) {
                throw new IllegalArgumentException("Test role may only write under src/test/");
            }
        }
    }
}
