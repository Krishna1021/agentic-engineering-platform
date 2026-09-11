param(
    [string]$BaseUrl = 'http://localhost:8080',
    [int]$TimeoutSeconds = 1800
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:OPERATOR_PASSWORD)) {
    throw 'Set $env:OPERATOR_PASSWORD to the password used when starting the API.'
}
$operator = @{
    Authorization = 'Basic ' + [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes("operator:$env:OPERATOR_PASSWORD"))
}
$requirements = @"
Create a production-quality URL shortener service using Java 17, Spring Boot, Gradle, and PostgreSQL.
Required capabilities:
- Create a short URL from a validated destination URL
- Redirect from the short code to the original URL
- Collision-safe short-code generation
- Persistent storage
- URL validation and clear error responses
- Unit and integration tests
- API documentation
- Health endpoint
"@
$create = @{
    Method = 'Post'
    Uri = "$($BaseUrl.TrimEnd('/'))/api/v1/workflows"
    Headers = $operator
    ContentType = 'application/json'
    Body = (@{ requirements = $requirements } | ConvertTo-Json)
}
$workflow = Invoke-RestMethod @create
$workflowUrl = "$($create.Uri)/$($workflow.id)"
Write-Host "Created workflow $($workflow.id). Polling $workflowUrl"
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
while ($workflow.status -in @('QUEUED', 'RUNNING')) {
    if ([DateTime]::UtcNow -ge $deadline) {
        throw "Polling timed out; workflow may still be running. GET $workflowUrl to resume inspection."
    }
    Start-Sleep -Seconds 2
    $workflow = Invoke-RestMethod -Uri $workflowUrl -Headers $operator
}
$workflow | ConvertTo-Json -Depth 30
Invoke-RestMethod -Uri "$workflowUrl/summary" -Headers $operator | ConvertTo-Json -Depth 30
if ($workflow.status -in @('FAILED', 'SAFE_STOPPED')) {
    throw "Workflow ended with status $($workflow.status). Inspect task errors above and API server logs."
}
if ($workflow.status -eq 'AWAITING_CLARIFICATION') {
    Write-Host 'Answer the clarification questions using the clarifications endpoint documented in docs/api.md.'
}
if ($workflow.status -eq 'AWAITING_APPROVAL') {
    Write-Host 'Generated artifacts are ready for review. Inspect validation evidence before approving.'
}
