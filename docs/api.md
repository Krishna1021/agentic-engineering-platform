# REST API

The machine-readable contract is [openapi.yaml](openapi.yaml). It is provided as a
source artifact; automatic Swagger UI hosting is not included.

Base path: `/api/v1/workflows`. JSON requests only. HTTP Basic authentication is
required. Operators create, revise, clarify, stop and recover. Approvers approve.
Both roles can read workflows, summaries, audit events and metrics.

| Method | Path | Body | Result |
| --- | --- | --- | --- |
| POST | /api/v1/workflows | requirements, optional repository | 201 + Location; asynchronous workflow |
| GET | /api/v1/workflows/{id} | none | Current revision, status, graph, outputs and errors |
| GET | /api/v1/workflows/{id}/summary | none | Plan, proposed files, validation evidence, limitations |
| GET | /api/v1/workflows/{id}/events?after=0 | none | Up to 200 ordered audit events; use last sequence as cursor |
| POST | /api/v1/workflows/{id}/clarifications | revision, answer | New revision after clarification pause |
| POST | /api/v1/workflows/{id}/revisions | revision, requirements | Replan with replacement requirements |
| POST | /api/v1/workflows/{id}/approvals | revision, reason | Complete reviewed current revision |
| POST | /api/v1/workflows/{id}/stop | revision, reason | Safe-stop workflow |
| POST | /api/v1/workflows/{id}/recover | revision | Fresh revision from a safe-stopped run |

Create a greenfield run:

```json
{"requirements":"Create a Java command-line application with a ready status and executable tests"}
```

Create a brownfield run (the directory must exist under REPOSITORY_ROOT):

```json
{"requirements":"Add documented validation and regression tests to the existing service","repository":"existing-service"}
```

Clarify an ambiguous request:

```json
{"revision":1,"answer":"Track daily UTC counts and expose them through a read-only endpoint"}
```

Approve after inspecting source proposals, workspace manifest and validation logs:

```json
{"revision":2,"reason":"Reviewed the current source and validation evidence"}
```

Request limits: requirements 1-20000 characters, repository at most 200 characters,
clarification 1-5000 characters, decision reason 1-1000 characters, revision >= 1.
Unknown fields and malformed values are rejected. Actor identity comes from the
authenticated principal, never from a request-body actor field.

Statuses: QUEUED, RUNNING, AWAITING_CLARIFICATION, AWAITING_APPROVAL, COMPLETED,
FAILED and SAFE_STOPPED. Poll GET until a pause or terminal status is returned.

Errors use problem JSON for 400 (invalid request), 404 (unknown workflow), and 409
(stale revision, wrong state, or changed validation evidence). Authentication and
authorization failures return 401/403. Internal exceptions do not expose stack
traces or upstream response bodies.

The summary's changedFiles list describes proposed paths. APPLY's manifest records
before/after hashes; task outputs retain complete proposed file contents. A failed
or stopped run can contain unapplied proposals. This API does not claim a Git diff.
