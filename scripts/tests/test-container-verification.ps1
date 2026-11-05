[CmdletBinding()]
param([switch]$Docker)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$sourcePath = Join-Path $PSScriptRoot '../container-verification.ps1'
$source = Get-Content -Raw -LiteralPath $sourcePath
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($sourcePath, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Container verification script has syntax errors.' }

# Load actual functions and startup scenarios without running build/up or reading secrets.
foreach ($definition in $ast.FindAll({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst]
}, $false)) {
    . ([scriptblock]::Create($definition.Extent.Text))
}
$lateStart = $source.IndexOf('# 依赖延迟启动：')
$exhaustStart = $source.IndexOf('# 重试耗尽：')
$summary = $source.IndexOf('    Write-Host "=== summary')
if ($lateStart -lt 0 -or $exhaustStart -le $lateStart -or $summary -le $exhaustStart) {
    throw 'Startup scenario boundaries changed; review the regression harness.'
}
$lateScenario = [scriptblock]::Create($source.Substring($lateStart, $exhaustStart - $lateStart))
$exhaustScenario = [scriptblock]::Create($source.Substring($exhaustStart, $summary - $exhaustStart))

if ($Docker) {
    # Real Docker metadata and lifecycle, with lightweight stand-ins rather than the business image.
    $project = 'vs45-startup-test-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
    $runDir = Join-Path ([IO.Path]::GetTempPath()) $project
    New-Item -ItemType Directory -Path $runDir | Out-Null
    $composeYml = Join-Path $runDir 'compose.yml'
    $envFile = Join-Path $runDir 'empty.env'
    $results = [ordered]@{}
    try {
        docker info *> $null
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: docker daemon unavailable' }
        [IO.File]::WriteAllText($envFile, '')
        $dependencyScript = @'
gate="/fixture/$1.ready"
touch "$gate"
trap 'rm -f "$gate"; exit 0' TERM INT
while :; do sleep 1 & wait $!; done
'@
        $backendScript = @'
rm -f /fixture/backend.ready
trap 'rm -f /fixture/backend.ready; exit 0' TERM INT
echo 'HikariPool-1 - Starting...'
attempt=0
while [ ! -f /fixture/mysql.ready ] || [ ! -f /fixture/redis.ready ]; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 25 ]; then exit 17; fi
    sleep 1 & wait $!
done
touch /fixture/backend.ready
while :; do sleep 1 & wait $!; done
'@
        [IO.File]::WriteAllText((Join-Path $runDir 'dependency.sh'), $dependencyScript.Replace("`r`n", "`n"))
        [IO.File]::WriteAllText((Join-Path $runDir 'backend.sh'), $backendScript.Replace("`r`n", "`n"))
        $mountPath = $runDir.Replace('\', '/')
        @"
x-fixture: &fixture
  image: ubuntu:22.04
  pull_policy: never
  network_mode: none
  volumes:
    - "${mountPath}:/fixture"
  healthcheck: &healthcheck
    interval: 1s
    timeout: 1s
    retries: 2
services:
  mysql:
    <<: *fixture
    command: ["sh", "/fixture/dependency.sh", "mysql"]
    healthcheck:
      <<: *healthcheck
      test: ["CMD", "test", "-f", "/fixture/mysql.ready"]
  redis:
    <<: *fixture
    command: ["sh", "/fixture/dependency.sh", "redis"]
    healthcheck:
      <<: *healthcheck
      test: ["CMD", "test", "-f", "/fixture/redis.ready"]
  backend:
    <<: *fixture
    command: ["sh", "/fixture/backend.sh"]
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      <<: *healthcheck
      test: ["CMD-SHELL", "test -f /fixture/backend.ready && test -f /fixture/mysql.ready && test -f /fixture/redis.ready"]
"@ | Set-Content -LiteralPath $composeYml
        Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '30') *> (Join-Path $runDir 'fixture-up.log')
        . $lateScenario
        . $exhaustScenario
        if ($results.Count -ne 9 -or @($results.Values | Where-Object { $_ -ne 'PASS' }).Count) {
            throw 'Docker startup scenario checks failed.'
        }
        Write-Host 'PASS: nine Docker lifecycle assertions using stand-ins; full business acceptance remains BLOCKED_ENV'
    } finally {
        if (Test-Path -LiteralPath $composeYml) {
            & docker compose -f $composeYml -p $project --env-file $envFile down *> (Join-Path $runDir 'fixture-down.log')
            if ($LASTEXITCODE -ne 0) { throw "Fixture cleanup failed; artifacts retained at $runDir" }
        }
        Get-ChildItem -LiteralPath $runDir -File | Remove-Item -Force
        Remove-Item -LiteralPath $runDir
    }
    exit 0
}

# Keep the actual polling functions, but bound fixture timeouts to one second.
foreach ($name in @('Wait-BackendExit', 'Wait-BackendConnectionAttempt')) {
    if (Test-Path "Function:$name") {
        $original = (Get-Item "Function:$name").ScriptBlock
        if ($name -eq 'Wait-BackendExit') {
            $script:originalWaitExit = $original
            function Wait-BackendExit([int]$TimeoutSeconds) { & $script:originalWaitExit 1 }
        } else {
            $script:originalWaitConnection = $original
            function Wait-BackendConnectionAttempt($Initial, [int]$TimeoutSeconds) {
                & $script:originalWaitConnection $Initial 1
            }
        }
    }
}
function Start-Sleep { Microsoft.PowerShell.Utility\Start-Sleep -Milliseconds 5 }
function Wait-Health([string]$Target, [int]$TimeoutSeconds) { (Get-BackendHealth) -eq $Target }

$project = 'vs45-fixture'
$composeYml = 'fixture.yml'
$envFile = 'unused.env'
$runDir = Join-Path ([IO.Path]::GetTempPath()) ('vs45-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runDir | Out-Null

function docker {
    $argv = @($args)
    $global:LASTEXITCODE = 0
    if ($argv[0] -eq 'inspect') {
        if ($script:mode -eq 'inspect-error' -and $argv[-1] -eq "$project-backend-1") {
            $global:LASTEXITCODE = 1
            return
        }
        if ($script:mode -eq 'inspect-empty' -and $argv[-1] -eq "$project-backend-1") { return }
        $service = ($argv[-1] -replace '^vs45-fixture-', '') -replace '-1$', ''
        if ($argv[2] -eq '{{.State.Health.Status}}') {
            if ($script:backendRunning -and $script:dependenciesRunning) { 'healthy' } else { 'unhealthy' }
        } else {
            $running = if ($service -eq 'backend') { $script:backendRunning } else { $script:dependenciesRunning }
            @{
                State = @{
                    Status = $(if ($running) { 'running' } else { 'exited' })
                    Running = $running; Restarting = $false
                    ExitCode = $(if ($service -eq 'backend') { $script:backendExit } else { 0 })
                    StartedAt = $script:startedAt
                }
                RestartCount = $script:restartCount
            } | ConvertTo-Json -Depth 4 -Compress
        }
        return
    }
    if ($argv[0] -eq 'logs') {
        if ($argv[2] -notmatch '^\d{4}-\d{2}-\d{2}T') { throw 'Docker --since must use an ISO timestamp' }
        if ($script:mode -ne 'no-connection-attempt') { 'HikariPool-1 - Starting...' }
        return
    }
    if ($argv[0] -eq 'update') { $script:restartDisabled = $true; return }
    if ($argv[0] -eq 'start') {
        $script:startedAt = '2026-10-24T00:00:02Z'
        $script:backendRunning = $script:mode -eq 'running-unhealthy'
        $script:backendExit = if ($script:mode -eq 'nonzero-exit') { 17 } else { 0 }
        return
    }
    if ($argv[0] -ne 'compose') { throw "Unexpected fixture command: $argv" }
    $verbIndex = -1
    for ($i = 1; $i -lt $argv.Count; $i++) {
        if ($argv[$i] -in @('stop', 'start', 'restart', 'up')) { $verbIndex = $i; break }
    }
    if ($verbIndex -lt 0) { throw "Unexpected fixture compose command: $argv" }
    $verb = $argv[$verbIndex]
    $services = @($argv[($verbIndex + 1)..($argv.Count - 1)] | Where-Object { $_ -in @('backend', 'mysql', 'redis') })
    if ($verb -eq 'stop') {
        if ($services -contains 'backend') { $script:backendRunning = $false; $script:backendExit = 0 }
        if ($services -contains 'mysql' -or $services -contains 'redis') { $script:dependenciesRunning = $false }
    } elseif ($verb -eq 'restart') {
        $script:backendRunning = $true
        $script:startedAt = '2026-10-24T00:00:01Z'
    } elseif ($verb -eq 'start') {
        $script:dependenciesRunning = $true
        if ($script:mode -eq 'restarted') {
            $script:restartCount++
            $script:startedAt = '2026-10-24T00:00:03Z'
        }
    } elseif ($verb -eq 'up') {
        if ($argv -notcontains '--no-deps') {
            $script:dependenciesRunning = $true
            $script:autoStartedDependencies = $true
        }
        if ($argv -contains '--wait') { $global:LASTEXITCODE = 17 }
    }
}

$failures = @()
try {
    foreach ($case in @(
        @{ Name = 'nonzero-exit'; Scenario = $exhaustScenario; Expected = $true },
        @{ Name = 'running-unhealthy'; Scenario = $exhaustScenario; Expected = $false; RejectedBy = 'A4-exhausted-retries-fail-clearly' },
        @{ Name = 'zero-exit'; Scenario = $exhaustScenario; Expected = $false; RejectedBy = 'A4-exhausted-retries-fail-clearly' },
        @{ Name = 'inspect-error'; Scenario = $exhaustScenario; Expected = $false; ErrorPattern = 'inspect' },
        @{ Name = 'inspect-empty'; Scenario = $exhaustScenario; Expected = $false; ErrorPattern = 'inspect' },
        @{ Name = 'same-process'; Scenario = $lateScenario; Expected = $true },
        @{ Name = 'restarted'; Scenario = $lateScenario; Expected = $false; RejectedBy = 'A4-late-same-process-recovered' },
        @{ Name = 'no-connection-attempt'; Scenario = $lateScenario; Expected = $false; RejectedBy = 'A4-late-connection-attempt-observed' }
    )) {
        $script:mode = $case.Name
        $script:results = [ordered]@{}
        $script:dependenciesRunning = $true
        $script:backendRunning = $true
        $script:backendExit = 0
        $script:restartCount = 0
        $script:startedAt = '2026-10-24T00:00:00Z'
        $script:restartDisabled = $false
        $script:autoStartedDependencies = $false
        $caught = $null
        try { . $case.Scenario } catch { $caught = $_ }
        $passed = -not $caught -and $results.Count -gt 0 -and
            @($results.Values | Where-Object { $_ -ne 'PASS' }).Count -eq 0
        $correct = ($passed -eq $case.Expected) -and -not $autoStartedDependencies
        if ($case.ErrorPattern) {
            $correct = $correct -and $null -ne $caught -and "$caught" -match $case.ErrorPattern
        }
        if ($case.RejectedBy) { $correct = $correct -and -not $caught -and $results[$case.RejectedBy] -eq 'FAIL' }
        if ($case.Expected -and $case.Scenario -eq $lateScenario) { $correct = $correct -and $restartDisabled }
        if (-not $correct) {
            $failures += $case.Name
            Write-Host "REGRESSION FAIL: $($case.Name) (passed=$passed, error=$caught, autoStartedDependencies=$autoStartedDependencies)"
        } else { Write-Host "REGRESSION PASS: $($case.Name)" }
    }
    if ($failures.Count) { throw "Startup regressions failed: $($failures -join ', ')" }
    Write-Host 'PASS: eight startup verification regressions (expected rejection cases included)'
} finally {
    # Only this newly-created fixture directory and its direct log files belong to this test.
    Get-ChildItem -LiteralPath $runDir -File | Remove-Item -Force
    Remove-Item -LiteralPath $runDir
}
