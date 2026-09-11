package io.krishna.agentic.workflow.api;

import io.krishna.agentic.workflow.domain.WorkflowStatus;
import java.util.List;
import java.util.UUID;

public record EngineeringSummary(UUID workflowId, int revision, WorkflowStatus status,
        String requirements, String plan, List<String> changedFiles, List<String> validationEvidence,
        List<String> limitations) { }
