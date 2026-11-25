# Docker 测试资源生命周期 harness。
#
# 契约：
#   * 本 harness 创建的 container / volume / network 一律带标签：
#       vs.owner=vision-space / vs.run-id=<本轮唯一 id> / vs.kind=test
#   * 数据卷一律命名（vsvol-<run-id>-<suffix>），不使用匿名卷；
#     postgres:18.7 与 redis:7.2.4 都声明了 VOLUME，任何未显式挂载的路径
#     都会让 Docker 自动配一个匿名卷，硬杀后无法归属。
#   * Complete-VsDockerRun 只删除带本轮 run-id 的资源：先按标签扫，
#     再用运行登记文件兜底（标签写入失败时）。永不调用任何 prune，
#     永不按镜像/名字通配删除。
#   * Assert-VsClean 把「无残留」变成显式退出条件 —— 测试全绿不能替代资源回收。
#
# 已知边界：
#   finally 覆盖不了 TerminateProcess、终端被关、宿主关机。这里**不**注册
#   ProcessExit 处理器：它在非 runspace 线程回调，脚本块无法调用模块函数
#   （实测清理不执行），还会把正常退出码污染成 127。硬杀残留由下一次运行
#   scripts/docker-test-resources.ps1 的审计兜底。
#
# 用法（入口脚本）：
#   Import-Module (Join-Path $PSScriptRoot 'docker-test-harness.psm1') -Force
#   Initialize-VsDockerRun -RunId $runId
#   $volume = New-VsVolume -Suffix 'postgres'
#   Start-VsDockerContainer -Name $name -Arguments @('-v', "${volume}:/var/lib/postgresql", 'postgres:18.7') -LogPath $log
#   Complete-VsDockerRun -LogDirectory $logDir   # finally 中调用，返回是否干净

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$script:VsOwner = 'vision-space'
$script:VsKind = 'test'
$script:VsStateDirectory = Join-Path ([IO.Path]::GetTempPath()) 'visionspace-docker-runs'
$script:VsRunId = $null
$script:VsCreatedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$script:VsRegistryPath = $null
$script:VsTeardownDone = $false

function ConvertTo-VsDockerName([string]$Value) {
    $name = ([string]$Value).ToLowerInvariant() -replace '[^a-z0-9_.-]', '-'
    $name = $name.Trim([char[]]'-._')
    if (-not $name) { $name = 'x' }
    if ($name.Length -gt 120) { $name = $name.Substring(0, 120).TrimEnd([char[]]'-._') }
    return $name
}

function New-VsDockerRunId {
    $sha = 'nogit'
    try {
        $value = & git -C (Split-Path $PSScriptRoot -Parent) rev-parse --short HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $value) { $sha = ($value | Select-Object -First 1).ToString().Trim() }
    } catch { }
    $stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 6)
    return ('vs-{0}-{1}-{2}-{3}' -f $stamp, (ConvertTo-VsDockerName $sha), $PID, $suffix)
}

function Initialize-VsDockerRun {
    [CmdletBinding()]
    param(
        [string]$RunId,
        [string]$Owner = 'vision-space',
        [string]$Kind = 'test',
        [string]$StateDirectory = (Join-Path ([IO.Path]::GetTempPath()) 'visionspace-docker-runs')
    )
    if (-not $RunId) { $RunId = New-VsDockerRunId }
    $script:VsRunId = ConvertTo-VsDockerName $RunId
    $script:VsOwner = ConvertTo-VsDockerName $Owner
    $script:VsKind = ConvertTo-VsDockerName $Kind
    $script:VsStateDirectory = $StateDirectory
    $script:VsRegistryPath = Join-Path $StateDirectory ("$($script:VsRunId).resources")
    $script:VsCreatedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    $script:VsTeardownDone = $false
    return $script:VsRunId
}

function Get-VsRunId {
    if (-not $script:VsRunId) { Initialize-VsDockerRun | Out-Null }
    return $script:VsRunId
}

function Get-VsDockerRunRegistryPath {
    Get-VsRunId | Out-Null
    return $script:VsRegistryPath
}

function Get-VsDockerLabelArguments {
    $runId = Get-VsRunId
    return @(
        '--label', "vs.owner=$($script:VsOwner)",
        '--label', "vs.run-id=$runId",
        '--label', "vs.kind=$($script:VsKind)",
        '--label', "vs.created-at=$($script:VsCreatedAt)"
    )
}

function Test-VsDockerAvailable {
    if (-not (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)) { return $false }
    & docker info *> $null
    return ($LASTEXITCODE -eq 0)
}

function Register-VsDockerResource {
    param(
        [Parameter(Mandatory)][ValidateSet('container', 'volume', 'network')][string]$Type,
        [Parameter(Mandatory)][string]$Name
    )
    Get-VsRunId | Out-Null
    if (-not $script:VsRegistryPath) {
        $script:VsRegistryPath = Join-Path $script:VsStateDirectory ("$($script:VsRunId).resources")
    }
    $directory = Split-Path -Parent $script:VsRegistryPath
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    "$Type`t$Name" | Add-Content -LiteralPath $script:VsRegistryPath -Encoding utf8
}

function Get-VsDockerRegistryEntries {
    if (-not $script:VsRegistryPath -or -not (Test-Path -LiteralPath $script:VsRegistryPath)) { return @() }
    return @(Get-Content -LiteralPath $script:VsRegistryPath -Encoding utf8 |
        Where-Object { $_ -match "`t" } |
        ForEach-Object {
            $parts = $_ -split "`t", 2
            [pscustomobject]@{ Type = $parts[0]; Name = $parts[1] }
        })
}

function New-VsVolume {
    param([Parameter(Mandatory)][string]$Suffix)
    $name = 'vsvol-{0}-{1}' -f (Get-VsRunId), (ConvertTo-VsDockerName $Suffix)
    $labelArguments = @(Get-VsDockerLabelArguments)
    $output = & docker volume create @labelArguments '--label' 'vs.role=volume' $name 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("docker volume create failed for {0}: {1}" -f $name, (($output | Out-String).Trim()))
    }
    Register-VsDockerResource -Type volume -Name $name
    return $name
}

function Start-VsDockerContainer {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string[]]$Arguments,
        [string]$LogPath
    )
    $labelArguments = @(Get-VsDockerLabelArguments)
    $dockerArguments = @('run', '-d', '--name', $Name) + $labelArguments + $Arguments
    if ($LogPath) { & docker @dockerArguments *> $LogPath } else { & docker @dockerArguments }
    $exitCode = $LASTEXITCODE
    # 只有真正创建成功的容器才登记：先登记后启动会让启动失败时的清理
    # 必然报错，把真实原因盖成 cleanup=FAIL。
    if ($exitCode -eq 0) { Register-VsDockerResource -Type container -Name $Name }
    return ($exitCode -eq 0)
}

function Get-VsDockerRunResources {
    [CmdletBinding()]
    param([string]$RunId = (Get-VsRunId))
    $containerIds = @()
    $containerNames = @()
    $volumes = @()
    $networks = @()
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $containerIds = @(& docker ps -aq --filter "label=vs.run-id=$RunId" 2>$null | Where-Object { $_ })
        $containerNames = @(& docker ps -a --filter "label=vs.run-id=$RunId" --format '{{.Names}}' 2>$null | Where-Object { $_ })
        $volumes = @(& docker volume ls -q --filter "label=vs.run-id=$RunId" 2>$null | Where-Object { $_ })
        $networks = @(& docker network ls -q --filter "label=vs.run-id=$RunId" 2>$null | Where-Object { $_ })
    }
    return [pscustomobject]@{
        RunId          = $RunId
        ContainerIds   = $containerIds
        ContainerNames = $containerNames
        Volumes        = $volumes
        Networks       = $networks
    }
}

function Assert-VsClean {
    [CmdletBinding()]
    param([string]$RunId = (Get-VsRunId))
    if (-not (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)) {
        throw 'docker CLI unavailable: cleanliness cannot be asserted'
    }
    $remaining = Get-VsDockerRunResources -RunId $RunId
    $total = $remaining.ContainerIds.Count + $remaining.Volumes.Count + $remaining.Networks.Count
    if ($total -eq 0) { return $true }
    throw ("LEAK: run {0} containers={1} volumes={2} networks={3}" -f
        $RunId, $remaining.ContainerIds.Count, $remaining.Volumes.Count, $remaining.Networks.Count)
}

function Complete-VsDockerRun {
    [CmdletBinding()]
    param(
        [string]$RunId = (Get-VsRunId),
        [string]$LogDirectory,
        [string[]]$LogNames = @()
    )
    if ($script:VsTeardownDone -and $RunId -eq $script:VsRunId) {
        # 已经拆过一轮：只能认为「干净」的前提是此刻确实没有残留。
        # 早退不校验会让「拆完又被重新拉起」或「重复调用」的情况静默判 PASS，
        # 资源就留在机器上（验收报告显示 cleanup=PASS，docker ps 里却还有容器）。
        $leftover = Get-VsDockerRunResources -RunId $RunId
        $leaked = $leftover.ContainerIds.Count + $leftover.Volumes.Count + $leftover.Networks.Count
        if ($leaked -gt 0) {
            Write-Warning ("LEAK after repeated teardown: run {0} containers={1} volumes={2} networks={3}" -f
                $RunId, $leftover.ContainerIds.Count, $leftover.Volumes.Count, $leftover.Networks.Count)
            return $false
        }
        return $true
    }

    $registryEntries = @(Get-VsDockerRegistryEntries)
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        # native-explicit 模式（-MySqlBin/-RedisBin）不创建任何 Docker 资源。
        if ($registryEntries.Count -eq 0) { return $true }
        return $false
    }

    $healthy = $true

    # 1) 先留证据：清理前把本轮的容器日志拷到报告目录（容器不存在时跳过）。
    if ($LogDirectory) {
        New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
        $names = @(@($registryEntries | Where-Object { $_.Type -eq 'container' } | ForEach-Object { $_.Name }) + $LogNames |
            Where-Object { $_ } | Select-Object -Unique)
        foreach ($name in $names) {
            & docker inspect $name *> $null
            if ($LASTEXITCODE -eq 0) {
                & docker logs $name *> (Join-Path $LogDirectory "$name-server.log")
            }
        }
    }

    # 2) 按标签删除本轮资源。保护标签优先：即使有人把 run-id 标签贴到了
    #    protected 资源上，也不删。列表命令失败时无法断言干净，直接记失败。
    $containerIds = @(& docker ps -aq --filter "label=vs.run-id=$RunId" 2>$null | Where-Object { $_ })
    if ($LASTEXITCODE -ne 0) { $healthy = $false }
    foreach ($id in $containerIds) {
        $protected = & docker inspect --format '{{index .Config.Labels "vs.protected"}}' $id 2>$null
        if ($protected -eq 'true') { continue }
        & docker rm -f -v $id *> $null
        if ($LASTEXITCODE -ne 0) { $healthy = $false }
    }
    $volumeNames = @(& docker volume ls -q --filter "label=vs.run-id=$RunId" 2>$null | Where-Object { $_ })
    if ($LASTEXITCODE -ne 0) { $healthy = $false }
    foreach ($volume in $volumeNames) {
        & docker volume rm -f $volume *> $null
        if ($LASTEXITCODE -ne 0) { $healthy = $false }
    }
    $networkIds = @(& docker network ls -q --filter "label=vs.run-id=$RunId" 2>$null | Where-Object { $_ })
    if ($LASTEXITCODE -ne 0) { $healthy = $false }
    foreach ($network in $networkIds) {
        & docker network rm $network *> $null
        if ($LASTEXITCODE -ne 0) { $healthy = $false }
    }

    # 3) 登记文件兜底：标签写入失败（例如 docker 中途断连）时仍能按名字回收。
    foreach ($entry in $registryEntries) {
        switch ($entry.Type) {
            'container' { & docker rm -f -v $entry.Name *> $null }
            'volume' { & docker volume rm -f $entry.Name *> $null }
            'network' { & docker network rm $entry.Name *> $null }
        }
    }
    if ($script:VsRegistryPath -and (Test-Path -LiteralPath $script:VsRegistryPath)) {
        Remove-Item -LiteralPath $script:VsRegistryPath -ErrorAction SilentlyContinue
    }

    # 4) 显式断言干净：清理动作的退出码不等于没有残留。
    $remaining = Get-VsDockerRunResources -RunId $RunId
    if (($remaining.ContainerIds.Count + $remaining.Volumes.Count + $remaining.Networks.Count) -gt 0) {
        $healthy = $false
        Write-Warning ("LEAK after teardown: run {0} containers={1} volumes={2} networks={3}" -f
            $RunId, $remaining.ContainerIds.Count, $remaining.Volumes.Count, $remaining.Networks.Count)
    }
    if ($RunId -eq $script:VsRunId) { $script:VsTeardownDone = $true }
    return $healthy
}

Export-ModuleMember -Function Initialize-VsDockerRun, New-VsDockerRunId, Get-VsRunId,
    Get-VsDockerRunRegistryPath, Get-VsDockerLabelArguments, Test-VsDockerAvailable,
    New-VsVolume, Start-VsDockerContainer, Get-VsDockerRunResources, Assert-VsClean,
    Complete-VsDockerRun, Register-VsDockerResource
