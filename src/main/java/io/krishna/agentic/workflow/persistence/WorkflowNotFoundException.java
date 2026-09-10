package io.krishna.agentic.workflow.persistence;

import java.util.UUID;

public class WorkflowNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WorkflowNotFoundException(UUID id) {
        super("Workflow not found: " + id);
    }
}
