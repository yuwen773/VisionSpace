$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Import-Module (Join-Path $PSScriptRoot '../docker-test-harness.psm1') -Force

if (-not (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)) {
    Write-Host 'BLOCKED_ENV: docker CLI unavailable'
    exit 2
}
& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'BLOCKED_ENV: docker daemon unavailable'
    exit 2
}

$run = Join-Path ([IO.Path]::GetTempPath()) ('vs-docker-lifecycle-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run | Out-Null
$runId = $null
$orphanRunId = $null
try {
    $runId = Initialize-VsDockerRun -RunId ('vs-lifecycle-' + [Guid]::NewGuid().ToString('N'))

    # 1) 卷必须是命名的、带归属标签 —— 匿名卷硬杀后无法归属。
    $volume = New-VsVolume -Suffix 'data'
    if ($volume -ne "vsvol-$runId-data") { throw "Volume must be named vsvol-<run-id>-data, got $volume" }
    $labels = & docker volume inspect --format '{{json .Labels}}' $volume 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'docker volume inspect failed' }
    if ($labels -notmatch [regex]::Escape('"vs.owner":"vision-space"')) { throw 'Volume owner label missing' }
    if ($labels -notmatch [regex]::Escape('"vs.run-id":"' + $runId + '"')) { throw 'Volume run-id label missing' }

    # 2) redis:7.2.4 声明了 VOLUME /data；显式挂命名卷后不应再产生匿名卷。
    $name = "vs-$runId-app"
    if (-not (Start-VsDockerContainer -Name $name -LogPath (Join-Path $run 'container.log') -Arguments @('-v', "${volume}:/data", 'redis:7.2.4'))) {
        throw 'Container start failed'
    }
    $mounts = @(& docker inspect --format '{{range .Mounts}}{{.Type}}:{{.Name}}{{println}}{{end}}' $name 2>$null |
        Where-Object { $_ })
    $volumeMounts = @($mounts | Where-Object { $_ -like 'volume:*' })
    if ($volumeMounts.Count -ne 1 -or $volumeMounts[0] -ne "volume:$volume") {
        throw "Expected exactly the named volume mount, got: $($volumeMounts -join ', ')"
    }

    # 3) 有资源时 Assert 必须失败 —— 否则「清理」变成只靠肉眼看。
    $threw = $false
    try { Assert-VsClean -RunId $runId | Out-Null } catch { $threw = $true }
    if (-not $threw) { throw 'Assert-VsClean must throw while resources still exist' }

    # 4) teardown 删除容器与命名卷，并断言干净；日志在删除前落到报告目录。
    if (-not (Complete-VsDockerRun -RunId $runId -LogDirectory $run -LogNames @($name))) {
        throw 'Complete-VsDockerRun reported leaks'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $run "$name-server.log"))) { throw 'Container log was not preserved before teardown' }
    & docker inspect $name *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Container survived teardown' }
    & docker volume inspect $volume *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Named volume survived teardown' }
    Assert-VsClean -RunId $runId | Out-Null

    # 5) 硬杀模拟：登记文件丢失，只剩标签 —— 仍必须能按 run-id 精确回收。
    $orphanRunId = Initialize-VsDockerRun -RunId ('vs-lifecycle-' + [Guid]::NewGuid().ToString('N'))
    $orphanVolume = New-VsVolume -Suffix 'orphan'
    $orphanName = "vs-$orphanRunId-orphan"
    if (-not (Start-VsDockerContainer -Name $orphanName -Arguments @('-v', "${orphanVolume}:/data", 'redis:7.2.4'))) {
        throw 'Orphan container start failed'
    }
    Remove-Item -LiteralPath (Get-VsDockerRunRegistryPath) -Force
    if (-not (Complete-VsDockerRun -RunId $orphanRunId)) {
        throw 'Label-based sweep must clean resources even without a registry file'
    }
    & docker inspect $orphanName *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Orphan container survived label-based teardown' }
    & docker volume inspect $orphanVolume *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Orphan volume survived label-based teardown' }

    Write-Host 'PASS: docker lifecycle (labels, named volumes, no anonymous volume, teardown, label-only sweep)'
} finally {
    if ($runId) { Complete-VsDockerRun -RunId $runId | Out-Null }
    if ($orphanRunId) { Complete-VsDockerRun -RunId $orphanRunId | Out-Null }
    Remove-Item -LiteralPath $run -Recurse -Force -ErrorAction SilentlyContinue
}
exit 0
