package io.krishna.agentic.workflow.application;

import io.krishna.agentic.config.PlatformProperties;
import io.krishna.agentic.execution.EngineeringTaskHandler;
import io.krishna.agentic.workflow.domain.Task;
import io.krishna.agentic.workflow.domain.TaskOutput;
import io.krishna.agentic.workflow.domain.Workflow;
import io.krishna.agentic.workflow.domain.WorkflowStatus;
import io.krishna.agentic.workflow.persistence.WorkflowStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "platform.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowScheduler.class);
    private final WorkflowStore store;
    private final WorkflowService service;
    private final EngineeringTaskHandler handler;
    private final ExecutorService executor;
    private final MeterRegistry metrics;
    private final Semaphore slots;
    private final Duration workflowTimeout;
    private final Clock clock;
    private volatile boolean ready;

    public WorkflowScheduler(WorkflowStore store, WorkflowService service, EngineeringTaskHandler handler,
            ExecutorService executor, MeterRegistry metrics, PlatformProperties properties, Clock clock) {
        this.store = store;
        this.service = service;
        this.handler = handler;
        this.executor = executor;
        this.metrics = metrics;
        this.clock = clock;
        this.slots = new Semaphore(properties.parallelism());
        this.workflowTimeout = Duration.ofMinutes(properties.workflowTimeoutMinutes());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        store.inProgressIds().forEach(service::interruptOnRestart);
        ready = true;
    }

    @Scheduled(fixedDelayString = "${platform.poll-interval-ms:500}")
    public void tick() {
        if (!ready) {
            return;
        }
        for (UUID id : store.runnableIds()) {
            if (!slots.tryAcquire()) {
                return;
            }
            try {
                Workflow workflow = store.get(id);
                if (Duration.between(workflow.revisionStartedAt(), clock.instant()).compareTo(workflowTimeout) > 0) {
                    service.stop(id, workflow.revision(), "orchestrator", "Workflow time budget exhausted");
                    slots.release();
                    continue;
                }
                List<WorkflowService.TaskClaim> claimed = service.claim(id, 1);
                if (claimed.isEmpty()) {
                    slots.release();
                    continue;
                }
                WorkflowService.TaskClaim claim = claimed.get(0);
                executor.submit(() -> execute(claim.workflow(), claim.task()));
            } catch (RuntimeException exception) {
                slots.release();
                LOG.error("Workflow dispatch failed: workflow={} type={}", id, exception.getClass().getSimpleName());
            }
        }
    }

    private void execute(Workflow workflow, Task task) {
        Timer.Sample timer = Timer.start(metrics);
        String outcome = "success";
        try {
            Workflow current = store.get(workflow.id());
            if (current.revision() != workflow.revision()
                    || current.status() != WorkflowStatus.RUNNING) {
                outcome = "superseded";
                return;
            }
            TaskOutput output = handler.execute(workflow, task);
            service.complete(workflow.id(), workflow.revision(), task, output);
            if (!output.passed()) {
                outcome = "validation_failure";
            }
        } catch (Exception exception) {
            outcome = "failure";
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recordFailure(workflow, task, exception);
            LOG.warn("Task failed: workflow={} revision={} task={} type={}", workflow.id(),
                    workflow.revision(), task.id(), exception.getClass().getSimpleName());
        } finally {
            timer.stop(metrics.timer("platform.task.duration", "kind", task.kind().name(), "outcome", outcome));
            metrics.counter("platform.task.executions", "kind", task.kind().name(), "outcome", outcome).increment();
            if (task.attempts() > 1) {
                metrics.counter("platform.task.retries").increment();
            }
            slots.release();
        }
    }

    private void recordFailure(Workflow workflow, Task task, Exception exception) {
        try {
            service.fail(workflow.id(), workflow.revision(), task, exception.getClass().getSimpleName());
        } catch (RuntimeException persistenceFailure) {
            LOG.error("Could not checkpoint failure; restart recovery required: workflow={} task={} type={}",
                    workflow.id(), task.id(), persistenceFailure.getClass().getSimpleName());
        }
    }
}
