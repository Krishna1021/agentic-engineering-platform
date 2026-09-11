[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Start', 'Watch', 'Clarify', 'Revise', 'Stop', 'Recover', 'Approve')]
    [string]$Action,
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$RequirementsFile,
    [string]$Repository,
    [guid]$WorkflowId = [guid]::Empty,
    [int]$Revision = 0,
    [string]$AnswerFile,
    [string]$Reason,
    [ValidateRange(1, 86400)][int]$TimeoutSeconds = 1800,
    [ValidateRange(1, 60)][int]$PollSeconds = 2,
    [string]$EvidenceRoot = (Join-Path $PSScriptRoot '../build/agent-runs'),
    [switch]$Preview
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-InputText([string]$Path, [int]$Limit) {
    if ([string]::IsNullOrWhiteSpace($Path)) { throw 'A text file path is required.' }
    $value = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -gt $Limit) {
        throw "Input must contain 1 to $Limit characters: $Path"
    }
    return $value
}

$base = $BaseUrl.TrimEnd('/')
$uri = [uri]$base
if (-not $uri.IsAbsoluteUri -or $uri.Scheme -notin @('http', 'https') -or $uri.UserInfo) {
    throw 'BaseUrl must be an HTTP(S) URL without embedded credentials.'
}
if ($uri.Scheme -eq 'http' -and -not $uri.IsLoopback) {
    throw 'Use HTTPS when sending credentials outside localhost.'
}
if ($Action -ne 'Start' -and $WorkflowId -eq [guid]::Empty) {
    throw 'WorkflowId is required for this action.'
}
if ($Action -notin @('Start', 'Watch') -and $Revision -lt 1) {
    throw 'Supply the exact reviewed Revision for this action.'
}
if ($Repository -and $Action -ne 'Start') { throw 'Repository is only valid for Start.' }

$body = $null
$suffix = ''
switch ($Action) {
    'Start' {
        $body = @{ requirements = Read-InputText $RequirementsFile 20000 }
        if ($Repository) {
            if ($Repository.Length -gt 200 -or $Repository -notmatch '^[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*$') {
                throw 'Repository must be a relative directory under the server REPOSITORY_ROOT.'
            }
            $body.repository = $Repository
        }
    }
    'Clarify' { $suffix = '/clarifications'; $body = @{ revision = $Revision; answer = Read-InputText $AnswerFile 5000 } }
    'Revise' { $suffix = '/revisions'; $body = @{ revision = $Revision; requirements = Read-InputText $RequirementsFile 20000 } }
    'Recover' { $suffix = '/recover'; $body = @{ revision = $Revision } }
    { $_ -in @('Stop', 'Approve') } {
        if ([string]::IsNullOrWhiteSpace($Reason) -or $Reason.Length -gt 1000) {
            throw 'Reason must contain 1 to 1000 characters.'
        }
        $suffix = if ($Action -eq 'Stop') { '/stop' } else { '/approvals' }
        $body = @{ revision = $Revision; reason = $Reason }
    }
}
$collection = "$base/api/v1/workflows"
$target = if ($Action -eq 'Start') { $collection } else { "$collection/$WorkflowId$suffix" }
if ($Preview) {
    [pscustomobject]@{ method = $(if ($Action -eq 'Watch') { 'GET' } else { 'POST' }); uri = $target; body = $body } |
        ConvertTo-Json -Depth 10
    return
}

$username = if ($Action -eq 'Approve') { 'approver' } else { 'operator' }
$password = if ($Action -eq 'Approve') { $env:APPROVER_PASSWORD } else { $env:OPERATOR_PASSWORD }
if ([string]::IsNullOrWhiteSpace($password)) {
    throw "Set the $($username.ToUpperInvariant())_PASSWORD environment variable."
}
$headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${username}:$password")) }

function Invoke-WorkflowApi([string]$Method, [string]$Url, $Payload = $null) {
    $request = @{ Method = $Method; Uri = $Url; Headers = $headers; TimeoutSec = 30; MaximumRedirection = 0 }
    if ($null -ne $Payload) {
        $request.ContentType = 'application/json; charset=utf-8'
        $request.Body = [Text.Encoding]::UTF8.GetBytes(($Payload | ConvertTo-Json -Depth 10 -Compress))
    }
    # Mutations are deliberately not retried: creation is not idempotent.
    Invoke-RestMethod @request
}

if ($Action -eq 'Approve') {
    $current = Invoke-WorkflowApi 'Get' "$collection/$WorkflowId"
    if ($current.revision -ne $Revision -or $current.status -ne 'AWAITING_APPROVAL') {
        throw 'Approval requires the current revision in AWAITING_APPROVAL.'
    }
    $validations = @($current.tasks | Where-Object { $_.kind -eq 'VALIDATE' -and $_.status -eq 'SUCCEEDED' })
    if ($validations.Count -eq 0 -or -not $validations[-1].output.passed -or
        $validations[-1].output.summary -match 'DEMO_ONLY|NOT executed') {
        throw 'This runner requires actual successful validation before approval.'
    }
    # This is only a basic evidence check. Human review must confirm actual tests ran.
}

$workflow = if ($Action -eq 'Watch') { Invoke-WorkflowApi 'Get' $target } else { Invoke-WorkflowApi 'Post' $target $body }
$WorkflowId = [guid]$workflow.id
$workflowUrl = "$collection/$WorkflowId"
$directory = Join-Path $EvidenceRoot "$WorkflowId/revision-$($workflow.revision)"
New-Item -ItemType Directory -Path $directory -Force | Out-Null
function Save-Evidence([string]$Name, $Value) {
    ConvertTo-Json -InputObject $Value -Depth 100 | Set-Content -LiteralPath (Join-Path $directory $Name) -Encoding UTF8
}
Save-Evidence 'workflow.json' $workflow
Write-Host "Workflow: $WorkflowId; revision: $($workflow.revision); evidence: $directory"
Write-Host "Resume: ./scripts/Invoke-AgentWorkflow.ps1 -Action Watch -BaseUrl '$base' -WorkflowId $WorkflowId"
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$lastStatus = ''
while ($true) {
    if ($workflow.status -ne $lastStatus) { Write-Host "Status: $($workflow.status)"; $lastStatus = $workflow.status }
    Save-Evidence 'workflow.json' $workflow
    if ($workflow.status -notin @('QUEUED', 'RUNNING')) { break }
    if ([DateTime]::UtcNow -ge $deadline) { throw "Polling timed out. Workflow $WorkflowId may still be running; use Watch to resume." }
    Start-Sleep -Seconds $PollSeconds
    $workflow = Invoke-WorkflowApi 'Get' $workflowUrl
    # A human may revise the workflow while it is being watched.
    $directory = Join-Path $EvidenceRoot "$WorkflowId/revision-$($workflow.revision)"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
Save-Evidence 'summary.json' (Invoke-WorkflowApi 'Get' "$workflowUrl/summary")
$events = [System.Collections.Generic.List[object]]::new()
[long]$cursor = 0
do {
    $page = @(Invoke-WorkflowApi 'Get' "$workflowUrl/events?after=$cursor")
    foreach ($event in $page) { $events.Add($event) }
    if ($page.Count -gt 0) {
        [long]$next = $page[-1].sequence
        if ($next -le $cursor) { throw 'Audit pagination did not advance.' }
        $cursor = $next
    }
} while ($page.Count -eq 200)
Save-Evidence 'events.json' @($events.ToArray())
if ($workflow.status -eq 'AWAITING_CLARIFICATION') {
    foreach ($task in $workflow.tasks) {
        if ($null -ne $task.output) { foreach ($question in $task.output.questions) { Write-Host "Clarification: $question" } }
    }
}
if ($workflow.status -eq 'AWAITING_APPROVAL') { Write-Host 'Review source, actual test reports, summary and audit evidence before using Approve.' }
if ($workflow.status -eq 'FAILED' -or ($workflow.status -eq 'SAFE_STOPPED' -and $Action -ne 'Stop')) { throw "Workflow ended in $($workflow.status). Inspect $directory/workflow.json before revising or recovering." }
$workflow
