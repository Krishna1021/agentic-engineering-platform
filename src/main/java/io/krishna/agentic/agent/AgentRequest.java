package io.krishna.agentic.agent;

import io.krishna.agentic.workflow.domain.TaskKind;
import io.krishna.agentic.workflow.domain.TaskOutput;
import java.util.Map;

public record AgentRequest(TaskKind role, String requirements, Map<String, TaskOutput> context) {
    public AgentRequest { context = Map.copyOf(context); }
}
