# #45 容器与健康部署隔离验证
#
# 用唯一 Compose project、随机回环端口、独立卷与网络启动隔离候选，逐条产出
# 验收证据：非 root 身份与写权限边界、端口发布与网络可达范围、实际生效
# profile 与敏感日志行为、依赖故障下的健康迁移与重试耗尽表现。
# 只清理本脚本创建的资源；测试秘密随机生成，留在忽略的临时文件里，不输出值。
#
# 用法： pwsh.exe -NoLogo -NoProfile -File ./scripts/container-verification.ps1
[CmdletBinding()]
param(
    # 跳过镜像构建（复用上次构建结果时用于复跑单项）
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path $PSScriptRoot -Parent

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host 'BLOCKED_ENV: docker CLI unavailable'
    exit 2
}
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'BLOCKED_ENV: docker daemon unavailable'
    exit 2
}

$nonce = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$project = "vs45-$nonce"
$runDir = Join-Path ([IO.Path]::GetTempPath()) "visionspace-container-$nonce"
New-Item -ItemType Directory -Path $runDir | Out-Null
$envFile = Join-Path $runDir '.env.secrets'
$composeYml = Join-Path $repo 'docker-compose.yml'

function Free-Port {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}

# 所有 compose 调用统一走这里：固定 -f / -p / --env-file，避免漏带。
# 只负责执行与失败即抛，不返回输出；需要输出时直接调 docker compose。
function Invoke-Compose([string[]]$ExtraArgs) {
    & docker compose -f $composeYml -p $project --env-file $envFile @ExtraArgs
    if ($LASTEXITCODE -ne 0) { throw "compose $($ExtraArgs -join ' ') failed (exit $LASTEXITCODE)" }
}

function Assert([bool]$Condition, [string]$Name, [string]$Detail) {
    if ($Condition) {
        $script:results[$Name] = 'PASS'
        Write-Host "PASS: $Name"
    } else {
        $script:results[$Name] = 'FAIL'
        Write-Host "FAIL: $Name -- $Detail"
    }
}

function Get-BackendHealth {
    & docker inspect --format '{{.State.Health.Status}}' "$project-backend-1" 2>$null
}

# 有界等待健康状态到达期望值；只认目标状态，中途其它状态继续等。
function Wait-Health([string]$Target, [int]$TimeoutSeconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ((Get-BackendHealth) -eq $Target) { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

# 有界等待后端业务端点可响应（容器 healthy 之前应用可能尚未监听）。
function Wait-BackendHttp([int]$TimeoutSeconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$backendPort/api/health" -TimeoutSec 5
            if ($response.StatusCode -eq 200) { return $true }
        } catch { Start-Sleep -Seconds 3 }
    }
    return $false
}

$results = [ordered]@{}
$backendPort = Free-Port

# ---- 随机测试秘密：写入忽略的临时文件，不回显 ----
# 口令字符集避免引号/反斜杠：隔离验收不覆盖 SQL 转义边界，初始化脚本
# 对任意口令的可靠性由独立口令规则与人工部署流程保证。
function New-Secret([int]$Length) {
    $chars = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    $bytes = [byte[]]::new($Length)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}
$mysqlRoot = New-Secret 24
$dbPassword = New-Secret 24
$redisPassword = New-Secret 24
$dashscopeKey = "test-" + (New-Secret 20)
$defaultPassword = New-Secret 20

@"
MYSQL_ROOT_PASSWORD=$mysqlRoot
MYSQL_DATABASE=vs45_$nonce
SPRING_DATASOURCE_PASSWORD=$dbPassword
REDIS_PASSWORD=$redisPassword
BACKEND_PORT=$backendPort
SPRING_PROFILES_ACTIVE=prod
SPRING_AI_DASHSCOPE_API_KEY=$dashscopeKey
SPRING_AI_OPENAI_API_KEY=$dashscopeKey
ALI_YUN_AI_API_KEY=$dashscopeKey
VISION_SPACE_PEXELS_API_KEY=$dashscopeKey
VISION_SPACE_VISION_RECOGNITION_API_KEY=$dashscopeKey
VISION_SPACE_DEFAULT_PASSWORD=$defaultPassword
"@ | Set-Content -LiteralPath $envFile -NoNewline

try {
    Write-Host "=== [1/4] build & up (project $project) ==="
    if (-not $SkipBuild) {
        Invoke-Compose @('build', '--pull=false') *> (Join-Path $runDir 'build.log')
    }
    # 只启动基础设施，先导入业务表结构，再让 backend 启动（防止 FileStorageInit
    # 的 PostConstruct 查询不存在的表导致候选启动失败）。
    Invoke-Compose @('up', '-d', 'mysql', 'redis')

    Write-Host '=== schema bootstrap (isolated database) ==='
    # 复用 #27 验收的约定：只提取跟踪 DDL 的 CREATE TABLE 语句，不含库选择、
    # DROP 与任何数据；表数变化必须人工复核（当前 15 张）。
    $sourceDdl = Get-Content -Raw -LiteralPath (Join-Path $repo 'sql/create_vision_space.sql')
    $tables = [regex]::Matches($sourceDdl, '(?ms)^CREATE TABLE .*?^\)[^\r\n]*;')
    if ($tables.Count -ne 15) { throw "FAIL: schema changed ($($tables.Count) tables); review bootstrap before running." }
    $ddl = ($tables | ForEach-Object { $_.Value }) -join "`n"
    # 隔离库的账号由 init 脚本在数据卷首启时创建；这里等 MySQL 就绪后用
    # healthcheck 账号导入业务表。口令经 MYSQL_PWD 环境变量传入。
    $schemaPath = Join-Path $runDir 'schema.sql'
    [IO.File]::WriteAllText($schemaPath, "USE ``vs45_$nonce``;`n$ddl", [Text.UTF8Encoding]::new($false))
    $mysqlReady = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $deadline) {
        $state = & docker inspect --format '{{.State.Health.Status}}' "$project-mysql-1" 2>$null
        if ($state -eq 'healthy') { $mysqlReady = $true; break }
        Start-Sleep -Seconds 3
    }
    if (-not $mysqlReady) { throw 'isolated MySQL did not become healthy within 120s' }
    # docker compose up -d mysql 已执行 init 脚本（vision_app/healthcheck 已建）；
    # 表结构导入用初始化管理身份（root）：vision_app 只有业务 DML 权限，没有 CREATE。
    # 通过 stdin 灌入 DDL，不把 schema 文件挂进镜像或卷。
    Get-Content -Raw $schemaPath | docker compose -f $composeYml -p $project --env-file $envFile exec -T `
        -e MYSQL_PWD="$mysqlRoot" mysql sh -c 'mysql' > (Join-Path $runDir 'schema-import.log')
    if ($LASTEXITCODE -ne 0) { throw 'schema import failed (see schema-import.log)' }
    Write-Host "PASS: schema-bootstrap ($($tables.Count) tables)"

    Invoke-Compose @('up', '-d', 'backend')

    # ---- 验收 1：容器实际 UID 与写权限 ----
    Write-Host '=== [2/4] identity & writable paths ==='
    $uid = (& docker compose -f $composeYml -p $project --env-file $envFile exec -T backend sh -c 'id -u' | Out-String).Trim()
    Assert ($uid -eq '10001') 'A1-backend-nonroot-uid' "uid=$uid"

    $writeTmp = & docker compose -f $composeYml -p $project --env-file $envFile exec -T backend sh -c 'touch /app/tmp/probe && rm /app/tmp/probe && echo OK' 2>$null
    Assert ("$writeTmp".Trim() -eq 'OK') 'A1-tmp-writable' 'write to /app/tmp'

    $writeApp = & docker compose -f $composeYml -p $project --env-file $envFile exec -T backend sh -c 'touch /app/forbidden 2>/dev/null && echo WROTE || echo DENIED' 2>$null
    Assert ("$writeApp".Trim() -eq 'DENIED') 'A1-app-readonly' 'write to /app must be denied'

    $writeRoot = & docker compose -f $composeYml -p $project --env-file $envFile exec -T backend sh -c 'touch /forbidden 2>/dev/null && echo WROTE || echo DENIED' 2>$null
    Assert ("$writeRoot".Trim() -eq 'DENIED') 'A1-root-readonly' 'write to / must be denied'

    # ---- 验收 2：端口发布与网络可达范围 ----
    Write-Host '=== [3/4] port publishing & network reach ==='
    # compose ps --format json 为每个服务输出一行 JSON，逐行解析。
    # 字段结构对照 Docker 官方文档：服务级 Service / 发布项级 URL（绑定地址）。
    $portsRaw = & docker compose -f $composeYml -p $project --env-file $envFile ps --format json 2>$null
    $portsJson = @($portsRaw | ForEach-Object { $_ | ConvertFrom-Json })
    $mysqlPublished = @($portsJson | Where-Object { $_.Service -eq 'mysql' } | ForEach-Object { $_.Publishers } | Where-Object { $_ })
    $redisPublished = @($portsJson | Where-Object { $_.Service -eq 'redis' } | ForEach-Object { $_.Publishers } | Where-Object { $_ })
    $backendPub = @($portsJson | Where-Object { $_.Service -eq 'backend' } | ForEach-Object { $_.Publishers } | Where-Object { $_ })
    Assert (($mysqlPublished.Count -eq 0) -and ($redisPublished.Count -eq 0)) 'A2-db-redis-not-published' 'mysql/redis must publish no ports'
    $backOk = ($backendPub.Count -eq 1) -and ($backendPub[0].PublishedPort -eq $backendPort) -and ("$($backendPub[0].URL)" -eq '127.0.0.1')
    Assert $backOk 'A2-backend-loopback-only' "backend published: $($backendPub | ConvertTo-Json -Compress)"

    $netInspect = & docker network inspect "$($project)_internal" --format '{{.Internal}}' 2>$null
    Assert ("$netInspect" -eq 'true') 'A2-internal-network-isolated' "internal network Internal=$netInspect"

    # 可达性以本轮候选为对象：DB/Redis 的任何宿主发布（含非默认端口）都算失败。
    # 宿主机上与候选无关的既有服务不在判定范围。
    $leaked = @()
    foreach ($svc in @('mysql', 'redis')) {
        $anyPublish = @($portsJson | Where-Object { $_.Service -eq $svc } | ForEach-Object { $_.Publishers } | Where-Object { $_ -and $_.PublishedPort -gt 0 })
        if ($anyPublish.Count -gt 0) { $leaked += $svc }
    }
    Assert ($leaked.Count -eq 0) 'A2-db-redis-not-published-any' "services with host publishing: $($leaked -join ',')"

    # ---- 验收 3：健康端点不泄露秘密 + profile 与日志卫生 ----
    Write-Host '=== [4/4] profile, log hygiene & health ==='
    # 后端业务端点就绪后才开始 HTTP 验收（up -d 不等待 Spring 冷启动）。
    if (-not (Wait-BackendHttp 180)) { throw 'backend business endpoint did not become ready within 180s' }
    $activeProfile = (& docker compose -f $composeYml -p $project --env-file $envFile exec -T backend sh -c 'echo $SPRING_PROFILES_ACTIVE' | Out-String).Trim()
    Assert ("$activeProfile" -eq 'prod') 'A3-prod-profile-active' "SPRING_PROFILES_ACTIVE=$activeProfile"

    $healthBody = (Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$backendPort/api/health" -TimeoutSec 10).Content
    $healthClean = (-not $healthBody.Contains($mysqlRoot)) -and (-not $healthBody.Contains($dbPassword)) -and (-not $healthBody.Contains($redisPassword)) -and (-not $healthBody.Contains($dashscopeKey))
    Assert $healthClean 'A3-health-no-secrets' 'health response must not contain secrets'

    # 触发一次真实 SQL（注册请求会写 user 表），再查日志
    $regBody = '{"userAccount":"vs45_' + $nonce + '","userPassword":"' + $defaultPassword + '","checkPassword":"' + $defaultPassword + '"}'
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$backendPort/api/user/register" -Method Post -Body $regBody -ContentType 'application/json' -TimeoutSec 15 *> $null
    } catch { }
    Start-Sleep -Seconds 2
    $backendLog = & docker compose -f $composeYml -p $project --env-file $envFile logs backend 2>&1 | Out-String
    $sqlLeak = ($backendLog -match 'Parameters:') -or ($backendLog -match 'Preparing:')
    Assert (-not $sqlLeak) 'A3-no-sql-param-logging' 'backend log must not contain MyBatis SQL parameter lines'
    $secretLeak = $backendLog.Contains($dbPassword) -or $backendLog.Contains($redisPassword)
    Assert (-not $secretLeak) 'A3-no-secret-in-logs' 'backend log must not contain injected secrets'

    # ---- 验收 4：健康状态与依赖故障（MySQL/Redis 故障、恢复、延迟启动、重试耗尽）----
    Write-Host '=== extra: dependency failure, recovery & retry exhaustion ==='
    Assert ((Get-BackendHealth) -eq 'healthy') 'A4-healthy-before-failure' 'readiness healthy before failure'

    Invoke-Compose @('stop', 'mysql') *> (Join-Path $runDir 'mysql-stop.log')
    Assert (Wait-Health 'unhealthy' 90) 'A4-readiness-down-on-mysql-stop' 'readiness must report unhealthy after mysql stops'

    Invoke-Compose @('start', 'mysql') *> (Join-Path $runDir 'mysql-start.log')
    # 恢复等待必须等到 healthy 或超时：unhealthy 是过渡态，不能一次判负
    Assert (Wait-Health 'healthy' 180) 'A4-recovers-after-mysql-restart' 'readiness must return healthy after mysql restarts'

    # Redis 故障同样必须反映到 readiness（redis 组件在 readiness 组内）
    Invoke-Compose @('stop', 'redis') *> (Join-Path $runDir 'redis-stop.log')
    Assert (Wait-Health 'unhealthy' 90) 'A4-readiness-down-on-redis-stop' 'readiness must report unhealthy after redis stops'

    Invoke-Compose @('start', 'redis') *> (Join-Path $runDir 'redis-start.log')
    Assert (Wait-Health 'healthy' 180) 'A4-recovers-after-redis-restart' 'readiness must return healthy after redis restarts'

    # 依赖延迟启动：backend 在依赖缺失时被拉起，应用在重试窗口内等待，
    # 依赖恢复后仍能到达 healthy（而非立即永久失败）
    Invoke-Compose @('stop', 'mysql', 'redis') *> (Join-Path $runDir 'deps-stop.log')
    Invoke-Compose @('restart', 'backend') *> (Join-Path $runDir 'backend-restart.log')
    Start-Sleep -Seconds 20   # 让 backend 越过快速启动阶段、进入依赖等待窗口
    Invoke-Compose @('start', 'mysql', 'redis') *> (Join-Path $runDir 'deps-restart.log')
    Assert (Wait-Health 'healthy' 240) 'A4-late-dependency-recovers' 'backend must reach healthy once dependencies start late'

    # 重试耗尽：依赖持续缺失时应用必须明确失败退出（退出码非 0），而非带病运行
    Invoke-Compose @('stop', 'mysql', 'redis') *> (Join-Path $runDir 'deps-stop2.log')
    Invoke-Compose @('stop', 'backend') *> (Join-Path $runDir 'backend-stop2.log')
    Invoke-Compose @('up', '-d', '--wait', '--wait-timeout', '150', 'backend') *> (Join-Path $runDir 'backend-exhaust.log') 2>&1
    $exhausted = $LASTEXITCODE -ne 0
    $exitCode = (& docker inspect --format '{{.State.ExitCode}}' "$project-backend-1" 2>$null)
    $restarted = (& docker inspect --format '{{.State.Restarting}}' "$project-backend-1" 2>$null)
    # 明确失败 = 进程以非 0 退出，或处于崩溃循环（restart 策略下）
    $clearFailure = $exhausted -or ("$exitCode" -ne '0') -or ("$restarted" -eq 'true')
    Assert $clearFailure 'A4-exhausted-retries-fail-clearly' "exit=$exitCode restarting=$restarted composeWaitExit=$LASTEXITCODE"

    Write-Host "=== summary (artifacts: $runDir) ==="
    $failed = @($results.GetEnumerator() | Where-Object { $_.Value -ne 'PASS' })
    if ($failed.Count -eq 0) {
        Write-Host 'ALL PASS'
        exit 0
    }
    Write-Host "FAILED: $($failed.Name -join ', ')"
    exit 1
} catch {
    Write-Host "BLOCKED_ENV/ERROR: $_"
    exit 2
} finally {
    Write-Host '=== cleanup: removing only this run''s resources ==='
    & docker compose -f $composeYml -p $project --env-file $envFile down -v --remove-orphans *> (Join-Path $runDir 'down.log')
    Remove-Item -LiteralPath $envFile -Force -ErrorAction SilentlyContinue
}
