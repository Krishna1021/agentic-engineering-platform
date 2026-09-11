package io.krishna.agentic.agent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.krishna.agentic.workflow.domain.TaskKind;
import io.krishna.agentic.workflow.domain.TaskOutput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentOutputPolicyTest {
    private final AgentOutputPolicy policy = new AgentOutputPolicy();

    @Test
    void acceptsScopedTestAndDocumentationFiles() {
        assertThatCode(() -> policy.validate(TaskKind.TEST, output("src/test/java/ExampleTest.java"))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(TaskKind.DOCUMENT, output("docs/design.md"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsAttemptsToModifyProductionFromOtherRoles() {
        assertThatThrownBy(() -> policy.validate(TaskKind.TEST, output("src/main/java/Application.java")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate(TaskKind.DOCUMENT, output("build.gradle")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate(TaskKind.ANALYZE, output("Application.java")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyGenerationOversizedFilesAndLateClarification() {
        assertThatThrownBy(() -> policy.validate(TaskKind.IMPLEMENT, TaskOutput.note("No files")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate(TaskKind.IMPLEMENT,
                new TaskOutput("Large", Map.of("Large.java", "a".repeat(100001)), List.of(), true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate(TaskKind.DESIGN,
                new TaskOutput("Question", Map.of(), List.of("What now?"), true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TaskOutput output(String file) {
        return new TaskOutput("Proposal", Map.of(file, "content"), List.of(), true);
    }
}
