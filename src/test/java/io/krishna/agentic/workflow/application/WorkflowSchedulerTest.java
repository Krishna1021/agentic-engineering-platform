package io.krishna.agentic.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.krishna.agentic.config.PlatformProperties;
import io.krishna.agentic.execution.EngineeringTaskHandler;
import io.krishna.agentic.workflow.domain.Task;
import io.krishna.agentic.workflow.domain.TaskKind;
import io.krishna.agentic.workflow.domain.TaskOutput;
import io.krishna.agentic.workflow.domain.Workflow;
import io.krishna.agentic.workflow.domain.WorkflowStatus;
import io.krishna.agentic.workflow.persistence.WorkflowStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorkflowSchedulerTest {
    @Test
    void executesIndependentTasksConcurrentlyWithinCapacity() throws Exception {
        var store = mock(WorkflowStore.class);
        var service = mock(WorkflowService.class);
        var handler = mock(EngineeringTaskHandler.class);
        var executor = Executors.newFixedThreadPool(2);
        var workflow = Workflow.create("Build a service with executable tests", null, "operator", Instant.now())
                .transition(WorkflowStatus.RUNNING, List.of(), Instant.now());
        var tests = Task.pending("test", TaskKind.TEST).start();
        var docs = Task.pending("document", TaskKind.DOCUMENT).start();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        when(store.runnableIds()).thenReturn(List.of(workflow.id()));
        when(store.inProgressIds()).thenReturn(List.of());
        when(store.get(workflow.id())).thenReturn(workflow);
        when(service.claim(eq(workflow.id()), anyInt())).thenReturn(
                List.of(new WorkflowService.TaskClaim(workflow, tests)),
                List.of(new WorkflowService.TaskClaim(workflow, docs)));
        when(handler.execute(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Test timed out"); }
            return TaskOutput.note("done");
        });
        doAnswer(invocation -> { completed.countDown(); return null; }).when(service).complete(any(), anyInt(), any(), any());
        var properties = new PlatformProperties(Path.of("build/work"), Path.of("build/repos"), 2, 2, 1, 30, 60, "demo", "unused");
        var scheduler = new WorkflowScheduler(store, service, handler, executor, new SimpleMeterRegistry(), properties, Clock.systemUTC());
        try {
            scheduler.recover();
            scheduler.tick();
            scheduler.tick();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            scheduler.tick();
            verify(service, org.mockito.Mockito.times(2)).claim(eq(workflow.id()), anyInt());
            release.countDown();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
