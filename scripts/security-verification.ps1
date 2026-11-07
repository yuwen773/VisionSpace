[CmdletBinding()]
param([string]$Trivy = 'trivy')

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path $PSScriptRoot -Parent
$run = Join-Path $repo ('.srf-runs/security/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $run | Out-Null
$results = [ordered]@{ 'secret-scan' = 'BLOCKED_ENV'; dependencies = 'BLOCKED_ENV' }
$pwsh = (Get-Process -Id $PID).Path
Push-Location $repo
try {
    & $pwsh -NoLogo -NoProfile -File (Join-Path $repo 'scripts/secret-scan.ps1') *> (Join-Path $run 'secret-scan.log')
    $results['secret-scan'] = if ($LASTEXITCODE -eq 0) { 'PASS' } elseif ($LASTEXITCODE -eq 1) { 'FAIL' } else { 'BLOCKED_ENV' }
    # Resolve exact transitive Maven coordinates through the normal local/Maven mirror.
    # Scanning raw pom.xml alone silently loses inherited and managed versions.
    mvn -B --no-transfer-progress org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom "-DoutputDirectory=$run" -DincludeTestScope=true *> (Join-Path $run 'sbom.log')
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path (Join-Path $run 'bom.json'))) { throw 'BLOCKED_ENV: resolved Maven SBOM is unavailable.' }
    & $Trivy --version *> (Join-Path $run 'scanner-version.log')
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: dependency scanner unavailable.' }
    $scanCodes = @()
    & $Trivy sbom --scanners vuln --severity HIGH,CRITICAL --exit-code 1 --no-progress --timeout 5m --format json --output (Join-Path $run 'maven.json') (Join-Path $run 'bom.json') *> (Join-Path $run 'maven-scan.log')
    if (-not (Test-Path (Join-Path $run 'maven.json'))) { throw 'BLOCKED_ENV: Maven dependency scan did not produce a report.' }
    $scanCodes += $LASTEXITCODE
    & $Trivy fs --scanners vuln --include-dev-deps --severity HIGH,CRITICAL --exit-code 1 --no-progress --timeout 5m --skip-dirs node_modules --format json --output (Join-Path $run 'npm.json') (Join-Path $repo 'frontend') *> (Join-Path $run 'npm-scan.log')
    if (-not (Test-Path (Join-Path $run 'npm.json'))) { throw 'BLOCKED_ENV: npm dependency scan did not produce a report.' }
    $scanCodes += $LASTEXITCODE
    # Retain only public advisory/coordinate metadata. No raw scanner input or logs.
    $findings = foreach ($name in @('maven.json', 'npm.json')) {
        $report = Get-Content -Raw (Join-Path $run $name) | ConvertFrom-Json
        if (-not $report.SchemaVersion -or -not $report.Results) { throw 'BLOCKED_ENV: dependency report is incomplete.' }
        foreach ($target in $report.Results) {
            foreach ($vulnerability in $target.Vulnerabilities) {
                [ordered]@{ id = $vulnerability.VulnerabilityID; package = $vulnerability.PkgName; installed = $vulnerability.InstalledVersion; fixed = $vulnerability.FixedVersion; severity = $vulnerability.Severity }
            }
        }
    }
    ConvertTo-Json -InputObject @($findings) -Depth 4 | Set-Content (Join-Path $run 'advisories.json')
    $results['dependencies'] = if (@($scanCodes | Where-Object { $_ -ne 0 }).Count -eq 0) { 'PASS' } else { 'FAIL' }
} catch {
    Write-Host 'BLOCKED_ENV: security verification incomplete; raw diagnostics retained locally.'
} finally {
    $summary = @{ head = (git rev-parse HEAD); runId = $(if ($env:VS_SRF_RUN_ID) { $env:VS_SRF_RUN_ID } else { [Guid]::NewGuid().ToString('N') }); results = $results; productionAcceptance = 'NOT_RUN' }
    $reportDir = Join-Path $repo 'target/security-verification'
    New-Item -ItemType Directory -Force $reportDir | Out-Null
    $summary | ConvertTo-Json | Set-Content (Join-Path $reportDir 'summary.json')
    if (Test-Path (Join-Path $run 'advisories.json')) { Copy-Item (Join-Path $run 'advisories.json') $reportDir }
    $summary | ConvertTo-Json | Write-Host
    Pop-Location
}
if ($results.Values -contains 'FAIL') { exit 1 }
if ($results.Values -contains 'BLOCKED_ENV') { exit 2 }
exit 0
