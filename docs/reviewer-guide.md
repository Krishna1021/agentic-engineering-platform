# Reviewer Guide

## Build and evidence

The dependency-JAR access issue reported in the original feedback is resolved.
Current platform verification is recorded in [testing.md](testing.md), with suite
counts, test names and report hashes in [evidence](evidence/test-results.json).
Passing platform tests verify orchestration; they do not prove that a generated
URL-shortener application meets its acceptance criteria.

## Scenario catalog

The executable catalog is [scripts/scenarios.json](../scripts/scenarios.json).
It points to the full requirements and clarification answer already in this repo.

| Scenario | Input | Reviewer checks |
| --- | --- | --- |
| Greenfield | 01-greenfield.md; no repository | Creation, redirect/404, URL validation, atomic collision handling, PostgreSQL persistence, UTC analytics, actual executed tests and API docs |
| Brownfield | 02-brownfield.md; trusted URL-shortener baseline | Existing endpoint compatibility, additive migration, preserved mappings, collision fixes, atomic analytics and regression tests |
| Ambiguous | 03-ambiguous.md; independent baseline | Pause before implementation for aggregation/privacy choices; answer from 03-answer.txt creates a new revision; only agreed behavior implemented |

[Historical observed output](evidence/scenario-observations.md) includes all three
runs and their failures. The ambiguous run demonstrably paused for clarification.
None of those runs is claimed as a successfully validated, approved application.
A working URL-shortener assessment remains an end-to-end acceptance requirement.

## Walkthrough on Windows, Linux or macOS

Start the API and prepared worker using [setup.md](setup.md). For actual generation,
use MODEL_PROVIDER=openai with the configured provider's credentials, and
VALIDATION_MODE=docker. The demo model generates a fixed Java scaffold independent
of the requirement; demo validation skips compilation. Neither is application proof.

Node.js 18+ is the only dependency of the portable runner. Export OPERATOR_PASSWORD
in the client shell (PowerShell uses `$env:OPERATOR_PASSWORD`). PLATFORM_URL defaults
to http://localhost:8080. Preview requires no credentials and sends no requests:

```sh
node scripts/run-scenario.mjs greenfield --preview
node scripts/run-scenario.mjs greenfield
node scripts/run-scenario.mjs brownfield --repository=url-shortener-baseline
node scripts/run-scenario.mjs ambiguous --repository=url-shortener-baseline
```

For brownfield/ambiguous, stage a reviewed source-only baseline under the server's
REPOSITORY_ROOT/url-shortener-baseline first. Preserve its original files for a
before/after comparison. Use an existing shortener without analytics to demonstrate
an enhancement; using one with analytics demonstrates correction/regression work.
See agent-implementation-scripts.md for explicit source-copy instructions.

Inspect the ambiguous run's questions, then submit the supplied business decision:

```sh
node scripts/run-scenario.mjs ambiguous --clarify=WORKFLOW-ID:REVISION
```

Replace WORKFLOW-ID and REVISION with the exact returned values. Clarification
invalidates the prior revision. The runner saves workflow.json, summary.json and
paginated events.json in build/agent-runs/<id>/revision-<revision>. Failed runs return
nonzero status; polling timeout does not cancel the server. No mutation is retried.
Copy reviewed evidence out of build before invoking Gradle clean.

## Acceptance gate

Inspect actual Gradle XML/HTML reports for nonzero executed tests, failures and skips.
A successful process or SUCCEEDED validation task alone does not establish coverage.
Exercise PostgreSQL separately from offline default tests; do not run nested Docker
inside the isolated worker. Review generated API/schema, collision concurrency,
restart persistence, migration compatibility, analytics privacy and limitations.
Record baseline identity, model/version, workflow/revision and report provenance.
Then use the approver endpoint with the current revision as documented in api.md.
The portable runner never approves. Existing demo-mode approval behavior represents
control-flow completion only and must not be presented as application acceptance.

## Prototype scale boundary

One active orchestrator owns each database/workspace set. Startup recovery assumes
old workers are gone. Multiple replicas require ownership leases and fencing before
safe horizontal scaling. This is a documented prototype boundary, not a scale claim.
