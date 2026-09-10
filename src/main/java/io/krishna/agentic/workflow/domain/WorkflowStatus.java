package io.krishna.agentic.workflow.domain;

public enum WorkflowStatus {
    QUEUED, RUNNING, AWAITING_CLARIFICATION, AWAITING_APPROVAL, COMPLETED, FAILED, SAFE_STOPPED
}
