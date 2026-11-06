# Full local entry; CI runs the same four entries as separate required jobs.
[CmdletBinding()]
param([string]$Trivy = 'trivy')
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path $PSScriptRoot -Parent
$run = Join-Path $repo ('.srf-runs/final/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $run | Out-Null
$pwsh = (Get-Process -Id $PID).Path
$entries = (Get-Content -Raw (Join-Path $repo 'scripts/srf-matrix.json') | ConvertFrom-Json -AsHashtable).entries
$savedRunId = $env:VS_SRF_RUN_ID
$runId = if ($savedRunId) { $savedRunId } else { [Guid]::NewGuid().ToString('N') }
$env:VS_SRF_RUN_ID = $runId
$results = [ordered]@{}
Push-Location $repo
try {
    foreach ($name in $entries.Keys) {
        Write-Host "Running SRF $name"
        $entry = $entries[$name]
        $summaryPath = Join-Path $repo "target/$($entry.report)/summary.json"
        if (Test-Path -LiteralPath $summaryPath) { Remove-Item -LiteralPath $summaryPath }
        $arguments = @('-NoLogo', '-NoProfile', '-File', (Join-Path $repo $entry.script))
        if ($name -eq 'security') { $arguments += @('-Trivy', $Trivy) }
        & $pwsh @arguments *> (Join-Path $run "$name.log")
        $results[$name] = if ($LASTEXITCODE -eq 0) { 'PASS' } elseif ($LASTEXITCODE -eq 2) { 'BLOCKED_ENV' } else { 'FAIL' }
        python -I scripts/publish-evidence.py (Join-Path $repo "target/$($entry.report)") (Join-Path $run $name)
        if ($LASTEXITCODE -ne 0) { $results[$name] = 'BLOCKED_ENV' }
        python -I scripts/runtime-versions.py (Join-Path $run "$name/runtime.json")
        if ($LASTEXITCODE -ne 0) { $results[$name] = 'BLOCKED_ENV' }
        Write-Host "$name : $($results[$name])"
    }
    $candidate = git rev-parse HEAD
    $results | ConvertTo-Json | Set-Content (Join-Path $run 'outcomes.json')
    python -I scripts/srf-evidence.py --root $run --candidate $candidate --run-id $runId --output (Join-Path $run 'ledger.json')
    $ledgerExit = $LASTEXITCODE
    Write-Host "Evidence: $run"
} finally { $env:VS_SRF_RUN_ID = $savedRunId; Pop-Location }
if ($results.Values -contains 'FAIL' -or $ledgerExit -eq 1) { exit 1 }
if ($results.Values -contains 'BLOCKED_ENV' -or $ledgerExit -ne 0) { exit 2 }
exit 0
