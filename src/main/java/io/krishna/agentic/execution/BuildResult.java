package io.krishna.agentic.execution;

public record BuildResult(int exitCode, boolean timedOut, String output) {
    public boolean passed() { return exitCode == 0 && !timedOut; }
}
