# Testing and verification

## Current status

The dependency-JAR access issue is resolved. On September 11, 2026, the cleaned
platform passed **36 default tests and 1 real PostgreSQL integration test**, with
zero failures, errors or skips. Compilation and bootJar also passed. All nine
Gradle tasks executed against the current source; no previous test result was reused.

The initial clean command encountered a JAR locked by the running API. Verification
then used a fresh, separate output directory without stopping or changing that API:

```text
./gradlew.bat -I scripts/verification.init.gradle check postgresTest bootJar --offline
```

Use ./gradlew on Linux/macOS. Reports from this run are under
build/verification/reports/tests and build/verification/reports/jacoco/test.
[Captured evidence](evidence/test-results.json) includes the command, suite counts,
test names, report SHA-256 hashes and source-input hashes. The report artifact is
stored separately from disposable build output.

The portable scenario runner passed three local mock-API checks for request payloads,
repository selection, saved evidence and absence of automatic approvals. The
clarification payload preview also passed. The cache preparation script passed a
synthetic copy/filter check. See runner-checks.json and cache-check.json in evidence/.
These checks do not validate a live model or a populated worker dependency image.

Platform tests verify orchestration, not a generated application's acceptance.
Historical live scenario observations are in evidence/scenario-observations.md.

## Test sources included

| Suite | Intended coverage |
| --- | --- |
| WorkflowEngineTest | DAG cycles/dependencies, parallel readiness, clarification, repair bounds and state gates |
| WorkspaceServiceTest | Greenfield creation, baseline restoration, traversal, size limits and case collisions |
| AgentOutputPolicyTest | Role permissions, empty generation and oversized outputs |
| ResponsesEngineeringModelTest | Local mock HTTP contract, upstream failures, malformed/incomplete responses |
| SecurityConfigurationTest | BCrypt credential prefixes, role users and password policy |
| BuildValidatorTest | Fixed Docker controls, failure evidence and explicit demo validation |
| WorkflowIntegrationTest | H2-backed lifecycle, approvals, revisions, recovery, stale results and API authorization |
| WorkflowSchedulerTest | Concurrent independent tasks and executor capacity |
| PostgresPersistenceTest | Actual PostgreSQL migration, snapshot round-trip and rejected transaction |

H2 provides a lightweight integration test database in PostgreSQL compatibility mode;
it does not replace the PostgreSQL-specific suite. Testcontainers requires Docker.

Verification commands:

```text
./gradlew clean check
./gradlew postgresTest
```

Use gradlew.bat on Windows. JaCoCo reports go to build/reports/jacoco/test; test reports
go to build/reports/tests. CI is configured to execute both suites and build the API
image. It has not been triggered by this implementation.

## Remaining verification work

- Repeat platform verification after source changes.
- Complete application-level acceptance for all three assessment scenarios.
- Exercise actual Docker validation, timeout cleanup and filesystem permissions.
- Exercise the optional live model provider with explicitly supplied credentials.
- Review generated application quality separately from orchestration fixture behavior.

Metrics include task duration/outcomes/retries, workflow transitions/latency, audit
events, validation failures, repairs, baseline restorations and recovery duration.
Recovery duration measures the first failed validation to a later successful one
within the running process. Counters reset on restart; durable audit events remain
the source for historical reporting. Success rates can be derived from terminal
workflow transition counts with an explicitly chosen reporting window.
