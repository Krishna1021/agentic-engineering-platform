package io.krishna.agentic.workflow.application;

public class WorkflowConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public WorkflowConflictException(String message) { super(message); }
}
