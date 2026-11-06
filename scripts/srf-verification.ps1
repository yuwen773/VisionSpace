# Full local entry; CI runs the same four entries as separate required jobs.
[CmdletBinding()]
param([string]$Trivy = 'trivy')
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path $PSScriptRoot -Parent
$run = Join-Path $repo ('.srf-runs/final/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $run | Out-Null
$pwsh = (Get-Process -Id $PID).Path
$entries = [ordered]@{
    acceptance = @('scripts/acceptance.ps1', 'acceptance')
    browser = @('scripts/srf-browser.ps1', 'srf-browser')
    container = @('scripts/container-verification.ps1', 'container-verification')
    security = @('scripts/security-verification.ps1', 'security-verification')
}
$results = [ordered]@{}
Push-Location $repo
try {
    foreach ($name in $entries.Keys) {
        Write-Host "Running SRF $name"
        $entry = $entries[$name]
        $arguments = @('-NoLogo', '-NoProfile', '-File', (Join-Path $repo $entry[0]))
        if ($name -eq 'security') { $arguments += @('-Trivy', $Trivy) }
        & $pwsh @arguments *> (Join-Path $run "$name.log")
        $results[$name] = if ($LASTEXITCODE -eq 0) { 'PASS' } elseif ($LASTEXITCODE -eq 2) { 'BLOCKED_ENV' } else { 'FAIL' }
        python -I scripts/publish-evidence.py (Join-Path $repo "target/$($entry[1])") (Join-Path $run $name)
        if ($LASTEXITCODE -ne 0) { $results[$name] = 'BLOCKED_ENV' }
        python -I scripts/runtime-versions.py (Join-Path $run "$name/runtime.json")
        if ($LASTEXITCODE -ne 0) { $results[$name] = 'BLOCKED_ENV' }
        Write-Host "$name : $($results[$name])"
    }
    $candidate = git rev-parse HEAD
    $runId = if ($env:GITHUB_RUN_ID) { $env:GITHUB_RUN_ID } else { 'local' }
    python -I scripts/srf-evidence.py --root $run --candidate $candidate --run-id $runId --output (Join-Path $run 'ledger.json')
    $ledgerExit = $LASTEXITCODE
    Write-Host "Evidence: $run"
} finally { Pop-Location }
if ($results.Values -contains 'FAIL' -or $ledgerExit -eq 1) { exit 1 }
if ($results.Values -contains 'BLOCKED_ENV' -or $ledgerExit -ne 0) { exit 2 }
exit 0
