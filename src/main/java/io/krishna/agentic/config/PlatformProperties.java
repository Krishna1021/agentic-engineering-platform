package io.krishna.agentic.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("platform")
public record PlatformProperties(
        @NotNull Path workspaceRoot,
        @NotNull Path repositoryRoot,
        @Min(1) @Max(8) int parallelism,
        @Min(1) @Max(5) int maxAttempts,
        @Min(0) @Max(3) int maxRepairs,
        @Min(1) @Max(120) int workflowTimeoutMinutes,
        @Min(1) @Max(600) int buildTimeoutSeconds,
        @NotBlank String validationMode,
        @NotBlank String buildImage) { }
