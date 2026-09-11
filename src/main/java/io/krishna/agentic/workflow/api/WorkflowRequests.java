package io.krishna.agentic.workflow.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class WorkflowRequests {
    private WorkflowRequests() { }

    public record Create(@NotBlank @Size(max = 20000) String requirements,
                         @Size(max = 200) String repository) { }
    public record Revise(@Min(1) int revision, @NotBlank @Size(max = 20000) String requirements) { }
    public record Clarify(@Min(1) int revision, @NotBlank @Size(max = 5000) String answer) { }
    public record Decision(@Min(1) int revision, @NotBlank @Size(max = 1000) String reason) { }
    public record Recover(@Min(1) int revision) { }
}
