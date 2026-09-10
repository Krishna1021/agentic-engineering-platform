# Implementation plan

## Product boundary

Build a fresh Java 17 / Gradle engineering platform. Requirements are mandatory;
an existing local codebase is optional. No application domain is embedded in the
platform. The reference repository informed capabilities, not source structure.

## Delivery increments

1. Bootstrap reproducible build and document architectural boundaries.
2. Implement immutable workflow/task models, dependency validation and state rules.
3. Add transactional persistence, audit events, revision handling and recovery.
4. Add agent contracts, requirement interpretation, context and isolated execution.
5. Expose authenticated workflow operations and asynchronous scheduling.
6. Verify failure paths, publish API/setup/security documentation and CI.

## Acceptance criteria

- Requirement-only submission creates an isolated greenfield workspace.
- Brownfield input is restricted to an operator-configured local repository root.
- Explicit DAG supports concurrent ready branches and synchronization.
- Clarification pauses execution; revised requirements create a fresh revision.
- Durable task attempts and outputs survive restarts; interrupted effects stop for review.
- Model proposals cannot choose commands, paths outside the workspace, or approvals.
- Failed validation invokes bounded repair with baseline restoration.
- Approval is authenticated and bound to the validated revision.
- Tests cover successful work, invalid inputs, conflicts, exceptions and recovery.

## Scope decisions

Start with one orchestrator instance, PostgreSQL state, and Docker-isolated Gradle
validation. A deterministic provider is explicitly an offline orchestration demo.
An optional Responses API adapter supplies actual requirement-driven generation.
Git publishing and production deployment of generated projects remain manual.
URL-shortener assessment scenarios are a later application-level increment.
