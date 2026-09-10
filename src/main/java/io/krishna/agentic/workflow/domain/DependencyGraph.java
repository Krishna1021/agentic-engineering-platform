package io.krishna.agentic.workflow.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class DependencyGraph {
    private DependencyGraph() { }

    public static void validate(List<Task> tasks) {
        Set<String> ids = tasks.stream().map(Task::id).collect(Collectors.toSet());
        if (ids.size() != tasks.size()) {
            throw new IllegalArgumentException("Duplicate task identifiers");
        }
        if (tasks.stream().anyMatch(task -> !ids.containsAll(task.dependencies()))) {
            throw new IllegalArgumentException("Missing task dependency");
        }
        Set<String> visited = new HashSet<>();
        while (visited.size() < ids.size()) {
            int previous = visited.size();
            tasks.stream().filter(task -> visited.containsAll(task.dependencies()))
                    .map(Task::id).forEach(visited::add);
            if (visited.size() == previous) {
                throw new IllegalArgumentException("Task dependencies contain a cycle");
            }
        }
    }

    public static List<Task> ready(List<Task> tasks) {
        Set<String> completed = tasks.stream().filter(task -> task.status() == TaskStatus.SUCCEEDED)
                .map(Task::id).collect(Collectors.toSet());
        return tasks.stream().filter(task -> task.status() == TaskStatus.PENDING)
                .filter(task -> completed.containsAll(task.dependencies())).toList();
    }
}
