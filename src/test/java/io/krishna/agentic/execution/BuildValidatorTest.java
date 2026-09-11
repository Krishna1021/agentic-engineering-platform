package io.krishna.agentic.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.krishna.agentic.config.PlatformProperties;
import io.krishna.agentic.workflow.domain.Workflow;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class BuildValidatorTest {
    @TempDir Path temporary;

    @Test
    void usesFixedIsolatedCommandAndCapturesFailure() throws Exception {
        var properties = properties("docker");
        var workspaces = new WorkspaceService(properties);
        var workflow = Workflow.create("Build a Java application with tests", null, "operator", Instant.now());
        workspaces.prepare(workflow);
        workspaces.apply(workflow, Map.of("build.gradle", "plugins { id 'java' }"));
        var runner = mock(ProcessRunner.class);
        when(runner.run(any(), any())).thenReturn(new BuildResult(1, false, "compiler failure"));
        var result = new BuildValidator(properties, workspaces, runner).validate(workflow);
        assertThat(result.passed()).isFalse();
        assertThat(result.summary()).contains("compiler failure");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> commands = ArgumentCaptor.forClass(List.class);
        verify(runner, org.mockito.Mockito.times(2)).run(commands.capture(), any());
        assertThat(commands.getAllValues().get(0)).contains("--network=none", "--read-only", "--offline", "--cap-drop=ALL");
        assertThat(commands.getAllValues().get(1)).startsWith("docker", "rm", "--force");
        assertThat(workspaces.validationUnchanged(workflow)).isFalse();
    }

    @Test
    void demoEvidenceExplicitlySaysTestsWereNotRun() throws Exception {
        var properties = properties("demo");
        var workspaces = new WorkspaceService(properties);
        var workflow = Workflow.create("Build a Java application with tests", null, "operator", Instant.now());
        workspaces.prepare(workflow);
        var result = new BuildValidator(properties, workspaces, mock(ProcessRunner.class)).validate(workflow);
        assertThat(result.summary()).contains("NOT executed");
        assertThat(workspaces.validationUnchanged(workflow)).isTrue();
    }

    @Test
    void rejectsUnknownValidationMode() {
        assertThatThrownBy(() -> new BuildValidator(properties("shell"), new WorkspaceService(properties("shell")), mock(ProcessRunner.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PlatformProperties properties(String mode) {
        return new PlatformProperties(temporary.resolve("workspaces"), temporary.resolve("repositories"),
                2, 2, 1, 30, 60, mode, "gradle:8.14.5-jdk17");
    }
}
