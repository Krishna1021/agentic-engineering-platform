package io.krishna.agentic.execution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class BoundedProcessRunner implements ProcessRunner {
    private static final int MAX_LOG_BYTES = 64_000;

    @Override
    public BuildResult run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().keySet().removeIf(key -> key.contains("KEY") || key.contains("TOKEN")
                || key.contains("PASSWORD") || key.startsWith("DATABASE"));
        Process process = builder.start();
        StringBuffer output = new StringBuffer();
        Thread reader = new Thread(() -> drain(process.getInputStream(), output), "bounded-build-log");
        reader.setDaemon(true);
        reader.start();
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            reader.join(2000);
            return new BuildResult(finished ? process.exitValue() : -1, !finished, output.toString());
        } finally {
            process.destroyForcibly();
        }
    }

    private static void drain(InputStream stream, StringBuffer output) {
        try (stream) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                int remaining = MAX_LOG_BYTES - output.length();
                if (remaining > 0) {
                    output.append(new String(buffer, 0, Math.min(remaining, count), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException exception) {
            // The process may close its output when cancelled. Exit/timeout remains authoritative.
        }
    }
}
