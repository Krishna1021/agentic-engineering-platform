package io.krishna.agentic.agent;

import io.krishna.agentic.workflow.domain.TaskOutput;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Repeatable orchestration fixture. It does not claim to implement arbitrary requirements. */
@Component
@ConditionalOnProperty(name = "platform.model.provider", havingValue = "demo", matchIfMissing = true)
public class DemoEngineeringModel implements EngineeringModel {
    @Override
    public TaskOutput generate(AgentRequest request) {
        return switch (request.role()) {
            case ANALYZE -> analyze(request.requirements());
            case DESIGN -> TaskOutput.note("Demo plan: inspect baseline; produce a Java 17 scaffold; "
                    + "generate smoke tests and documentation in parallel; apply, validate, request review. "
                    + "Application-specific behavior requires the real model provider.");
            case IMPLEMENT, REPAIR -> new TaskOutput("Offline Java scaffold; not requirement-specific implementation",
                    scaffold(), List.of(), true);
            case TEST -> new TaskOutput("Dependency-free executable smoke test", Map.of(
                    "src/test/java/generated/SmokeTest.java", """
                    package generated;

                    public final class SmokeTest {
                        public static void main(String[] args) {
                            if (!Application.status().equals("ready")) {
                                throw new AssertionError("Expected ready status");
                            }
                        }
                    }
                    """), List.of(), true);
            case DOCUMENT -> new TaskOutput("Demo outcome and limitations", Map.of("docs/generated-outcome.md",
                    "# Generated demo\n\nRequested requirement:\n\n" + request.requirements()
                            + "\n\nThis scaffold demonstrates orchestration only. Review validation evidence.\n"),
                    List.of(), true);
            default -> throw new IllegalArgumentException("No model role for " + request.role());
        };
    }

    private static TaskOutput analyze(String requirements) {
        if (requirements.trim().split("\\s+").length < 5) {
            return new TaskOutput("Clarification needed", Map.of(),
                    List.of("What observable behavior and acceptance criteria should this change satisfy?"), true);
        }
        return TaskOutput.note("Demo interpretation: " + requirements);
    }

    private static Map<String, String> scaffold() {
        return Map.of(
                "settings.gradle", "rootProject.name = \"generated-application\"\n",
                "build.gradle", """
                plugins { id 'java' }
                java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
                tasks.register('smokeTest', JavaExec) {
                    dependsOn(tasks.testClasses)
                    classpath = sourceSets.test.runtimeClasspath
                    mainClass = "generated.SmokeTest"
                }
                tasks.test { dependsOn('smokeTest') }
                """,
                "src/main/java/generated/Application.java", """
                package generated;

                public final class Application {
                    private Application() { }
                    public static String status() { return "ready"; }
                    public static void main(String[] args) { System.out.println(status()); }
                }
                """);
    }
}
