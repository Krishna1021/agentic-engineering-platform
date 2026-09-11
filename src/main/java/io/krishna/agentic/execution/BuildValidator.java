package io.krishna.agentic.execution;

import io.krishna.agentic.config.PlatformProperties;
import io.krishna.agentic.workflow.domain.TaskOutput;
import io.krishna.agentic.workflow.domain.Workflow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BuildValidator {
    private final PlatformProperties properties;
    private final WorkspaceService workspaces;
    private final ProcessRunner runner;

    public BuildValidator(PlatformProperties properties, WorkspaceService workspaces, ProcessRunner runner) {
        this.properties = properties;
        this.workspaces = workspaces;
        this.runner = runner;
        if (!List.of("docker", "demo").contains(properties.validationMode())) {
            throw new IllegalArgumentException("VALIDATION_MODE must be docker or demo");
        }
    }

    public TaskOutput validate(Workflow workflow) throws IOException, InterruptedException {
        Path repository = workspaces.repository(workflow);
        if (properties.validationMode().equals("demo")) {
            workspaces.sealValidation(workflow);
            return TaskOutput.note("DEMO_ONLY: proposal applied; compilation and tests were NOT executed.");
        }
        if (!Files.exists(repository.resolve("build.gradle.kts")) && !Files.exists(repository.resolve("build.gradle"))) {
            return new TaskOutput("No Gradle build supplied", Map.of(), List.of(), false);
        }
        String name = "agentic-" + workflow.id() + "-" + workflow.revision();
        List<String> command = List.of("docker", "run", "--rm", "--name", name,
                "--network=none", "--memory=512m", "--cpus=1", "--pids-limit=128",
                "--cap-drop=ALL", "--security-opt=no-new-privileges", "--read-only",
                "--user=1000:1000", "--tmpfs", "/tmp:rw,nosuid,size=128m",
                "--tmpfs", "/home/gradle/.gradle:rw,nosuid,size=128m,uid=1000,gid=1000",
                "--mount", "type=bind,source=" + repository + ",target=/workspace",
                "--workdir=/workspace", properties.buildImage(),
                "gradle", "--offline", "--no-daemon", "--console=plain", "clean", "test");
        BuildResult result;
        try {
            result = runner.run(command, Duration.ofSeconds(properties.buildTimeoutSeconds()));
        } finally {
            // Killing the Docker CLI alone does not guarantee that its container has stopped.
            runner.run(List.of("docker", "rm", "--force", name), Duration.ofSeconds(10));
        }
        String evidence = "exit=" + result.exitCode() + ", timedOut=" + result.timedOut() + "\n" + result.output();
        if (result.passed()) {
            workspaces.sealValidation(workflow);
        }
        return new TaskOutput(evidence, Map.of(), List.of(), result.passed());
    }
}
