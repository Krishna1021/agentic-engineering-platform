# Agentic Engineering Platform

A fresh requirements-first engineering platform using **Java 17, Gradle 8.14.5,
Spring Boot 3.5.16 and PostgreSQL**. It accepts requirements alone for greenfield
work, or requirements plus an optional local codebase for brownfield work.

## What is implemented

- Explicit dependency graph with cycle validation, entry/exit rules and concurrent branches.
- Requirement clarification, versioned replanning and stale-result rejection.
- Transactional checkpoints and append-only application audit history.
- Separate API, application, domain, persistence, agent and external-client components.
- Optional Responses API model adapter with structured output and bounded HTTP responses.
- Isolated revision directories, source snapshots, checked patch paths and baseline restoration.
- Docker-isolated offline Gradle validation, bounded logs and repair attempts.
- Operator/approver roles, safe-stop, explicit restart recovery and fingerprint-checked approval.
- Workflow summaries and Prometheus metrics.

No URL-shortener behavior is embedded in the platform. The deterministic `demo`
provider generates a fixed Java scaffold to demonstrate control flow; it does not
implement arbitrary requirements. Set `MODEL_PROVIDER=openai` for model generation.
Validation is independently configured: `demo` explicitly skips compilation, while
`docker` runs the fixed build capability.

## Documentation

- [Implementation plan](plan.md)
- [Setup and configuration](docs/setup.md)
- [Architecture and decisions](docs/architecture.md)
- [REST API](docs/api.md)
- [Security boundaries](docs/security.md)
- [Testing and verification status](docs/testing.md)
- [Limitations and next increments](docs/limitations.md)

## Verification status

The earlier dependency-JAR access issue has been resolved. The cleaned platform passes **36 default tests and 1 PostgreSQL test**, and its executable JAR builds successfully. See
[testing and captured evidence](docs/testing.md) for verification of the cleaned
repository, including the separate PostgreSQL suite.

All three URL-shortener assessment scenarios are defined in the
[scenario catalog](scripts/scenarios.json). The [Reviewer Guide](docs/reviewer-guide.md)
provides a cross-platform walkthrough, acceptance checks and captured historical
outcomes. Those live runs failed after generating artifacts or requesting
clarification; they are not successful autonomous application submissions.

The demo provider remains an orchestration fixture. Actual Spring Boot/PostgreSQL
validation uses a [prepared offline worker cache](docs/setup.md). One active
orchestrator is supported; production deployment is outside this prototype's scope.
