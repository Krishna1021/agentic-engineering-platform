# Testing and verification

## Current status

No successful compilation or test execution is claimed. Two earlier build attempts
failed when the local runtime could not read a Spring Boot dependency JAR. The user
then explicitly requested no build commands and no pushes. Subsequent checks are
source inspection and Git whitespace checks only.

## Test sources included

| Suite | Intended coverage |
| --- | --- |
| WorkflowEngineTest | DAG cycles/dependencies, parallel readiness, clarification, repair bounds and state gates |
| WorkspaceServiceTest | Greenfield creation, baseline restoration, traversal, size limits and case collisions |
| AgentOutputPolicyTest | Role permissions, empty generation and oversized outputs |
| ResponsesEngineeringModelTest | Local mock HTTP contract, upstream failures, malformed/incomplete responses |
| BuildValidatorTest | Fixed Docker controls, failure evidence and explicit demo validation |
| WorkflowIntegrationTest | H2-backed lifecycle, approvals, revisions, recovery, stale results and API authorization |
| WorkflowSchedulerTest | Concurrent independent tasks and executor capacity |
| PostgresPersistenceTest | Actual PostgreSQL migration, snapshot round-trip and rejected transaction |

H2 provides a lightweight integration test database in PostgreSQL compatibility mode;
it does not replace the PostgreSQL-specific suite. Testcontainers requires Docker.

When builds are authorized, the intended commands are:

```text
./gradlew clean check
./gradlew postgresTest
```

Use gradlew.bat on Windows. JaCoCo reports go to build/reports/jacoco/test; test reports
go to build/reports/tests. CI is configured to execute both suites and build the API
image. It has not been triggered by this implementation.

## Remaining verification work

- Compile all sources and resolve any compiler findings.
- Execute unit and integration suites on Java 17.
- Exercise actual Docker validation, timeout cleanup and filesystem permissions.
- Exercise the optional live model provider with explicitly supplied credentials.
- Review generated application quality separately from orchestration fixture behavior.

Metrics include task duration/outcomes/retries, workflow transitions/latency, audit
events, validation failures, repairs, baseline restorations and recovery duration.
Recovery duration measures the first failed validation to a later successful one
within the running process. Counters reset on restart; durable audit events remain
the source for historical reporting. Success rates can be derived from terminal
workflow transition counts with an explicitly chosen reporting window.
