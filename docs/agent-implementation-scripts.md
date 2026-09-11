# Implementing through the platform agents

These scripts submit requirements to the existing Java platform REST API. They do not launch Codex, change the running server, deploy generated code, or automatically approve output. Running Start initiates model requests and writes generated artifacts in the server's isolated workspace. Preview sends no requests. Requirement files are proposed acceptance criteria, including explicit design choices where the assignment was vague; review them before submission.

## Prerequisites

Start the API using docs/setup.md with MODEL_PROVIDER=openai, a configured compatible model endpoint/key, and VALIDATION_MODE=docker. Set the client OPERATOR_PASSWORD to match the server. Use HTTPS outside localhost. Never include credentials in requirement files.

Actual Spring Boot validation needs a prepared BUILD_IMAGE/dependency arrangement: the worker is offline, and its command mounts an empty tmpfs over /home/gradle/.gradle. Merely warming that directory in an image will not survive that mount. The worker must be verified with its actual mounts and commands before treating runs as validated. The default image alone is not evidence that dependencies are available. Do not switch to demo mode to claim successful validation.

The existing engine executes ANALYZE -> INSPECT -> DESIGN -> IMPLEMENT -> parallel TEST and DOCUMENT -> APPLY -> VALIDATE, with bounded repair and human approval. Prompts request behavior; they cannot add capabilities to the running engine. Platform self-improvements are generated proposals that must be independently reviewed and installed before later runs can use them.

## Greenfield

Run from the platform checkout in PowerShell:

```powershell
$env:OPERATOR_PASSWORD = 'replace-with-your-server-operator-password'
./scripts/Invoke-AgentWorkflow.ps1 -Action Start -RequirementsFile ./scripts/requirements/01-greenfield.md -Preview
$run = ./scripts/Invoke-AgentWorkflow.ps1 -Action Start -RequirementsFile ./scripts/requirements/01-greenfield.md
$run.id
$run.revision
```

Output is under the server WORKSPACE_ROOT/<id>/revision-<revision>/repository. Client evidence is saved under build/agent-runs/<id>/revision-<revision>: workflow.json (including proposals), summary.json and paginated events.json. These files can contain source and requirements; keep them private. A failed or stopped run still saves its available evidence before reporting an error. A polling timeout does not stop the server's workflow.

```powershell
./scripts/Invoke-AgentWorkflow.ps1 -Action Watch -WorkflowId '<id-from-output>'
```

If creation returns a transport error, do not blindly repeat Start: the server has no idempotency key and may have accepted it. Recover its ID from the server's records first. Network errors during later actions also require reading the current state before repeating a mutation.

## Brownfield

Prepare a named input directory under the server REPOSITORY_ROOT. For a local server using ./repositories, this example stages the generated project's source, docs and build file without copying caches, IDE metadata or nested workspaces. Use a new name for each reviewed baseline; the example refuses to overwrite it.

```powershell
$source = './workspaces/<reviewed-workflow-id>/revision-<revision>/repository'
$destination = './repositories/url-shortener-baseline'
if (Test-Path -LiteralPath $destination) { throw 'Choose a fresh baseline directory.' }
New-Item -ItemType Directory -Path $destination -ErrorAction Stop | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'src') -Destination $destination -Recurse -ErrorAction Stop
Copy-Item -LiteralPath (Join-Path $source 'build.gradle') -Destination $destination -ErrorAction Stop
foreach ($name in @('settings.gradle', 'gradle.properties', 'docs', 'README.md')) {
    $item = Join-Path $source $name
    if (Test-Path -LiteralPath $item) { Copy-Item -LiteralPath $item -Destination $destination -Recurse -ErrorAction Stop }
}
./scripts/Invoke-AgentWorkflow.ps1 -Action Start -Repository url-shortener-baseline -RequirementsFile ./scripts/requirements/02-brownfield.md
```

Inspect and remove embedded credentials from the staged baseline before submitting it to an external model. For the supplied existing generated shortener, application.properties contains hardcoded database credentials; replace them with environment placeholders first. If the API is remote, stage input on that server instead. The repository argument is a relative server-side name, not a client path. Use the older shortener without analytics to demonstrate an enhancement; using the greenfield result instead exercises analytics hardening and regression preservation.

## Ambiguous requirements and clarification

Use an independent baseline copy so the scenario has a clear before/after history:

```powershell
$run = ./scripts/Invoke-AgentWorkflow.ps1 -Action Start -Repository url-shortener-baseline -RequirementsFile ./scripts/requirements/03-ambiguous.md
# Inspect the returned questions. Only continue when status is AWAITING_CLARIFICATION.
./scripts/Invoke-AgentWorkflow.ps1 -Action Clarify -WorkflowId $run.id -Revision $run.revision -AnswerFile ./scripts/requirements/03-answer.txt
```

The real model's clarification behavior is not deterministic. If it skips the required question, retain that as failed scenario evidence instead of presenting it as a successful ambiguous demonstration. The answer creates a new revision and invalidates prior work.

## Platform improvements in separate increments

Stage the platform's src, docs, build.gradle and settings.gradle into a fresh directory named platform-validation under REPOSITORY_ROOT, using the same explicit-copy pattern above. Do not copy its workspaces, repositories, build or caches. The platform only snapshots supported text formats (300 files / 2 MB total); it cannot generate PowerShell scripts, Dockerfiles or wrapper binaries. Review configuration before model submission.

```powershell
./scripts/Invoke-AgentWorkflow.ps1 -Action Start -Repository platform-validation -RequirementsFile ./scripts/requirements/04-platform-validation.md
# After reviewing the output, stage THAT output as platform-planning.
./scripts/Invoke-AgentWorkflow.ps1 -Action Start -Repository platform-planning -RequirementsFile ./scripts/requirements/05-platform-planning-governance.md
# After reviewing the next output, stage THAT output as platform-observability.
./scripts/Invoke-AgentWorkflow.ps1 -Action Start -Repository platform-observability -RequirementsFile ./scripts/requirements/06-platform-observability-summary.md
```

Do not run these dependent increments against the same unchanged baseline or merge competing generated trees automatically. Each increment must preserve the preceding changes. Review/install accepted platform code in the actual checkout and restart separately to activate it. Those installation/restart operations are not performed by these scripts. For provider output limits, split any oversized increment into smaller requirement files and run each against the preceding reviewed baseline.

## Review, approval, revision and recovery

Before approval, inspect actual test reports for nonzero executed tests and failures, compare artifacts to acceptance criteria, check schema/compatibility/security risks, and record unexecuted validations. The runner blocks demo-only approval but cannot prove that successful build text means tests actually ran; increment 04 addresses that platform gap. Approval is a separate explicit action with the server's approver identity; it does not publish or install anything.

```powershell
$env:APPROVER_PASSWORD = 'replace-with-your-server-approver-password'
./scripts/Invoke-AgentWorkflow.ps1 -Action Approve -WorkflowId '<id>' -Revision 1 -Reason 'Reviewed exact revision, artifacts and actual test evidence'
./scripts/Invoke-AgentWorkflow.ps1 -Action Revise -WorkflowId '<id>' -Revision 1 -RequirementsFile ./scripts/requirements/02-brownfield.md
./scripts/Invoke-AgentWorkflow.ps1 -Action Stop -WorkflowId '<id>' -Revision 1 -Reason 'Pause for review'
# Recover is only accepted by the server from SAFE_STOPPED.
./scripts/Invoke-AgentWorkflow.ps1 -Action Recover -WorkflowId '<id>' -Revision 1
```

These are alternative actions, not a sequence to paste together. Substitute the current revision. Revise replaces the complete requirement text. Recover starts fresh work and may incur model cost. Completed workflows require a new Start for further work. GET/summary/events collection is not a transactional snapshot; avoid concurrent human mutations while collecting final review evidence.
