$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$auditor = Join-Path $PSScriptRoot '../docker-test-resources.ps1'

if (-not (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)) {
    Write-Host 'BLOCKED_ENV: docker CLI unavailable'
    exit 2
}
& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'BLOCKED_ENV: docker daemon unavailable'
    exit 2
}

# 静态契约：回收器永远不能调用全局 prune。
$auditorText = Get-Content -Raw -LiteralPath $auditor
if ($auditorText -match 'docker\s+(system|volume|container|image|network)\s+prune') {
    throw 'docker-test-resources.ps1 must never invoke a global prune command'
}

$tag = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$ownedRunId = "vs-reaper-$tag-owned"
$guardRunId = "vs-reaper-$tag-guard"
$activeRunId = "vs-reaper-$tag-active"
$foreignRunId = "vs-reaper-$tag-foreign"
$run = Join-Path ([IO.Path]::GetTempPath()) ('vs-docker-reaper-' + $tag)
New-Item -ItemType Directory -Path $run | Out-Null

$fixtureContainers = @()
$fixtureVolumes = @()

function New-Fixture([string]$Name, [string[]]$Labels, [string]$Volume, [switch]$Running) {
    $arguments = @('create', '--name', $Name) + $Labels
    if ($Volume) { $arguments += @('-v', "${Volume}:/data") } else { $arguments += @('--tmpfs', '/data') }
    $arguments += 'redis:7.2.4'
    & docker @arguments *> $null
    if ($LASTEXITCODE -ne 0) { throw "Fixture create failed: $Name" }
    $script:fixtureContainers += $Name
    if ($Running) {
        & docker start $Name *> $null
        if ($LASTEXITCODE -ne 0) { throw "Fixture start failed: $Name" }
    }
}

function Get-ReportedClass([string]$Name) {
    $matches = @($report.resources.containers | Where-Object { $_.name -eq $Name })
    if ($matches.Count -ne 1) { throw "Auditor did not report container $Name exactly once" }
    return $matches[0].class
}

try {
    # 1) 本仓库 owner、已停止、仍持有命名卷 —— 唯一可自动回收的形态。
    $ownedVolume = "vsvol-$ownedRunId-data"
    & docker volume create --label vs.owner=vision-space --label "vs.run-id=$ownedRunId" --label vs.kind=test $ownedVolume *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Fixture volume create failed' }
    $fixtureVolumes += $ownedVolume
    $ownedName = "vs-reaper-$tag-owned-mysql"
    New-Fixture -Name $ownedName -Labels @(
        '--label', 'vs.owner=vision-space', '--label', "vs.run-id=$ownedRunId", '--label', 'vs.kind=test'
    ) -Volume $ownedVolume

    # 2) 同时带 owner 与 protected 标签：保护必须优先于归属。
    $protectedName = "vs-reaper-$tag-protected"
    New-Fixture -Name $protectedName -Labels @(
        '--label', 'vs.owner=vision-space', '--label', "vs.run-id=$guardRunId",
        '--label', 'vs.kind=test', '--label', 'vs.protected=true'
    )

    # 3) compose 开发服务形态：同样带 owner，也必须判成 PROTECTED_DEV。
    $composeName = "vs-reaper-$tag-compose"
    New-Fixture -Name $composeName -Labels @(
        '--label', 'vs.owner=vision-space', '--label', "vs.run-id=$guardRunId",
        '--label', 'com.docker.compose.project=vs-review-fixture'
    )

    # 4) 其他项目 owner：不属于本命名空间。
    $foreignName = "vs-reaper-$tag-foreign"
    New-Fixture -Name $foreignName -Labels @(
        '--label', 'vs.owner=other-project', '--label', "vs.run-id=$foreignRunId"
    )

    # 5) 无标签历史残留（vs-shots-* 的同类形态）：只报告，永不自动删。
    $legacyName = "vs-legacy-$tag"
    New-Fixture -Name $legacyName -Labels @()

    # 6) 本仓库 owner 但仍在运行：永不删。
    $activeName = "vs-reaper-$tag-active"
    New-Fixture -Name $activeName -Labels @(
        '--label', 'vs.owner=vision-space', '--label', "vs.run-id=$activeRunId", '--label', 'vs.kind=test'
    ) -Running

    # ---------------------------------------------------------- report ----
    $auditorOutput = & pwsh -NoLogo -NoProfile -File $auditor -Json 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Auditor inventory failed: $($auditorOutput -join ' ')" }
    $report = ($auditorOutput | Out-String) | ConvertFrom-Json

    $expected = @{
        $ownedName     = 'SAFE_HELD_STOPPED'
        $protectedName = 'PROTECTED_DEV'
        $composeName   = 'PROTECTED_DEV'
        $foreignName   = 'OTHER_PROJECT'
        $legacyName    = 'REVIEW_LEGACY_STOPPED'
        $activeName    = 'ACTIVE_OWNED'
    }
    foreach ($entry in $expected.GetEnumerator()) {
        $actual = Get-ReportedClass $entry.Key
        if ($actual -ne $entry.Value) {
            throw "Auditor classified $($entry.Key) as $actual, expected $($entry.Value)"
        }
    }

    # ------------------------------------------------------ apply-safe ----
    & pwsh -NoLogo -NoProfile -File $auditor -ApplySafe -RunId $ownedRunId *> (Join-Path $run 'apply-owned.log')
    if ($LASTEXITCODE -ne 0) { throw 'apply-safe failed for the owned run' }
    & docker inspect $ownedName *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Stopped owned container was not reaped' }
    & docker volume inspect $ownedVolume *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Unheld owned volume was not reaped' }

    # 保护优先于归属：即使按 run-id 精确限定，protected / compose 也不能被删。
    & pwsh -NoLogo -NoProfile -File $auditor -ApplySafe -RunId $guardRunId *> (Join-Path $run 'apply-guard.log')
    if ($LASTEXITCODE -ne 0) { throw 'apply-safe failed for the guarded run' }
    foreach ($name in @($protectedName, $composeName)) {
        & docker inspect $name *> $null
        if ($LASTEXITCODE -ne 0) { throw "Protected fixture $name was deleted" }
    }

    # 运行中的容器永不删。
    & pwsh -NoLogo -NoProfile -File $auditor -ApplySafe -RunId $activeRunId *> (Join-Path $run 'apply-active.log')
    if ($LASTEXITCODE -ne 0) { throw 'apply-safe failed for the active run' }
    $state = & docker inspect --format '{{.State.Status}}' $activeName 2>$null
    if ($state -ne 'running') { throw 'Running owned container was removed or stopped' }

    # 其他项目与无标签历史残留不因任何一次 apply 被动过。
    foreach ($name in @($foreignName, $legacyName)) {
        & docker inspect $name *> $null
        if ($LASTEXITCODE -ne 0) { throw "Unrelated fixture $name was deleted" }
    }

    Write-Host 'PASS: docker reaper (classification, protection before ownership, apply-safe scoping, no global prune)'
} finally {
    foreach ($name in $fixtureContainers) { & docker rm -f -v $name *> $null }
    foreach ($volume in $fixtureVolumes) { & docker volume rm -f $volume *> $null }
    Remove-Item -LiteralPath $run -Recurse -Force -ErrorAction SilentlyContinue
}
exit 0
