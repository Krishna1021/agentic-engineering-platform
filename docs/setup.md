# Setup

## Prerequisites

- JDK 17 with JAVA_HOME set to its installation directory.
- PostgreSQL 17, or Docker Desktop/Engine to run the supplied database service.
- Docker Engine with Linux containers for real generated-code validation.
- Gradle Wrapper is committed; a global Gradle installation is unnecessary.

See testing.md for current platform verification and reviewer-guide.md for scenario outcomes.

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
supporting structured JSON-schema outputs. Historical live requests produced artifacts and failures; see reviewer-guide.md. Never put credentials in source control, logs, chat history or request
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

## URL shortener from PowerShell

Start the API with `MODEL_PROVIDER=openai`, `MODEL_NAME` set to a model supporting
structured Responses output, and `MODEL_API_KEY` set to credentials for the chosen
endpoint. `MODEL_ENDPOINT` defaults to `https://api.openai.com/v1/responses`.
If using another compatible provider, set its Responses endpoint and use that
provider's API key and model name together. Restart the API after configuration changes.
For larger generated services, `MODEL_TIMEOUT=PT180S` and
`MODEL_MAX_OUTPUT_TOKENS=16000` can increase the generation budget if supported by
the selected model. These settings now control the actual model request.

In the client PowerShell session:

```powershell
$env:OPERATOR_PASSWORD = 'operator-password-12' # must match the API server
./scripts/create-url-shortener.ps1
```

The script submits the URL shortener requirements, polls the asynchronous workflow,
and displays artifacts, task errors and validation evidence. Do not paste Markdown
quote markers (`>`), escaped underscores (`\_`) or Markdown links into PowerShell.
The script uses parameter splatting to avoid fragile backtick line continuations.
A 401 means the client password does not match the API server configuration.

`MODEL_PROVIDER=demo` only generates the fixed scaffold. Spring Boot/PostgreSQL
validation requires a prepared build image containing the requested dependencies:
the Docker validator runs offline. `VALIDATION_MODE=demo` skips tests and must not
be treated as production validation. Generated files reside below
`WORKSPACE_ROOT/<workflow-id>/revision-<revision>/repository` after APPLY succeeds.

## Dependency-cache worker

The supplied worker/Dockerfile reads dependencies from /opt/gradle-readonly using
GRADLE_RO_DEP_CACHE. This is outside /home/gradle/.gradle, so the validator's empty
writable tmpfs does not hide the prepared dependencies.

Prepare the target project's dependencies with Gradle 8.14.5 on a trusted machine
that has network access. Run its default test suite and any separately selected
PostgreSQL suite on the host. Stop Gradle daemons before copying the cache. Copy
only the resulting caches/modules-2 tree to build/worker-context/cache/modules-2,
excluding *.lock and gc.properties. Do not include Gradle properties, init scripts
or credentials. The portable preparation script copies only modules-2, excludes
lock/GC files and symlinks, and refuses to mix with an existing cache context:

```sh
node scripts/prepare-worker-cache.mjs /trusted/gradle-home/caches/modules-2
```

On Windows pass the quoted full path to your Gradle cache. If the preparation fails,
inspect its partial context and use a fresh context for the next attempt. Then run
from this checkout:

```sh
docker build -f worker/Dockerfile -t agentic-worker:dependency-cache build/worker-context
```

Set BUILD_IMAGE=agentic-worker:dependency-cache and VALIDATION_MODE=docker on the
API, then restart it. The image is specific to the cached plugins/dependency
versions. A new dependency requires rebuilding the cache; offline workers do not
fetch arbitrary packages. Validate with the actual Docker mounts and restrictions
before claiming the image supports a target application. Default generated tests
must avoid nested Docker; run real PostgreSQL tests separately on the host.

## Cross-platform assessment walkthrough

Use Node.js 18+ and scripts/run-scenario.mjs on Windows, Linux or macOS. The
[Reviewer Guide](reviewer-guide.md) covers all three scenarios, baseline staging,
clarification, evidence capture and manual acceptance. The existing PowerShell
runner remains available for Watch, Revise, Stop, Recover and explicit approval.


If a running API locks build/libs during clean, use the isolated verification command in testing.md. Its output is build/verification; it does not replace the running JAR.
