# 工单 44 专用双实例验收驱动（本票独立脚本，不改共享验收脚本）。
#
# 流程：
#   1. 起隔离 PostgreSQL/Redis（Docker：postgres:18.7 + redis:7.2.4，随机端口 + nonce 命名）
#   2. 打包后端 jar，启动两个真实 Spring Boot 进程（不同端口，共享隔离库）
#   3. 通过 psql seed 团队空间 + 用户 + 图片
#   4. 运行 collab-dual-instance.mjs 驱动真实 WebSocket 协议
#   5. 断言后查询最终数据（picture 未被修改、配额不变）
#   6. 清理自己创建的进程与临时目录
#
# 业务表只由 Flyway 以迁移角色 vision_migrator 创建（Start-AcceptanceDockerServices 内），
# 本脚本不再执行 DDL，也不再有原生二进制模式。
#
# 用法：
#   pwsh.exe -NoLogo -NoProfile -File scripts/collab-verification/collab-dual-instance.ps1
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$processWindow = @{}
if ($IsWindows) { $processWindow['WindowStyle'] = 'Hidden' }
Import-Module (Join-Path $repo 'scripts/acceptance-environment.psm1') -Force
$services = $null
$savedEnvironment = @{}
foreach ($entry in Get-ChildItem Env:) { $savedEnvironment[$entry.Name] = $entry.Value }
$nonce = [Guid]::NewGuid().ToString('N')
# C 盘空间紧张：运行目录放 D 盘
$runDir = Join-Path (Join-Path $repo '.vs44-runs') "vs44-collab-$nonce"
New-Item -ItemType Directory -Path $runDir | Out-Null
$artifacts = Join-Path $runDir 'artifacts'
New-Item -ItemType Directory -Path $artifacts | Out-Null
$ownedProcesses = @()
$exitCode = 0

function Free-Port {
    $l = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $l.Start(); try { return $l.LocalEndpoint.Port } finally { $l.Stop() }
}
# psql 裸值读取：口令经 PGPASSWORD 环境变量传入（不进 argv），SQL 走 stdin
# （避免 Windows 参数转义吃掉引号），输出用 -tA（tuples-only + unaligned）：
# 每行一个裸值，多列用 | 分隔。
function Invoke-PsqlRows {
    param(
        [string]$Container,
        [string]$Database,
        [string]$Password,
        [string]$Sql,
        [string]$LogPath
    )
    $output = $Sql | & docker exec -i -e "PGPASSWORD=$Password" $Container psql --no-psqlrc -tA -v ON_ERROR_STOP=1 -f - -U vision_app -d $Database 2> $LogPath
    if ($LASTEXITCODE -ne 0) {
        $detail = Get-Content -Raw -LiteralPath $LogPath -ErrorAction SilentlyContinue
        throw "psql failed (exit $LASTEXITCODE): $Sql`n$detail"
    }
    return @($output | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
}
# PATH 上的 java 可能是 Oracle javapath 转发器（Start-Process 会静默退出且不写日志）。
# 显式优先 JAVA_HOME 并逐个探测，避免把「java 起不来」误报成「后端启动超时」。
function Resolve-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME $(if ($IsWindows) { 'bin/java.exe' } else { 'bin/java' })) }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $candidates += $command.Source }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not $candidate -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $probe = Join-Path $runDir 'java-probe.log'
        $p = Start-Process @processWindow -FilePath $candidate -ArgumentList @('-version') -PassThru -RedirectStandardOutput ($probe + '.out') -RedirectStandardError ($probe + '.err')
        if ($p.WaitForExit(30000) -and $p.ExitCode -eq 0) { return $candidate }
    }
    throw 'BLOCKED_ENV: no working Java runtime found'
}

try {
    Write-Host "== [1/6] isolated PostgreSQL/Redis =="
    $database = "vs44_collab_$nonce"
    $services = Start-AcceptanceDockerServices -RunDirectory $runDir -Database $database -Nonce $nonce
    $postgresPort = $services.PostgresPort
    $redisPort = $services.RedisPort
    Write-Host "   postgres=$postgresPort redis=$redisPort"

    Write-Host "== [2/6] package backend jar =="
    Push-Location $repo
    & mvn -B --no-transfer-progress -DskipTests package *> (Join-Path $runDir 'package.log')
    if ($LASTEXITCODE -ne 0 -or -not (Select-String -LiteralPath (Join-Path $runDir 'package.log') -SimpleMatch 'BUILD SUCCESS')) {
        throw 'mvn package failed or Maven did not execute; see package.log'
    }
    $jar = Get-ChildItem (Join-Path $repo 'target/*.jar') | Where-Object { $_.Name -notmatch '\.original\.jar$' } | Select-Object -First 1
    if (-not $jar) { throw 'no jar produced' }
    Pop-Location

    Write-Host "== [3/6] start two backend instances =="
    $portA = Free-Port; $portB = Free-Port
    while ($portB -eq $portA) { $portB = Free-Port }
    # 数据源与 Redis 全部走外部 yml（--spring.config.additional-location），
    # 与 scripts/browser-acceptance.ps1 的已验证模式一致：命令行/环境变量在这台
    # 机器的 Start-Process 组合下曾被 jar 内属性源覆盖（见 jdbc-probe-before 对比）。
    $backendConfigPath = Join-Path $runDir 'backend-overlay.yml'
    [IO.File]::WriteAllText($backendConfigPath, @"
spring:
  datasource:
    # ${database} 明确变量边界：PS7 会把 "$database?x" 解析为 null 条件成员访问
    url: jdbc:postgresql://127.0.0.1:$postgresPort/${database}
    username: vision_app
    password: $nonce
    hikari:
      jdbc-url: jdbc:postgresql://127.0.0.1:$postgresPort/${database}
      username: vision_app
      password: $nonce
  data:
    redis:
      host: 127.0.0.1
      port: $redisPort
      password: $nonce
      database: 15
  session:
    store-type: redis
    timeout: 600
"@, [Text.UTF8Encoding]::new($false))
    $commonArgs = @(
        # 验收实例内存占用收敛：协作验收不需要大堆，两实例 + PostgreSQL + Redis 同机并存
        '-Duser.timezone=UTC', '-Xmx256m', '-Xms128m',
        '-jar', $jar.FullName,
        "--server.port=$portA",
        # 不用 local profile（它依赖 gitignore 的本地配置文件），占位密钥走命令行
        '--spring.profiles.active=default',
        "--spring.config.additional-location=file:$backendConfigPath",
        '--spring.session.store-type=redis', '--spring.session.timeout=600',
        '--vision-space.websocket.lease-ttl-seconds=5',
        '--vision-space.websocket.lease-renew-interval-ms=1500',
        '--vision-space.security.default-password=acceptance-only'
    ) + @(Get-AcceptanceApplicationArguments $postgresPort $redisPort $database $nonce)
    $argsB = $commonArgs -replace "--server\.port=$portA", "--server.port=$portB"
    $java = Resolve-Java
    $ownedProcesses += Start-Process @processWindow -FilePath $java -ArgumentList $commonArgs -PassThru -RedirectStandardOutput (Join-Path $runDir 'backend-a.log') -RedirectStandardError (Join-Path $runDir 'backend-a-err.log') -Environment $backendEnv
    $ownedProcesses += Start-Process @processWindow -FilePath $java -ArgumentList $argsB -PassThru -RedirectStandardOutput (Join-Path $runDir 'backend-b.log') -RedirectStandardError (Join-Path $runDir 'backend-b-err.log') -Environment $backendEnv
    # 低内存环境下 JVM 启动可能超过 120s；同时监测进程是否被杀
    foreach ($pair in @(@($portA, 'backend A', 0), @($portB, 'backend B', 1))) {
        $p = $ownedProcesses[$pair[2]]
        $deadline = [DateTime]::UtcNow.AddSeconds(240)
        $opened = $false
        while ([DateTime]::UtcNow -lt $deadline) {
            if ($p.HasExited) { throw "BLOCKED_ENV: $($pair[1]) process exited with $($p.ExitCode) (check backend logs; often OOM on this host)" }
            $c = [Net.Sockets.TcpClient]::new()
            try { $c.Connect('127.0.0.1', $pair[0]); $c.Dispose(); $opened = $true; break } catch { Start-Sleep -Milliseconds 800 } finally { $c.Dispose() }
        }
        if (-not $opened) {
            # 诊断：backend 失败时用 psql 直接验证 PostgreSQL 端凭据状态
            # （区分「数据库端拒绝」与「backend 属性被覆盖」两种可能）
            throw "BLOCKED_ENV: $($pair[1]) did not open port $($pair[0])"
        }
    }
    # 等待两个实例真正可服务（健康探测）
    foreach ($p in @($portA, $portB)) {
        $ok = $false
        foreach ($i in 1..60) {
            try {
                $resp = Invoke-WebRequest -Uri "http://127.0.0.1:$p/api/user/login" -Method POST -ContentType 'application/json' -Body '{}' -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
                $ok = $true; break
            } catch { Start-Sleep -Seconds 2 }
        }
        if (-not $ok) { throw "backend on $p never became servable" }
    }
    Write-Host "   instanceA=$portA instanceB=$portB"

    Write-Host "== [4/6] register users + seed fixtures =="
    # 用户通过真实注册入口创建（mjs register 阶段），PS 只 seed 资源与角色。
    $env:VS44_PHASE = 'register'
    $env:VS44_PORT_A = "$portA"; $env:VS44_PORT_B = "$portB"
    # collab-dual-instance.mjs 只把端口当存在性守卫；值为本轮 PostgreSQL 端口。
    $env:VS44_PG_PORT = "$postgresPort"; $env:VS44_REDIS_PORT = "$redisPort"
    $env:VS44_NONCE = $nonce
    Push-Location (Join-Path $repo 'frontend')
    & node (Join-Path $PSScriptRoot 'collab-dual-instance.mjs')
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'user registration phase failed' }
    Pop-Location
    $seedPath = Join-Path $runDir 'seed.sql'
    $idOwner = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql "SELECT id FROM app_user WHERE user_account='owner_44';" -LogPath (Join-Path $runDir 'user-lookup.log'))[0]
    $idEditor = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql "SELECT id FROM app_user WHERE user_account='editor_44';" -LogPath (Join-Path $runDir 'user-lookup.log'))[0]
    $idViewer = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql "SELECT id FROM app_user WHERE user_account='viewer_44';" -LogPath (Join-Path $runDir 'user-lookup.log'))[0]
    if (-not $idOwner -or -not $idEditor -or -not $idViewer) { throw 'user registration produced no rows' }
    [IO.File]::WriteAllText($seedPath, @"
INSERT INTO space (id, space_name, user_id, space_level, space_type, max_size, max_count, total_size, total_count, is_delete)
 VALUES (9441001, 'team44', $idOwner, 1, 1, 1000, 1000, 0, 1, 0);
INSERT INTO space_user (space_id, user_id, space_role) VALUES
 (9441001, $idOwner, 'admin'), (9441001, $idEditor, 'editor'), (9441001, $idViewer, 'viewer');
INSERT INTO picture (id, url, name, user_id, pic_size, space_id, review_status, is_delete)
 VALUES (9442001, 'collab-picture', 'original', $idOwner, 10, 9441001, 1, 0);
"@, [Text.UTF8Encoding]::new($false))
    # 走 stdin：psql 逐条执行，ON_ERROR_STOP 让第一条失败即停（PostgreSQL 不会在
    # 失败后继续同事务语句），错误明细留在 seed.log。
    $seedRows = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql (Get-Content -Raw -LiteralPath $seedPath) -LogPath (Join-Path $runDir 'seed.log'))
    $seedRows | Set-Content (Join-Path $runDir 'seed-output.log')
    $fixture = [ordered]@{
        spaceId  = 9441001
        pictureId = 9442001
        users    = [ordered]@{
            owner  = [ordered]@{ id = [long]$idOwner; account = 'owner_44' }
            editor = [ordered]@{ id = [long]$idEditor; account = 'editor_44' }
            viewer = [ordered]@{ id = [long]$idViewer; account = 'viewer_44' }
        }
    }
    $fixturePath = Join-Path $runDir 'fixture.json'
    $fixture | ConvertTo-Json | Set-Content $fixturePath

    Write-Host "== [5/6] run protocol scenarios =="
    $env:VS44_PHASE = 'scenarios'
    $env:VS44_PORT_A = "$portA"; $env:VS44_PORT_B = "$portB"
    $env:VS44_PG_PORT = "$postgresPort"; $env:VS44_REDIS_PORT = "$redisPort"
    $env:VS44_NONCE = $nonce
    $env:VS44_ARTIFACTS = $artifacts
    $env:VS44_FIXTURE_JSON = $fixturePath
    $env:VS44_DEMOTE_FILE = (Join-Path $artifacts 'demote.trigger')
    $env:VS44_DEMOTE_DONE_FILE = (Join-Path $artifacts 'demote.done')
    # 降权触发文件由 mjs 写入（带真实 editor userId）；这里起一个轮询 job 执行 SQL 并写 done
    $demoteJob = Start-Job -ScriptBlock {
        param($container, $database, $password, $trigger, $done, $logPath)
        while ($true) {
            if (Test-Path $trigger) {
                $triggerData = Get-Content -Raw $trigger | ConvertFrom-Json
                # JS 侧雪花 id 会精度丢失，用 account 反查真实 id
                $account = ([string]$triggerData.user.account) -replace "'", "''"
                $uid = & docker exec -e "PGPASSWORD=$password" $container psql --no-psqlrc -tA -v ON_ERROR_STOP=1 `
                    -c "SELECT id FROM app_user WHERE user_account='$account';" -U vision_app -d $database 2> $logPath
                if ($LASTEXITCODE -ne 0 -or -not $uid) { throw 'Could not locate the isolated fixture user.' }
                $uid = "$uid".Trim()
                & docker exec -e "PGPASSWORD=$password" $container psql --no-psqlrc -v ON_ERROR_STOP=1 `
                    -c "UPDATE space_user SET space_role='viewer' WHERE space_id=$($triggerData.spaceId) AND user_id=$uid;" -U vision_app -d $database *> $logPath
                if ($LASTEXITCODE -ne 0) { throw 'Fixture demotion did not execute.' }
                Set-Content -Path $done -Value "demoted userId=$uid account=$account"
                break
            }
            Start-Sleep -Milliseconds 200
        }
    } -ArgumentList $services.PostgresContainer, $database, $nonce, (Join-Path $artifacts 'demote.trigger'), (Join-Path $artifacts 'demote.done'), (Join-Path $runDir 'demote-psql.log')

    Push-Location (Join-Path $repo 'frontend')
    & node --experimental-default-type=module (Join-Path $PSScriptRoot 'collab-dual-instance.mjs')
    $protocolExit = $LASTEXITCODE
    Pop-Location
    Receive-Job $demoteJob -ErrorAction Continue *> (Join-Path $runDir 'demote-job.log')
    Stop-Job $demoteJob -ErrorAction SilentlyContinue; Remove-Job $demoteJob -Force -ErrorAction SilentlyContinue

    Write-Host "== [6/6] final data assertions =="
    $finalQueries = @"
SELECT name FROM picture WHERE id = 9442001;
SELECT total_size, total_count FROM space WHERE id = 9441001;
SELECT space_role FROM space_user WHERE space_id = 9441001 AND user_id = $idEditor;
"@
    $finalPath = Join-Path $runDir 'final.sql'
    [IO.File]::WriteAllText($finalPath, $finalQueries, [Text.UTF8Encoding]::new($false))
    $finalOutput = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql $finalQueries -LogPath (Join-Path $runDir 'final-query-error.log'))
    $finalOutput | Set-Content (Join-Path $artifacts 'final-data.txt')
    Write-Host $finalOutput
    # psql -tA 的多列输出用 | 分隔（MySQL 的 batch 输出用制表符）。
    if ($finalOutput.Count -eq 3 -and $finalOutput[0] -eq 'original' -and $finalOutput[1] -eq '0|1' -and $finalOutput[2] -eq 'viewer') {
        Write-Host 'PASS  最终数据：picture.name 保持 original（协作消息不落库）'
    } else {
        Write-Host 'FAIL  最终数据：picture.name 被意外修改'
        $exitCode = 1
    }
    if ($protocolExit -ne 0) { $exitCode = 1 }
    Copy-Item (Join-Path $artifacts 'protocol-results.json') (Join-Path $runDir 'protocol-results-copy.json') -ErrorAction SilentlyContinue
} catch {
    Write-Host "ERROR: $_"
    $_ | Out-String | Set-Content (Join-Path $runDir 'driver-error.log')
    $exitCode = 2
} finally {
    $cleanup = 'PASS'
    foreach ($ownedProcess in $ownedProcesses) {
        try {
            if (-not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id -Force -ErrorAction Stop }
            if (-not $ownedProcess.WaitForExit(10000)) { throw 'Owned process did not exit.' }
        } catch { $cleanup = 'FAIL' }
    }
    if ($services) { try { Stop-AcceptanceDockerServices $services } catch { $cleanup = 'FAIL' } }
    if ($demoteJob) { Stop-Job $demoteJob -ErrorAction SilentlyContinue; Remove-Job $demoteJob -Force -ErrorAction SilentlyContinue }
    foreach ($entry in @(Get-ChildItem Env:)) {
        if (-not $savedEnvironment.ContainsKey($entry.Name)) { [Environment]::SetEnvironmentVariable($entry.Name, $null) }
    }
    foreach ($key in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key]) }
    if ($cleanup -ne 'PASS') { $exitCode = 1 }
    $report = Join-Path $repo 'target/collab-protocol'
    New-Item -ItemType Directory -Force $report | Out-Null
    @{ head = (git rev-parse HEAD); runId = $(if ($env:VS_SRF_RUN_ID) { $env:VS_SRF_RUN_ID } else { [Guid]::NewGuid().ToString('N') }); infrastructure = 'docker'; results = @{ protocol = $(if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }); cleanup = $cleanup }; productionAcceptance = 'NOT_RUN' } |
        ConvertTo-Json | Set-Content (Join-Path $report 'summary.json')
    if (Test-Path (Join-Path $artifacts 'protocol-results.json')) {
        Copy-Item (Join-Path $artifacts 'protocol-results.json') $report
    }
    Write-Host "artifacts: $artifacts"
    Write-Host "rundir:    $runDir"
}
exit $exitCode
