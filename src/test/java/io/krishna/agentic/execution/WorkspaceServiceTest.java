package io.krishna.agentic.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.krishna.agentic.config.PlatformProperties;
import io.krishna.agentic.workflow.domain.Workflow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceServiceTest {
    @TempDir Path temporary;

    @Test
    void createsGreenfieldWorkspaceAndAppliesFiles() throws IOException {
        WorkspaceService service = service();
        Workflow workflow = workflow(null);
        assertThat(service.prepare(workflow)).isEmpty();
        String manifest = service.apply(workflow, Map.of("src/main/java/App.java", "class App {}"));
        assertThat(Files.readString(service.repository(workflow).resolve("src/main/java/App.java"))).isEqualTo("class App {}");
        assertThat(manifest).contains("NEW ->");
    }

    @Test
    void repairRestoresBaselineAndKeepsOriginalRepositoryUntouched() throws IOException {
        Path source = Files.createDirectories(temporary.resolve("repositories/existing"));
        Files.writeString(source.resolve("README.md"), "original");
        WorkspaceService service = service();
        Workflow workflow = workflow("existing");
        assertThat(service.prepare(workflow)).containsEntry("README.md", "original");
        service.apply(workflow, Map.of("README.md", "changed", "bad.java", "broken"));
        service.apply(workflow, Map.of("fixed.java", "class Fixed {}"));
        assertThat(Files.readString(service.repository(workflow).resolve("README.md"))).isEqualTo("original");
        assertThat(service.repository(workflow).resolve("bad.java")).doesNotExist();
        assertThat(Files.readString(source.resolve("README.md"))).isEqualTo("original");
    }

    @Test
    void rejectsTraversalAbsolutePathsUnsupportedFilesAndCaseCollisions() throws IOException {
        WorkspaceService service = service();
        Workflow workflow = workflow(null);
        service.prepare(workflow);
        for (String path : new String[]{"../escape.java", "/tmp/Escape.java", "C:/Escape.java", ".git/config", "run.sh"}) {
            assertThatThrownBy(() -> service.apply(workflow, Map.of(path, "bad"))).isInstanceOf(IllegalArgumentException.class);
        }
        Map<String, String> conflicts = new LinkedHashMap<>();
        conflicts.put("App.java", "one");
        conflicts.put("app.java", "two");
        assertThatThrownBy(() -> service.apply(workflow, conflicts)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateRepository("../outside")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOversizedProposalsAndExistingRevisions() throws IOException {
        WorkspaceService service = service();
        Workflow workflow = workflow(null);
        service.prepare(workflow);
        assertThatThrownBy(() -> service.apply(workflow, Map.of("large.java", "x".repeat(100001))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.prepare(workflow)).isInstanceOf(IOException.class);
    }

    private WorkspaceService service() {
        return new WorkspaceService(new PlatformProperties(temporary.resolve("workspaces"), temporary.resolve("repositories"),
                2, 2, 1, 30, 60, "demo", "gradle:8.14.5-jdk17"));
    }

    private Workflow workflow(String repository) {
        return Workflow.create("Build a new application with tests", repository, "operator", Instant.now());
    }
}
