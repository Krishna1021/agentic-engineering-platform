package io.krishna.agentic.agent;

import io.krishna.agentic.workflow.domain.TaskOutput;

public interface EngineeringModel {
    TaskOutput generate(AgentRequest request) throws Exception;
}
