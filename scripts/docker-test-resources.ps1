# Docker 测试资源盘点 + 安全回收。
#
# 默认是 DRY RUN：只列出资源、分类、以及 --ApplySafe 会执行的命令，不删除任何东西。
#
# 硬性安全规则（由本脚本保证，不只是文档约定）：
#   * 永不调用 docker system/volume/container/image/network prune。
#   * 永不删除运行中的容器。
#   * 永不删除 compose 开发服务、vs.protected=true、kind=dev-service 的资源。
#   * 永不删除其他项目的资源（t3-*、eye-diagnosis_*、ai-interview_*、milvus* 等）。
#   * 无 vs.owner 标签的历史资源只报告（REVIEW_*），绝不猜测归属自动删除。
#   * --ApplySafe 只回收带 vs.owner=vision-space 且已停止/无引用的资源，
#     并在每次删除前重新确认状态。
#
# 用法：
#   pwsh -File ./scripts/docker-test-resources.ps1                     # 人读报告
#   pwsh -File ./scripts/docker-test-resources.ps1 -Json               # 供 CI/脚本消费
#   pwsh -File ./scripts/docker-test-resources.ps1 -RunId <id>         # 只看某一轮
#   pwsh -File ./scripts/docker-test-resources.ps1 -AssertClean -RunId <id>
#   pwsh -File ./scripts/docker-test-resources.ps1 -ApplySafe [-RunId <id>]
[CmdletBinding()]
param(
    [switch]$Json,
    [switch]$ApplySafe,
    [switch]$AssertClean,
    [string]$RunId,
    [string]$Owner = 'vision-space'
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

if (-not (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)) {
    Write-Host 'BLOCKED_ENV: docker CLI unavailable'
    exit 2
}
& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'BLOCKED_ENV: docker daemon unavailable'
    exit 2
}

$separator = "`t"
$separatorPattern = [regex]::Escape($separator)

function Normalize-Label([string]$Value) {
    if (-not $Value -or $Value -eq '<no value>') { return '' }
    return $Value
}

# ------------------------------------------------------------ assert-clean ---
# 只回答「这一轮是否还有残留」，不构建完整清单。
if ($AssertClean) {
    if (-not $RunId) {
        Write-Host 'BLOCKED_ENV: -AssertClean requires -RunId'
        exit 2
    }
    $containers = @(& docker ps -aq --filter "label=vs.run-id=$RunId" 2>$null | Where-Object { $_ })
    $volumes = @(& docker volume ls -q --filter "label=vs.run-id=$RunId" 2>$null | Where-Object { $_ })
    $networks = @(& docker network ls -q --filter "label=vs.run-id=$RunId" 2>$null | Where-Object { $_ })
    if (($containers.Count + $volumes.Count + $networks.Count) -eq 0) {
        Write-Host "clean: run $RunId has no leftover resources"
        exit 0
    }
    Write-Host ("LEAK: run {0} containers={1} volumes={2} networks={3}" -f
        $RunId, $containers.Count, $volumes.Count, $networks.Count)
    exit 1
}

# ---------------------------------------------------------------- snapshot ---
$containerRows = @()
$containerFormat = '{{.ID}}' + $separator + '{{.Names}}' + $separator + '{{.State}}' + $separator +
    '{{.Image}}' + $separator + '{{.Label "vs.owner"}}' + $separator + '{{.Label "vs.run-id"}}' + $separator +
    '{{.Label "vs.kind"}}' + $separator + '{{.Label "vs.protected"}}' + $separator +
    '{{.Label "com.docker.compose.project"}}'
foreach ($line in @(& docker ps -a --format $containerFormat 2>$null)) {
    if (-not $line) { continue }
    $fields = $line -split $separatorPattern
    if ($fields.Count -lt 9) { continue }
    $containerRows += [pscustomobject]@{
        Id             = $fields[0]
        Name           = $fields[1]
        State          = $fields[2]
        Image          = $fields[3]
        Owner          = Normalize-Label $fields[4]
        RunId          = Normalize-Label $fields[5]
        Kind           = Normalize-Label $fields[6]
        Protected      = Normalize-Label $fields[7]
        ComposeProject = Normalize-Label $fields[8]
    }
}

# 挂载关系：用于判断「已停止的容器是否还持有卷」。
$mountMap = @{}
if ($containerRows.Count -gt 0) {
    $mountFormat = '{{.Name}}' + $separator + '{{range .Mounts}}{{if eq .Type "volume"}}{{.Name}},{{end}}{{end}}'
    $ids = @($containerRows | ForEach-Object { $_.Id })
    foreach ($line in @(& docker inspect --format $mountFormat @ids 2>$null)) {
        if (-not $line) { continue }
        $fields = $line -split $separatorPattern, 2
        if ($fields.Count -lt 2) { continue }
        $mountMap[$fields[0].TrimStart('/')] = $fields[1]
    }
}

function Get-VolumeHolders([string]$VolumeName) {
    $holders = @()
    foreach ($container in $containerRows) {
        $mounts = $mountMap[$container.Name]
        if ($mounts -and (",$mounts" -like "*,$VolumeName,*")) {
            $holders += ('{0}({1})' -f $container.Name, $container.State)
        }
    }
    return ($holders -join ' ')
}

$volumeRows = @()
$volumeNames = @(& docker volume ls -q 2>$null | Where-Object { $_ })
if ($volumeNames.Count -gt 0) {
    $volumeFormat = '{{.Name}}' + $separator + '{{.Driver}}' + $separator +
        '{{index .Labels "vs.owner"}}' + $separator + '{{index .Labels "vs.run-id"}}' + $separator +
        '{{index .Labels "vs.kind"}}' + $separator + '{{index .Labels "vs.protected"}}' + $separator +
        '{{index .Labels "com.docker.compose.project"}}' + $separator + '{{.CreatedAt}}'
    foreach ($line in @(& docker volume inspect --format $volumeFormat @volumeNames 2>$null)) {
        if (-not $line) { continue }
        $fields = $line -split $separatorPattern
        if ($fields.Count -lt 8) { continue }
        $volumeRows += [pscustomobject]@{
            Name           = $fields[0]
            Driver         = $fields[1]
            Owner          = Normalize-Label $fields[2]
            RunId          = Normalize-Label $fields[3]
            Kind           = Normalize-Label $fields[4]
            Protected      = Normalize-Label $fields[5]
            ComposeProject = Normalize-Label $fields[6]
            CreatedAt      = $fields[7]
            Holders        = Get-VolumeHolders $fields[0]
        }
    }
}

$danglingNetworks = @(& docker network ls --filter dangling=true --format '{{.Name}}' 2>$null)
$networkRows = @()
$networkFormat = '{{.ID}}' + $separator + '{{.Name}}' + $separator + '{{.Label "vs.owner"}}' + $separator +
    '{{.Label "vs.run-id"}}' + $separator + '{{.Label "com.docker.compose.project"}}'
foreach ($line in @(& docker network ls --format $networkFormat 2>$null)) {
    if (-not $line) { continue }
    $fields = $line -split $separatorPattern
    if ($fields.Count -lt 5) { continue }
    $networkRows += [pscustomobject]@{
        Id             = $fields[0]
        Name           = $fields[1]
        Owner          = Normalize-Label $fields[2]
        RunId          = Normalize-Label $fields[3]
        ComposeProject = Normalize-Label $fields[4]
        Dangling       = ($danglingNetworks -contains $fields[1])
    }
}

# ------------------------------------------------------------- classifiers ---
function Test-ProtectedName([string]$Name) {
    return ($Name -match '^(t3-|eye-diagnosis_|local-preview_|ai-interview_|milvus)')
}

function Test-VsLegacyName([string]$Name) {
    return ($Name -match '^(vs|vsvol|vs45|vs47|visionspace)([-_.]|$)')
}

function Get-ContainerClass($Row) {
    # 保护必须判在归属之前：compose 开发服务/受保护资源也可能带 vs.owner 标签，
    # 先判 owner 会让「已停止的开发容器」落进 SAFE_STOPPED 被误删。
    if ($Row.Protected -eq 'true' -or $Row.Kind -eq 'dev-service' -or $Row.ComposeProject) { return 'PROTECTED_DEV' }
    if ($Row.Owner -eq $Owner) {
        if ($Row.State -eq 'running') { return 'ACTIVE_OWNED' }
        $mounts = $mountMap[$Row.Name]
        if ($mounts) { return 'SAFE_HELD_STOPPED' }
        return 'SAFE_STOPPED'
    }
    if ($Row.Owner) { return 'OTHER_PROJECT' }
    if (Test-VsLegacyName $Row.Name) {
        if ($Row.State -eq 'running') { return 'REVIEW_LEGACY_RUNNING' }
        return 'REVIEW_LEGACY_STOPPED'
    }
    return 'OTHER_PROJECT'
}

function Get-VolumeClass($Row) {
    if ($Row.Protected -eq 'true' -or $Row.Kind -eq 'dev-service' -or (Test-ProtectedName $Row.Name)) { return 'PROTECTED_DEV' }
    if ($Row.Owner -eq $Owner) {
        if (-not $Row.Holders) { return 'SAFE_UNHELD' }
        if ($Row.Holders -match '\(running\)') { return 'ACTIVE_OWNED' }
        return 'SAFE_HELD_STOPPED'
    }
    if ($Row.Owner) { return 'OTHER_PROJECT' }
    if ($Row.Name -match '^[0-9a-f]{64}$') { return 'REVIEW_ANON' }
    if ($Row.Name -match '^(eye-|t3-|ai-interview_|milvus|local-preview_)') { return 'OTHER_PROJECT' }
    if (Test-VsLegacyName $Row.Name) { return 'REVIEW_LEGACY' }
    return 'REVIEW_UNKNOWN'
}

function Get-NetworkClass($Row) {
    if ($Row.Name -in @('bridge', 'host', 'none')) { return 'SYSTEM' }
    if ($Row.ComposeProject) { return 'PROTECTED_DEV' }
    if ($Row.Owner -eq $Owner) {
        if ($Row.Dangling) { return 'SAFE_DANGLING' }
        return 'ACTIVE_OWNED'
    }
    if ($Row.Owner) { return 'OTHER_PROJECT' }
    if ($Row.Name -match '^(t3-|eye-|ai-interview|milvus)') { return 'OTHER_PROJECT' }
    if (Test-VsLegacyName $Row.Name) { return 'REVIEW_LEGACY' }
    return 'REVIEW_UNKNOWN'
}

$containerRows = @($containerRows | Where-Object { -not $RunId -or $_.RunId -eq $RunId } | ForEach-Object {
        $_ | Add-Member -NotePropertyName Class -NotePropertyValue (Get-ContainerClass $_) -PassThru
    })
$volumeRows = @($volumeRows | Where-Object { -not $RunId -or $_.RunId -eq $RunId } | ForEach-Object {
        $_ | Add-Member -NotePropertyName Class -NotePropertyValue (Get-VolumeClass $_) -PassThru
    })
$networkRows = @($networkRows | Where-Object { -not $RunId -or $_.RunId -eq $RunId } | ForEach-Object {
        $_ | Add-Member -NotePropertyName Class -NotePropertyValue (Get-NetworkClass $_) -PassThru
    })

$classes = @(
    'ACTIVE_OWNED', 'SAFE_STOPPED', 'SAFE_UNHELD', 'SAFE_HELD_STOPPED', 'SAFE_DANGLING',
    'PROTECTED_DEV', 'REVIEW_LEGACY_RUNNING', 'REVIEW_LEGACY_STOPPED', 'REVIEW_LEGACY',
    'REVIEW_ANON', 'REVIEW_UNKNOWN', 'OTHER_PROJECT', 'SYSTEM'
)
$counts = [ordered]@{}
foreach ($class in $classes) {
    $counts[$class] = [ordered]@{
        containers = @($containerRows | Where-Object { $_.Class -eq $class }).Count
        volumes    = @($volumeRows | Where-Object { $_.Class -eq $class }).Count
        networks   = @($networkRows | Where-Object { $_.Class -eq $class }).Count
    }
}

$safeContainers = @($containerRows | Where-Object { $_.Class -in @('SAFE_STOPPED', 'SAFE_HELD_STOPPED') })
$safeVolumes = @($volumeRows | Where-Object { $_.Class -in @('SAFE_UNHELD', 'SAFE_HELD_STOPPED') })
$safeNetworks = @($networkRows | Where-Object { $_.Class -eq 'SAFE_DANGLING' })

if ($Json) {
    $output = [ordered]@{
        owner     = $Owner
        runFilter = $RunId
        counts    = $counts
        safe      = [ordered]@{
            containers = $safeContainers.Count
            volumes    = $safeVolumes.Count
            networks   = $safeNetworks.Count
        }
        resources = [ordered]@{
            containers = @($containerRows | ForEach-Object {
                    [ordered]@{ name = $_.Name; state = $_.State; image = $_.Image; owner = $_.Owner; runId = $_.RunId; class = $_.Class }
                })
            volumes    = @($volumeRows | ForEach-Object {
                    [ordered]@{ name = $_.Name; holders = $_.Holders; owner = $_.Owner; runId = $_.RunId; class = $_.Class }
                })
            networks   = @($networkRows | ForEach-Object {
                    [ordered]@{ name = $_.Name; owner = $_.Owner; runId = $_.RunId; class = $_.Class }
                })
        }
    }
    $output | ConvertTo-Json -Depth 6
    exit 0
}

# ---------------------------------------------------------------- report -----
Write-Host '=== Docker test resource inventory (dry-run) ==='
Write-Host "owner namespace : $Owner"
Write-Host "run filter      : $(if ($RunId) { $RunId } else { '<all>' })"
Write-Host "host            : $(docker version --format '{{.Server.Version}}' 2>$null)"
Write-Host ''

Write-Host '--- CONTAINERS ---'
if ($containerRows.Count -eq 0) { Write-Host '  (none)' }
foreach ($row in $containerRows) {
    Write-Host ('  [{0,-22}] {1,-38} {2,-10} {3}  run={4}' -f $row.Class, $row.Name, $row.State, $row.Image, $(if ($row.RunId) { $row.RunId } else { '<none>' }))
}
Write-Host ''

Write-Host '--- VOLUMES ---'
if ($volumeRows.Count -eq 0) { Write-Host '  (none)' }
foreach ($row in $volumeRows) {
    Write-Host ('  [{0,-22}] {1,-38} holders={2}' -f $row.Class, $row.Name, $(if ($row.Holders) { $row.Holders } else { '<none>' }))
}
Write-Host ''

Write-Host '--- NETWORKS ---'
if ($networkRows.Count -eq 0) { Write-Host '  (none)' }
foreach ($row in $networkRows) {
    Write-Host ('  [{0,-22}] {1,-38} project={2}' -f $row.Class, $row.Name, $(if ($row.ComposeProject) { $row.ComposeProject } else { '<none>' }))
}
Write-Host ''

Write-Host '--- SUMMARY ---'
foreach ($class in $classes) {
    $entry = $counts[$class]
    Write-Host ('  {0,-22} containers={1,-3} volumes={2,-3} networks={3}' -f $class, $entry.containers, $entry.volumes, $entry.networks)
}
Write-Host ''
Write-Host ('SAFE set: containers={0} volumes={1} networks={2}' -f $safeContainers.Count, $safeVolumes.Count, $safeNetworks.Count)
Write-Host 'PROTECTED_DEV / OTHER_PROJECT / REVIEW_* are never deleted automatically.'
Write-Host 'REVIEW_* rows (legacy, unlabeled) need a human ownership decision.'
Write-Host 'This tool never calls any docker prune command.'

# ---------------------------------------------------------------- apply ------
if (-not $ApplySafe) {
    if (($safeContainers.Count + $safeVolumes.Count + $safeNetworks.Count) -gt 0) {
        Write-Host ''
        Write-Host 'Commands that WOULD run (dry run, nothing executed):'
        foreach ($row in $safeContainers) { Write-Host "  docker rm -f -v $($row.Id)" }
        foreach ($row in $safeVolumes) { Write-Host "  docker volume rm -f $($row.Name)" }
        foreach ($row in $safeNetworks) { Write-Host "  docker network rm $($row.Id)" }
        Write-Host 'Re-run with -ApplySafe to remove the SAFE set (optionally with -RunId).'
    }
    exit 0
}

Write-Host ''
Write-Host "=== -ApplySafe: deleting SAFE set (owner=$Owner) ==="
$removed = [ordered]@{ containers = 0; volumes = 0; networks = 0 }
$failed = $false

foreach ($row in $safeContainers) {
    $state = & docker inspect --format '{{.State.Status}}' $row.Id 2>$null
    if ($state -eq 'running') {
        Write-Host "  skip (started since snapshot): $($row.Name)"
        continue
    }
    & docker rm -f -v $row.Id *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  removed container $($row.Name)"
        $removed.containers++
    } else {
        Write-Host "  FAILED container $($row.Name)"
        $failed = $true
    }
}

foreach ($row in $safeVolumes) {
    # 容器循环可能刚刚释放卷，务必重新确认而不是相信快照。
    $held = @(& docker ps -a --filter "volume=$($row.Name)" -q 2>$null | Where-Object { $_ })
    if ($held.Count -gt 0) {
        Write-Host "  skip (still held by a container): $($row.Name)"
        continue
    }
    & docker volume rm -f $row.Name *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  removed volume $($row.Name)"
        $removed.volumes++
    } else {
        Write-Host "  FAILED volume $($row.Name)"
        $failed = $true
    }
}

foreach ($row in $safeNetworks) {
    $attached = & docker network inspect --format '{{len .Containers}}' $row.Id 2>$null
    if ($attached -ne '0') {
        Write-Host "  skip (network is attached): $($row.Name)"
        continue
    }
    & docker network rm $row.Id *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  removed network $($row.Name)"
        $removed.networks++
    } else {
        Write-Host "  FAILED network $($row.Name)"
        $failed = $true
    }
}

Write-Host ("removed: containers={0} volumes={1} networks={2}" -f $removed.containers, $removed.volumes, $removed.networks)
if ($failed) { exit 1 }
exit 0
