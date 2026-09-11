# Setup

## Prerequisites

- JDK 17 with JAVA_HOME set to its installation directory.
- PostgreSQL 17, or Docker Desktop/Engine to run the supplied database service.
- Docker Engine with Linux containers for real generated-code validation.
- Gradle Wrapper is committed; a global Gradle installation is unnecessary.

The following commands are instructions for a future authorized run. They have not
been run successfully for this handoff, and should not be interpreted as verification.

## Local configuration

Copy `.env.example` to `.env` and choose three distinct passwords. Spring Boot does
not automatically load `.env`; export the variables in the shell that launches the
application. Docker Compose reads `.env` itself.

Required variables: `DATABASE_PASSWORD`, `OPERATOR_PASSWORD`, `APPROVER_PASSWORD`.
Operator and approver passwords must differ and have at least 12 characters.
The fixed local usernames are `operator` and `approver`.

```powershell
$env:DATABASE_PASSWORD = 'your-local-database-password'
$env:OPERATOR_PASSWORD = 'your-distinct-operator-password'
$env:APPROVER_PASSWORD = 'your-distinct-approver-password'
docker compose up -d postgres
./gradlew.bat bootRun
```

On Linux/macOS use `export NAME=value` and `./gradlew bootRun`. Configure a writable
`WORKSPACE_ROOT`; point `REPOSITORY_ROOT` at trusted local source projects. Repository
paths in API requests are relative to this root. Greenfield requests omit repository.

## Modes

| Setting | Meaning |
| --- | --- |
| MODEL_PROVIDER=demo | Fixed scaffold for repeatable orchestration demonstrations |
| MODEL_PROVIDER=openai | Structured model generation; also requires MODEL_NAME and MODEL_API_KEY |
| VALIDATION_MODE=docker | Actual offline Gradle invocation in an isolated Linux container (default) |
| VALIDATION_MODE=demo | Explicitly records that compilation/tests were NOT executed |

The model name and API key are supplied by the operator through `MODEL_NAME` and
`MODEL_API_KEY`. The adapter uses the Responses endpoint and requires a model
supporting structured JSON-schema outputs. A live paid request has not been
performed. Never put credentials in source control, logs, chat history or request
bodies. Revoke any exposed key and create a replacement.

For a containerized orchestration-only demo, `docker compose --profile demo up --build`
starts PostgreSQL and the API with both demo modes. This profile does not execute
generated builds and does not expose the Docker socket inside the API container.

## Real build execution

Run the API on the host that owns the local Docker daemon. Pre-pull the configured
`BUILD_IMAGE` (default `gradle:8.14.5-jdk17`). The daemon must see the same workspace
path as the API. Remote Docker daemons and arbitrary Docker-in-Docker paths are not
supported. On Linux the container UID 1000 needs write permission to the revision
repository; run the API under that UID or arrange a dedicated compatible workspace.

The worker has no network, no extra capabilities, bounded memory/CPU/process count,
and a read-only root filesystem. It executes `gradle --offline ... clean test`.
Dependency-free Gradle projects work without external caches. Dependency-rich
projects require a separately prepared build image/cache strategy; this version
does not fetch arbitrary dependencies on the worker's behalf.

## Health and operations

`GET /actuator/health` is public. `/actuator/prometheus` requires authentication.
Use one orchestrator process per database. Keep the database and workspace volume
together when backing up/restoring. A restart safe-stops interrupted active tasks;
inspect the run and use the recovery endpoint to start a fresh revision.
