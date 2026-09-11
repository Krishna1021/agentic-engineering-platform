package io.krishna.agentic.execution;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public interface ProcessRunner {
    BuildResult run(List<String> command, Duration timeout) throws IOException, InterruptedException;
}
