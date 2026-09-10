package io.krishna.agentic.workflow.persistence;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(long sequence, UUID workflowId, int revision, String type,
                         String actor, String detail, Instant occurredAt) { }
