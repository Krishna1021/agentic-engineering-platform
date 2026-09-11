package io.krishna.agentic.execution;

import io.krishna.agentic.config.PlatformProperties;
import io.krishna.agentic.workflow.domain.Workflow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {
    private static final int MAX_FILES = 300;
    private static final int MAX_FILE_BYTES = 100_000;
    private static final int MAX_TOTAL_BYTES = 2_000_000;
    private static final Set<String> IGNORED = Set.of(".git", ".gradle", "build", "node_modules", ".idea");
    private final Path root;
    private final Path repositories;

    public WorkspaceService(PlatformProperties properties) {
        root = properties.workspaceRoot().toAbsolutePath().normalize();
        repositories = properties.repositoryRoot().toAbsolutePath().normalize();
    }

    public void validateRepository(String repository) {
        if (repository != null && !repository.isBlank()) {
            resolveRepository(repository);
        }
    }

    public synchronized Map<String, String> prepare(Workflow workflow) throws IOException {
        Path revision = revision(workflow);
        if (Files.exists(revision, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Revision workspace already exists; recover using a new revision");
        }
        Files.createDirectories(revision);
        Path baseline = Files.createDirectory(revision.resolve("baseline"));
        if (workflow.repository() != null && !workflow.repository().isBlank()) {
            copySources(resolveRepository(workflow.repository()), baseline);
        }
        Path repository = Files.createDirectory(revision.resolve("repository"));
        copySources(baseline, repository);
        Files.writeString(revision.resolve("baseline.sha256"), fingerprint(baseline));
        return readSources(repository);
    }

    public synchronized String apply(Workflow workflow, Map<String, String> proposals) throws IOException {
        validateProposals(proposals);
        Path revision = revision(workflow);
        Path baseline = revision.resolve("baseline");
        if (!Files.readString(revision.resolve("baseline.sha256")).equals(fingerprint(baseline))) {
            throw new IOException("Baseline integrity check failed");
        }
        Path repository = repository(workflow);
        Path staging = Files.createTempDirectory(revision, "proposal-");
        copySources(baseline, staging);
        Map<String, String> before = readSources(baseline);
        StringBuilder evidence = new StringBuilder("Patch manifest (SHA-256 before -> after):\n");
        for (Map.Entry<String, String> proposal : proposals.entrySet()) {
            Path target = staging.resolve(proposal.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, proposal.getValue(), StandardCharsets.UTF_8);
            evidence.append(proposal.getKey()).append(": ")
                    .append(before.containsKey(proposal.getKey()) ? hash(before.get(proposal.getKey())) : "NEW")
                    .append(" -> ").append(hash(proposal.getValue())).append('\n');
        }
        // Keep the previous tree until the staged baseline + full proposal is installed.
        Path backup = revision.resolve("previous");
        deleteTree(backup);
        Files.move(repository, backup, StandardCopyOption.ATOMIC_MOVE);
        try {
            Files.move(staging, repository, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(backup, repository, StandardCopyOption.ATOMIC_MOVE);
            throw exception;
        }
        Files.writeString(revision.resolve("patch-manifest.txt"), evidence, StandardCharsets.UTF_8);
        return evidence.toString();
    }

    public Path repository(Workflow workflow) throws IOException {
        Path path = revision(workflow).resolve("repository");
        rejectLinks(path);
        return path;
    }

    public synchronized void sealValidation(Workflow workflow) throws IOException {
        Files.writeString(revision(workflow).resolve("validation.sha256"), fingerprint(repository(workflow)));
    }

    public synchronized boolean validationUnchanged(Workflow workflow) throws IOException {
        Path seal = revision(workflow).resolve("validation.sha256");
        return Files.exists(seal) && Files.readString(seal).equals(fingerprint(repository(workflow)));
    }

    private static String fingerprint(Path directory) throws IOException {
        StringBuilder content = new StringBuilder();
        readSources(directory).forEach((path, text) -> content.append(path).append(':').append(hash(text)).append('\n'));
        return hash(content.toString());
    }

    private Path revision(Workflow workflow) throws IOException {
        rejectLinks(root);
        Path path = root.resolve(workflow.id().toString()).resolve("revision-" + workflow.revision());
        rejectLinks(path);
        return path;
    }

    private Path resolveRepository(String supplied) {
        if (!supplied.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*")) {
            throw new IllegalArgumentException("Repository must be a relative directory under REPOSITORY_ROOT");
        }
        Path path = repositories.resolve(supplied).normalize();
        try {
            rejectLinks(path);
            if (!path.startsWith(repositories) || !Files.isDirectory(path)) {
                throw new IllegalArgumentException("Repository directory does not exist");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Repository contains an unsafe path", exception);
        }
        return path;
    }

    private static void validateProposals(Map<String, String> proposals) {
        if (proposals.isEmpty() || proposals.size() > MAX_FILES) {
            throw new IllegalArgumentException("Proposal must contain 1 to " + MAX_FILES + " files");
        }
        int bytes = 0;
        Set<String> foldedPaths = new java.util.HashSet<>();
        for (Map.Entry<String, String> file : proposals.entrySet()) {
            String path = file.getKey();
            if (!path.matches("[a-zA-Z0-9_-]+(?:[./][a-zA-Z0-9_-]+)*") || path.contains("..")
                    || !allowed(path) || !foldedPaths.add(path.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Unsupported or duplicate proposal path: " + path);
            }
            int size = file.getValue().getBytes(StandardCharsets.UTF_8).length;
            bytes += size;
            if (size > MAX_FILE_BYTES || bytes > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Proposal exceeds file or total size limit");
            }
        }
    }

    private static boolean allowed(String path) {
        return path.endsWith(".java") || path.endsWith(".gradle") || path.endsWith(".gradle.kts")
                || path.endsWith(".md") || path.endsWith(".json") || path.endsWith(".yml")
                || path.endsWith(".yaml") || path.endsWith(".properties") || path.endsWith(".sql");
    }

    private static Map<String, String> readSources(Path directory) throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        int total = 0;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted().toList()) {
                rejectLinks(path);
                String relative = directory.relativize(path).toString().replace('\\', '/');
                boolean ignored = Stream.of(relative.split("/")).anyMatch(IGNORED::contains);
                if (ignored || !Files.isRegularFile(path) || !allowed(relative)) {
                    continue;
                }
                long size = Files.size(path);
                if (size > MAX_FILE_BYTES || total + size > MAX_TOTAL_BYTES || sources.size() >= MAX_FILES) {
                    throw new IOException("Repository exceeds bounded source context limits");
                }
                total += (int) size;
                sources.put(relative, Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    private static void copySources(Path source, Path destination) throws IOException {
        for (Map.Entry<String, String> file : readSources(source).entrySet()) {
            Path target = destination.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
    }

    private void deleteTree(Path path) throws IOException {
        if (!path.normalize().startsWith(root) || path.equals(root)) {
            throw new IOException("Refusing deletion outside revision workspace");
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        rejectLinks(path);
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(item);
            }
        }
    }

    private static void rejectLinks(Path path) throws IOException {
        for (Path current = path.toAbsolutePath(); current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not supported");
            }
        }
    }

    private static String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
