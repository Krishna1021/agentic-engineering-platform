package io.krishna.agentic.workflow.application;

import io.krishna.agentic.workflow.domain.TaskKind;
import io.krishna.agentic.workflow.domain.TaskStatus;
import io.krishna.agentic.workflow.domain.WorkflowStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkflowTelemetry {
    private final MeterRegistry metrics;
    private final Map<UUID, Instant> recovering = new ConcurrentHashMap<>();

    public WorkflowTelemetry(MeterRegistry metrics) { this.metrics = metrics; }

    @TransactionalEventListener
    public void record(WorkflowChanged event) {
        metrics.counter("platform.audit.events", "type", event.eventType()).increment();
        var after = event.after();
        var before = event.before();
        if (before != null && (before.revision() != after.revision()
                || List.of(WorkflowStatus.COMPLETED, WorkflowStatus.FAILED, WorkflowStatus.SAFE_STOPPED).contains(after.status()))) {
            recovering.remove(after.id());
        }
        if (before == null || before.status() != after.status()) {
            metrics.counter("platform.workflow.transitions", "status", after.status().name()).increment();
            if (List.of(WorkflowStatus.COMPLETED, WorkflowStatus.FAILED, WorkflowStatus.SAFE_STOPPED).contains(after.status())) {
                metrics.timer("platform.workflow.latency", "status", after.status().name())
                        .record(Duration.between(after.revisionStartedAt(), after.updatedAt()));
            }
        }
        if (before == null || !event.eventType().equals("TASK_COMPLETED")) {
            return;
        }
        before.tasks().stream().filter(task -> task.status() == TaskStatus.RUNNING)
                .filter(task -> after.task(task.id()).status() == TaskStatus.SUCCEEDED).forEach(task -> {
                    if (task.kind() == TaskKind.REPAIR) {
                        metrics.counter("platform.repairs").increment();
                    }
                    if (task.kind() == TaskKind.APPLY && !task.id().equals("apply")) {
                        metrics.counter("platform.rollbacks").increment();
                    }
                    if (task.kind() == TaskKind.VALIDATE && !after.task(task.id()).output().passed()) {
                        metrics.counter("platform.validation.failures").increment();
                        if (after.status() == WorkflowStatus.RUNNING) {
                            recovering.putIfAbsent(after.id(), after.updatedAt());
                        }
                    }
                    if (task.kind() == TaskKind.VALIDATE && after.task(task.id()).output().passed()) {
                        Instant started = recovering.remove(after.id());
                        if (started != null) {
                            metrics.timer("platform.recovery.duration").record(Duration.between(started, after.updatedAt()));
                        }
                    }
                });
    }
}
