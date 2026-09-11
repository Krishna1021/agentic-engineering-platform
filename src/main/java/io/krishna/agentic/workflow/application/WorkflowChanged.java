package io.krishna.agentic.workflow.application;

import io.krishna.agentic.workflow.domain.Workflow;

public record WorkflowChanged(Workflow before, Workflow after, String eventType) { }
