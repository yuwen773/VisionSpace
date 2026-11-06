# One discovery list for the existing browser / collaboration command entries.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path $PSScriptRoot -Parent
$run = Join-Path $repo ('.srf-runs/browser/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $run | Out-Null
$results = [ordered]@{}
$pwsh = (Get-Process -Id $PID).Path
$entries = [ordered]@{
    'https-browser' = @('scripts/browser-acceptance.ps1', 'browser-acceptance')
    'collab-protocol' = @('scripts/collab-verification/collab-dual-instance.ps1', 'collab-protocol')
    'collab-browser' = @('scripts/collab-verification/collab-browser.ps1', 'collab-browser')
}
Push-Location $repo
try {
    foreach ($name in $entries.Keys) {
        Write-Host "Running $name"
        $entry = $entries[$name]
        & $pwsh -NoLogo -NoProfile -File (Join-Path $repo $entry[0]) *> (Join-Path $run "$name.log")
        $results[$name] = if ($LASTEXITCODE -eq 0) { 'PASS' } elseif ($LASTEXITCODE -eq 2) { 'BLOCKED_ENV' } else { 'FAIL' }
        # The next driver may mvn clean; preserve this outcome before starting it.
        python -I scripts/publish-evidence.py (Join-Path $repo "target/$($entry[1])") (Join-Path $run $name)
        if ($LASTEXITCODE -ne 0) { $results[$name] = 'BLOCKED_ENV' }
    }
    Write-Host 'Running diagram-browser'
    node frontend/harness/run-ci.mjs *> (Join-Path $run 'diagram-browser.log')
    $results['diagram-browser'] = if ($LASTEXITCODE -eq 0) { 'PASS' } else { 'FAIL' }
    python -I scripts/publish-evidence.py target/diagram-browser (Join-Path $run 'diagram-browser')
    if ($LASTEXITCODE -ne 0) { $results['diagram-browser'] = 'BLOCKED_ENV' }
} finally {
    $reportDir = Join-Path $repo 'target/srf-browser'
    New-Item -ItemType Directory -Force $reportDir | Out-Null
    foreach ($name in $results.Keys) {
        if (Test-Path (Join-Path $run $name)) { Copy-Item (Join-Path $run $name) $reportDir -Recurse -Force }
    }
    @{ head = (git rev-parse HEAD); runId = $(if ($env:GITHUB_RUN_ID) { $env:GITHUB_RUN_ID } else { 'local' }); results = $results; productionAcceptance = 'NOT_RUN' } |
        ConvertTo-Json | Set-Content (Join-Path $reportDir 'summary.json')
    $results | ConvertTo-Json | Write-Host
    Pop-Location
}
if ($results.Values -contains 'FAIL') { exit 1 }
if ($results.Values -contains 'BLOCKED_ENV' -or $results.Count -ne $entries.Count + 1) { exit 2 }
exit 0
