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

$dockerExecutable = (Get-Command docker -CommandType Application -TotalCount 1 -ErrorAction SilentlyContinue).Source
if (-not $dockerExecutable) {
    Write-Host 'BLOCKED_ENV: docker CLI unavailable'
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

# 写入临时目录的原始输出。即使 Docker CLI 挂起，也能终止本次进程并保留已有输出。
function Invoke-BoundedProcess([string]$Executable, [string[]]$Arguments, [string]$Log, [int]$Seconds) {
    $start = [Diagnostics.ProcessStartInfo]::new($Executable)
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.CreateNoWindow = $true
    $stdout = [IO.FileStream]::new($Log, 'Create', 'Write', 'Read')
    try {
        $stderr = [IO.FileStream]::new("$Log.stderr", 'Create', 'Write', 'Read')
        try {
            $process = [Diagnostics.Process]::Start($start)
            try {
                $copies = @($process.StandardOutput.BaseStream.CopyToAsync($stdout),
                            $process.StandardError.BaseStream.CopyToAsync($stderr))
                if (-not $process.WaitForExit($Seconds * 1000)) {
                    try { $process.Kill($true) } catch { if (-not $process.HasExited) { throw } }
                    [void]$process.WaitForExit(5000)
                    [void][Threading.Tasks.Task]::WaitAll($copies, 5000)
                    return 124
                }
                if (-not [Threading.Tasks.Task]::WaitAll($copies, 5000)) { return 124 }
                return $process.ExitCode
            } finally { $process.Dispose() }
        } finally { $stderr.Dispose() }
    } finally { $stdout.Dispose() }
}

function Invoke-BoundedCompose([string[]]$ExtraArgs, [string]$Log, [int]$Seconds,
        [string]$ProjectName = $project, [string]$ProjectEnv = $envFile, [string]$OverrideFile) {
    $arguments = @('compose', '-f', $composeYml)
    if ($OverrideFile) { $arguments += @('-f', $OverrideFile) }
    $arguments += @('-p', $ProjectName, '--env-file', $ProjectEnv)
    Invoke-BoundedProcess $dockerExecutable ($arguments + $ExtraArgs) $Log $Seconds
}

function Remove-ContainerCandidate([string]$ProjectName = $project, [string]$ProjectEnv = $envFile,
        [string]$OverrideFile) {
    $prefix = if ($OverrideFile) { 'host-access-cleanup' } else { 'cleanup' }
    $projectFilter = "label=com.docker.compose.project=$ProjectName"
    # 先收回中断的 Flyway 一次性容器，避免 down 并行删卷时容器还占着卷。
    # compose rm 仅筛选常规服务容器，按项目、服务和 oneoff 标签精确定位。
    $flywayLog = Join-Path $runDir "$prefix-flyway-list.log"
    $flywayExit = Invoke-BoundedProcess $dockerExecutable @('container', 'ls', '--all', '--quiet', '--no-trunc',
        '--filter', $projectFilter, '--filter', 'label=com.docker.compose.service=flyway',
        '--filter', 'label=com.docker.compose.oneoff=True') $flywayLog 10
    Add-Content -LiteralPath $startupDiagnostic "$prefix-flyway-list exit=$flywayExit"
    $clean = $flywayExit -eq 0
    if ($clean) {
        $flywayIds = @(Get-Content -LiteralPath $flywayLog | Where-Object { $_.Trim() })
        if ($flywayIds -cnotmatch '^[0-9a-f]{64}$') {
            $clean = $false
        } elseif ($flywayIds.Count -gt 0) {
            $flywayExit = Invoke-BoundedProcess $dockerExecutable (@('container', 'rm', '--force', '--volumes') + $flywayIds) `
                (Join-Path $runDir "$prefix-flyway-remove.log") 30
            Add-Content -LiteralPath $startupDiagnostic "$prefix-flyway-remove exit=$flywayExit"
            $clean = $flywayExit -eq 0
        }
    }
    $code = Invoke-BoundedCompose @('--profile', 'tools', 'down', '-v', '--remove-orphans') `
        (Join-Path $runDir "$prefix-down.log") 45 $ProjectName $ProjectEnv $OverrideFile
    Add-Content -LiteralPath $startupDiagnostic "$prefix-down exit=$code"
    $clean = $clean -and $code -eq 0
    foreach ($kind in @('container', 'network', 'volume')) {
        $log = Join-Path $runDir "$prefix-$kind.log"
        $arguments = @($kind, 'ls', '-q', '--filter', $projectFilter)
        if ($kind -eq 'container') { $arguments += '--all' }
        $code = Invoke-BoundedProcess $dockerExecutable $arguments $log 10
        $remaining = @(Get-Content -LiteralPath $log | Where-Object { $_.Trim() }).Count
        Add-Content -LiteralPath $startupDiagnostic "$prefix-resource=$kind exit=$code remaining=$remaining"
        if ($code -ne 0 -or $remaining -gt 0) { $clean = $false }
    }
    return $clean
}

function Invoke-StartupCompose([string]$Stage, [string[]]$ExtraArgs, [int]$Seconds) {
    Add-Content -LiteralPath $startupDiagnostic "stage=$Stage status=started"
    $code = Invoke-BoundedCompose $ExtraArgs (Join-Path $runDir "$Stage.log") $Seconds
    Add-Content -LiteralPath $startupDiagnostic "stage=$Stage exit=$code"
    if ($code -ne 0) { throw "container startup failed at $Stage (exit $code)" }
}

function Write-StartupSnapshot {
    # 只发布已知的进度标签。原始 BuildKit/Flyway 行可能含 URL、SQL 或凭据。
    foreach ($stage in @('build', 'flyway-migrate')) {
        $tail = foreach ($suffix in @('', '.stderr')) {
            $log = Join-Path $runDir "$stage.log$suffix"
            if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 25 }
        }
        foreach ($line in $tail) {
            switch -Regex ($line) {
                '^#([1-9][0-9]{0,3}) (CACHED|DONE|ERROR)\b' {
                    Add-Content -LiteralPath $startupDiagnostic "$stage step=$($Matches[1]) status=$($Matches[2])"
                }
                '^#([1-9][0-9]{0,3}) \[' { Add-Content -LiteralPath $startupDiagnostic "$stage step=$($Matches[1]) status=started" }
                '^Successfully validated\b' { Add-Content -LiteralPath $startupDiagnostic "$stage status=validated" }
                '^Migrating schema\b' { Add-Content -LiteralPath $startupDiagnostic "$stage status=migrating" }
                '^Successfully applied\b' { Add-Content -LiteralPath $startupDiagnostic "$stage status=applied" }
                '^ERROR:' { Add-Content -LiteralPath $startupDiagnostic "$stage status=error" }
            }
        }
    }
    $stateLog = Join-Path $runDir 'startup-ps.log'
    $code = Invoke-BoundedCompose @('ps', '-a', '--format', '{{.Service}} {{.State}} {{.Health}}') $stateLog 10
    Add-Content -LiteralPath $startupDiagnostic "compose-ps exit=$code"
    if ($code -eq 0) {
        Get-Content -LiteralPath $stateLog | ForEach-Object {
            if ($_ -cmatch '^(backend|postgres|redis|flyway) (created|exited|running|restarting|paused|dead|removing) ?(healthy|unhealthy|starting)?$') {
                Add-Content -LiteralPath $startupDiagnostic "service=$($Matches[1]) state=$($Matches[2]) health=$($Matches[3])"
            }
        }
    }
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

# 有界 TCP 连接探测：从宿主机（容器网络之外）主动发起一次连接，
# 返回是否连通。用于验证「不可达」不是只靠 compose 元数据推断。
function Test-TcpConnect([string]$TargetHost, [int]$Port, [int]$TimeoutMs = 1500) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync($TargetHost, $Port)
        return $task.Wait($TimeoutMs) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-ContainerIp([string]$Service) {
    $raw = & docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$project-$Service-1" 2>$null
    if ($LASTEXITCODE -ne 0) { throw "docker inspect failed for $Service IP" }
    return ("$raw".Trim() -split "`n" | Where-Object { $_ } | Select-Object -First 1)
}

function Test-ContainerNetworkTcp([string]$Network, [string]$TargetHost, [int]$Port) {
    $output = & docker run --rm --network $Network redis:7-alpine sh -c "nc -z -w 3 $TargetHost $Port && echo CONNECTED || echo BLOCKED" 2>$null
    if ($LASTEXITCODE -ne 0 -or @($output).Count -ne 1 -or "$output" -notin @('CONNECTED', 'BLOCKED')) {
        throw "network probe failed for $Network to $TargetHost`:$Port"
    }
    return "$output" -eq 'CONNECTED'
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

function Get-ContainerInspection([string]$Service) {
    $raw = & docker inspect --format '{"State":{{json .State}},"RestartCount":{{.RestartCount}}}' "$project-$Service-1" 2>$null
    if ($LASTEXITCODE -ne 0) { throw "docker inspect failed for $Service (exit $LASTEXITCODE)" }
    $inspection = $raw | ConvertFrom-Json
    if (-not $inspection.State.Status -or -not $inspection.State.StartedAt -or
        $null -eq $inspection.State.Running -or $null -eq $inspection.State.Restarting -or
        $null -eq $inspection.State.ExitCode -or $null -eq $inspection.RestartCount) {
        throw "docker inspect returned incomplete state for $Service"
    }
    return $inspection
}

function Wait-BackendConnectionAttempt($Initial, [int]$TimeoutSeconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $current = Get-ContainerInspection 'backend'
        if (-not $current.State.Running -or $current.State.StartedAt -ne $Initial.State.StartedAt -or
            $current.RestartCount -ne $Initial.RestartCount) { return $false }
        # 只看本次进程的日志，不能用上一次启动的 Hikari 消息证明已进入连接阶段。
        $since = ([DateTimeOffset]$Initial.State.StartedAt).ToString('o', [Globalization.CultureInfo]::InvariantCulture)
        $log = & docker logs --since $since "$project-backend-1" 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0) { throw 'docker logs failed while waiting for the connection attempt' }
        if ($log -match 'HikariPool-\d+\s+-\s+Starting') { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Wait-BackendExit([int]$TimeoutSeconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $inspection = Get-ContainerInspection 'backend'
        if ($inspection.State.Status -eq 'exited') { return $inspection }
        Start-Sleep -Seconds 3
    }
    return $null
}

$results = [ordered]@{}
$reportDir = Join-Path $repo 'target/container-verification'
$diagnosticsDir = Join-Path $reportDir 'diagnostics'
$startupDiagnostic = Join-Path $runDir 'container-startup.log'
Set-Content -LiteralPath $startupDiagnostic 'container candidate startup'
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
$superuserPassword = New-Secret 24
$migratorPassword = New-Secret 24
$dbPassword = New-Secret 24
$redisPassword = New-Secret 24
$dashscopeKey = "test-" + (New-Secret 20)
$defaultPassword = New-Secret 20

@"
POSTGRES_SUPERUSER_PASSWORD=$superuserPassword
POSTGRES_MIGRATOR_PASSWORD=$migratorPassword
POSTGRES_DB=vs45_$nonce
SPRING_DATASOURCE_PASSWORD=$dbPassword
REDIS_PASSWORD=$redisPassword
BACKEND_PORT=$backendPort
SPRING_PROFILES_ACTIVE=prod
SPRING_AI_DASHSCOPE_API_KEY=$dashscopeKey
SPRING_AI_OPENAI_API_KEY=$dashscopeKey
# 模型端点与模型名：生产守卫要求这两项非空。指向假端点，容器内不会有任何外呼。
SPRING_AI_OPENAI_BASE_URL=http://openai-fixture.invalid/v1
SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=fixture-model
ALI_YUN_AI_API_KEY=$dashscopeKey
VISION_SPACE_PEXELS_API_KEY=$dashscopeKey
VISION_SPACE_VISION_RECOGNITION_API_KEY=$dashscopeKey
VISION_SPACE_VISION_RECOGNITION_BASE_URL=http://vision-fixture.invalid:8080
VISION_SPACE_VISION_RECOGNITION_MODEL=fixture-model
VISION_SPACE_DEFAULT_PASSWORD=$defaultPassword
"@ | Set-Content -LiteralPath $envFile -NoNewline

$dockerAvailable = $false
try {
    Add-Content -LiteralPath $startupDiagnostic 'stage=docker-info status=started'
    $code = Invoke-BoundedProcess $dockerExecutable @('info') (Join-Path $runDir 'docker-info.log') 15
    Add-Content -LiteralPath $startupDiagnostic "stage=docker-info exit=$code"
    if ($code -ne 0) { throw "docker daemon unavailable (exit $code)" }
    $dockerAvailable = $true
    Write-Host "=== [1/4] build & up (project $project) ==="
    if (-not $SkipBuild) {
        Invoke-StartupCompose 'build' @('--progress', 'plain', 'build', '--pull=false') 900
    }
    # 只启动基础设施，先让 Flyway 建业务表，再让 backend 启动（防止 FileStorageInit
    # 的 PostConstruct 查询不存在的表导致候选启动失败）。
    Invoke-StartupCompose 'dependencies-up' @('up', '-d', 'postgres', 'redis') 180

    Write-Host '=== schema bootstrap (Flyway, isolated database) ==='
    Add-Content -LiteralPath $startupDiagnostic 'stage=postgres-health status=started'
    # 唯一建表入口：Flyway 以迁移角色 vision_migrator 执行 V1 基线。
    # 迁移目录只读挂进 tools profile 的 flyway 服务；口令走 FLYWAY_* 环境变量，
    # 不出现在 compose 参数或日志里。
    $postgresReady = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $deadline) {
        $healthLog = Join-Path $runDir 'postgres-health.log'
        $code = Invoke-BoundedProcess $dockerExecutable `
            @('inspect', '--format', '{{.State.Health.Status}}', "$project-postgres-1") $healthLog 10
        if ($code -eq 124) { throw 'PostgreSQL health inspection timed out' }
        $state = Get-Content -LiteralPath $healthLog
        if ($state -eq 'healthy') { $postgresReady = $true; break }
        Start-Sleep -Seconds 3
    }
    if (-not $postgresReady) {
        throw 'isolated PostgreSQL did not become healthy within 120s'
    }
    Add-Content -LiteralPath $startupDiagnostic 'stage=postgres-health status=healthy'
    $previousFlyway = @{}
    foreach ($name in @('FLYWAY_URL', 'FLYWAY_USER', 'FLYWAY_PASSWORD')) {
        $previousFlyway[$name] = [Environment]::GetEnvironmentVariable($name)
    }
    try {
        $env:FLYWAY_URL = "jdbc:postgresql://postgres:5432/vs45_$nonce"
        $env:FLYWAY_USER = 'vision_migrator'
        $env:FLYWAY_PASSWORD = $migratorPassword
        # Flyway 镜像原生读取 FLYWAY_URL / FLYWAY_USER / FLYWAY_PASSWORD，
        # 不把连接串与口令写进 compose 参数（进程列表可见）。
        Invoke-StartupCompose 'flyway-migrate' @('--profile', 'tools', 'run', '--rm', '--no-deps',
            '-e', 'FLYWAY_URL', '-e', 'FLYWAY_USER', '-e', 'FLYWAY_PASSWORD', 'flyway',
            '-locations=filesystem:/flyway/sql', '-validateOnMigrate=true', '-cleanDisabled=true', 'migrate') 180
    } finally {
        foreach ($name in $previousFlyway.Keys) { [Environment]::SetEnvironmentVariable($name, $previousFlyway[$name]) }
    }

    # 基线声明的业务表数量是期望值；Flyway 自己的元数据表不计入。
    $baselineSql = Get-Content -Raw -LiteralPath (Join-Path $repo 'src/main/resources/db/migration/V1__baseline.sql')
    $expectedTables = [regex]::Matches($baselineSql, '(?m)^CREATE TABLE ').Count
    if ($expectedTables -eq 0) { throw 'FAIL: no CREATE TABLE statements found in the Flyway baseline' }
    $tableCountSql = "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'"
    Invoke-StartupCompose 'schema-check' @('exec', '-T', '-e', "PGPASSWORD=$dbPassword",
        'postgres', 'psql', '--no-psqlrc', '-tA', '-U', 'vision_app', '-d', "vs45_$nonce", '-c', $tableCountSql) 30
    $createdTables = (Get-Content -Raw -LiteralPath (Join-Path $runDir 'schema-check.log')) -replace '\s', ''
    if ($createdTables -ne "$expectedTables") {
        throw "FAIL: schema bootstrap incomplete (expected $expectedTables tables, found '$createdTables')"
    }
    Write-Host "PASS: schema-bootstrap ($expectedTables tables via Flyway)"

    Invoke-StartupCompose 'backend-up' @('up', '-d', 'backend') 120

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

    $writeDefaultTmp = & docker compose -f $composeYml -p $project --env-file $envFile exec -T backend sh -c 'touch /tmp/forbidden 2>/dev/null && echo WROTE || echo DENIED' 2>$null
    Assert ("$writeDefaultTmp".Trim() -eq 'DENIED') 'A1-default-tmp-readonly' 'write to /tmp must be denied'
    $jvmTmp = & docker compose -f $composeYml -p $project --env-file $envFile exec -T backend sh -c "tr '\000' '\n' < /proc/1/cmdline | grep -Fx -- '-Djava.io.tmpdir=/app/tmp'" 2>$null
    Assert ("$jvmTmp".Trim() -eq '-Djava.io.tmpdir=/app/tmp') 'A1-jvm-tmp-configured' 'the application JVM must use /app/tmp'

    # ---- 验收 2：端口发布与网络可达范围 ----
    Write-Host '=== [3/4] port publishing & network reach ==='
    # compose ps --format json 为每个服务输出一行 JSON，逐行解析。
    # 字段结构对照 Docker 官方文档：服务级 Service / 发布项级 URL（绑定地址）。
    $portsRaw = & docker compose -f $composeYml -p $project --env-file $envFile ps --format json 2>$null
    $portsJson = @($portsRaw | ForEach-Object { $_ | ConvertFrom-Json })
    $postgresPublished = @($portsJson | Where-Object { $_.Service -eq 'postgres' } | ForEach-Object { $_.Publishers } | Where-Object { $_ -and $_.PublishedPort -gt 0 })
    $redisPublished = @($portsJson | Where-Object { $_.Service -eq 'redis' } | ForEach-Object { $_.Publishers } | Where-Object { $_ -and $_.PublishedPort -gt 0 })
    $backendPub = @($portsJson | Where-Object { $_.Service -eq 'backend' } | ForEach-Object { $_.Publishers } | Where-Object { $_ })
    Assert (($postgresPublished.Count -eq 0) -and ($redisPublished.Count -eq 0)) 'A2-db-redis-not-published' 'postgres/redis must publish no ports'
    $backOk = ($backendPub.Count -eq 1) -and ($backendPub[0].PublishedPort -eq $backendPort) -and ("$($backendPub[0].URL)" -eq '127.0.0.1')
    Assert $backOk 'A2-backend-loopback-only' "backend published: $($backendPub | ConvertTo-Json -Compress)"

    $netInspect = & docker network inspect "$($project)_internal" --format '{{.Internal}}' 2>$null
    Assert ("$netInspect" -eq 'true') 'A2-internal-network-isolated' "internal network Internal=$netInspect"

    # 可达性以本轮候选为对象：DB/Redis 的任何宿主发布（含非默认端口）都算失败。
    # 宿主机上与候选无关的既有服务不在判定范围。
    $leaked = @()
    foreach ($svc in @('postgres', 'redis')) {
        $anyPublish = @($portsJson | Where-Object { $_.Service -eq $svc } | ForEach-Object { $_.Publishers } | Where-Object { $_ -and $_.PublishedPort -gt 0 })
        if ($anyPublish.Count -gt 0) { $leaked += $svc }
    }
    Assert ($leaked.Count -eq 0) 'A2-db-redis-not-published-any' "services with host publishing: $($leaked -join ',')"

    # 内部网络正向探针证明服务在监听；独立 edge 网络反向探针验证跨网络隔离。
    # Docker 宿主可能直接路由容器 IP；宿主其他服务也可能占用默认端口。
    # 此检查不代替从真实外网验证部署环境的路由和防火墙。
    $postgresIp = Get-ContainerIp 'postgres'
    $redisIp = Get-ContainerIp 'redis'
    foreach ($target in @(@('postgres', $postgresIp, 5432), @('redis', $redisIp, 6379))) {
        $name, $address, $port = $target
        $inside = Test-ContainerNetworkTcp "$($project)_internal" $address $port
        $outside = Test-ContainerNetworkTcp "$($project)_edge" $address $port
        Assert $inside "A2-$name-internal-reachable" "internal network cannot connect to $address`:$port"
        Assert (-not $outside) "A2-$name-edge-unreachable" "edge network can connect to $address`:$port"
    }

    # ---- 验收 3：健康端点不泄露秘密 + profile 与日志卫生 ----
    Write-Host '=== [4/4] profile, log hygiene & health ==='
    # 后端业务端点就绪后才开始 HTTP 验收（up -d 不等待 Spring 冷启动）。
    if (-not (Wait-BackendHttp 180)) { throw 'backend business endpoint did not become ready within 180s' }
    $activeProfile = (& docker compose -f $composeYml -p $project --env-file $envFile exec -T backend sh -c 'echo $SPRING_PROFILES_ACTIVE' | Out-String).Trim()
    Assert ("$activeProfile" -eq 'prod') 'A3-prod-profile-active' "SPRING_PROFILES_ACTIVE=$activeProfile"

    $visionConfig = @(& docker compose -f $composeYml -p $project --env-file $envFile exec -T backend `
        sh -c 'printf "%s\n%s\n" "$VISION_SPACE_VISION_RECOGNITION_BASE_URL" "$VISION_SPACE_VISION_RECOGNITION_MODEL"')
    Assert ($LASTEXITCODE -eq 0 -and $visionConfig.Count -eq 2 -and
        $visionConfig[0] -eq 'http://vision-fixture.invalid:8080' -and $visionConfig[1] -eq 'fixture-model') `
        'A3-vision-config-forwarded' 'backend container must receive the configured vision endpoint and model'

    $healthBody = (Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$backendPort/api/health" -TimeoutSec 10).Content
    $healthClean = (-not $healthBody.Contains($superuserPassword)) -and (-not $healthBody.Contains($migratorPassword)) -and (-not $healthBody.Contains($dbPassword)) -and (-not $healthBody.Contains($redisPassword)) -and (-not $healthBody.Contains($dashscopeKey))
    Assert $healthClean 'A3-health-no-secrets' 'health response must not contain secrets'

    # 触发一次真实写 SQL，并先用数据库行数确认写入确实发生，再判断日志卫生。
    # 没有正向控制时，「日志里没有 Preparing:/Parameters:」也可能只是请求被 CSRF
    # 或参数校验拦下、根本没有 SQL 执行，断言会静默通过。
    $probeAccount = 'vs45_' + $nonce
    $regBody = '{"userAccount":"' + $probeAccount + '","userPassword":"' + $defaultPassword + '","checkPassword":"' + $defaultPassword + '"}'
    function Get-UserRowCount {
        # 业务表名是 app_user（user 是 PostgreSQL 保留字）。
        $sql = 'SELECT count(*) FROM app_user;'
        $raw = $sql | docker compose -f $composeYml -p $project --env-file $envFile exec -T `
            -e "PGPASSWORD=$dbPassword" postgres psql --no-psqlrc -tA -U vision_app -d "vs45_$nonce" 2>$null | Out-String
        if ($LASTEXITCODE -ne 0) { throw 'user row count query failed' }
        $matches = [regex]::Matches($raw, '(?m)^\s*(\d+)\s*$')
        if ($matches.Count -eq 0) { throw "user row count query returned no number: $raw" }
        return [int]$matches[$matches.Count - 1].Groups[1].Value
    }
    $rowsBefore = Get-UserRowCount
    $registerStatus = 0
    $registerBody = ''
    try {
        $register = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$backendPort/api/user/register" -Method Post -Body $regBody -ContentType 'application/json' -TimeoutSec 15
        $registerStatus = [int]$register.StatusCode
        $registerBody = $register.Content
    } catch {
        $response = $_.Exception.Response
        if ($null -ne $response) {
            $registerStatus = [int]$response.StatusCode
            $registerBody = $_.ErrorDetails.Message
        } else {
            $registerStatus = -1
            $registerBody = $_.Exception.Message
        }
    }
    Start-Sleep -Seconds 2
    $rowsAfter = Get-UserRowCount
    $wroteRow = $registerStatus -eq 200 -and $registerBody -match '"code"\s*:\s*0' -and $rowsAfter -eq $rowsBefore + 1
    Assert $wroteRow 'A3-sql-probe-wrote-a-row' "register status=$registerStatus body=$registerBody rows=$rowsBefore->$rowsAfter"
    $backendLog = & docker compose -f $composeYml -p $project --env-file $envFile logs backend 2>&1 | Out-String
    $sqlLeak = ($backendLog -match 'Parameters:') -or ($backendLog -match 'Preparing:')
    # 只有在写 SQL 确实发生的前提下，「没有参数日志」才算有效证据。
    Assert ($wroteRow -and -not $sqlLeak) 'A3-no-sql-param-logging' 'backend log must not contain MyBatis SQL parameter lines once a write is confirmed'
    $secretLeak = $backendLog.Contains($dbPassword) -or $backendLog.Contains($redisPassword)
    Assert (-not $secretLeak) 'A3-no-secret-in-logs' 'backend log must not contain injected secrets'

    # ---- 验收 3b：宿主访问覆盖栈实跑（不再只是 compose config 静态核对）----
    # 用独立 project 与随机回环端口启动「默认栈 + host-access 覆盖」，实际验证
    # 覆盖文件只发布回环非默认端口，且从非回环地址连不通。不触碰主候选的依赖。
    Write-Host '=== extra: host-access override stack ==='
    $hostPostgresPort = Free-Port
    $hostRedisPort = Free-Port
    while ($hostRedisPort -eq $hostPostgresPort) { $hostRedisPort = Free-Port }
    $hostProject = "vs45-host-$nonce"
    $hostAccessYml = Join-Path $repo 'docker-compose.host-access.yml'
    $hostEnvFile = Join-Path $runDir '.env.host-access'
    @"
POSTGRES_SUPERUSER_PASSWORD=$superuserPassword
POSTGRES_MIGRATOR_PASSWORD=$migratorPassword
POSTGRES_DB=vs45_host_$nonce
SPRING_DATASOURCE_PASSWORD=$dbPassword
REDIS_PASSWORD=$redisPassword
HOST_POSTGRES_PORT=$hostPostgresPort
HOST_REDIS_PORT=$hostRedisPort
SPRING_AI_DASHSCOPE_API_KEY=$dashscopeKey
SPRING_AI_OPENAI_API_KEY=$dashscopeKey
# 模型端点与模型名：生产守卫要求这两项非空。指向假端点，容器内不会有任何外呼。
SPRING_AI_OPENAI_BASE_URL=http://openai-fixture.invalid/v1
SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=fixture-model
ALI_YUN_AI_API_KEY=$dashscopeKey
VISION_SPACE_PEXELS_API_KEY=$dashscopeKey
VISION_SPACE_VISION_RECOGNITION_API_KEY=$dashscopeKey
VISION_SPACE_DEFAULT_PASSWORD=$defaultPassword
"@ | Set-Content -LiteralPath $hostEnvFile -NoNewline
    try {
        & docker compose -f $composeYml -f $hostAccessYml -p $hostProject --env-file $hostEnvFile up -d postgres redis *> (Join-Path $runDir 'host-access-up.log')
        if ($LASTEXITCODE -ne 0) { throw 'host-access override stack failed to start' }
        $hostDeadline = [DateTime]::UtcNow.AddSeconds(120)
        $hostReady = $false
        while ([DateTime]::UtcNow -lt $hostDeadline) {
            $postgresState = & docker inspect --format '{{.State.Health.Status}}' "$hostProject-postgres-1" 2>$null
            $redisState = & docker inspect --format '{{.State.Health.Status}}' "$hostProject-redis-1" 2>$null
            if ($postgresState -eq 'healthy' -and $redisState -eq 'healthy') { $hostReady = $true; break }
            Start-Sleep -Seconds 3
        }
        if (-not $hostReady) { throw 'host-access override dependencies did not become healthy' }
        $hostPortsRaw = & docker compose -f $composeYml -f $hostAccessYml -p $hostProject --env-file $hostEnvFile ps --format json 2>$null
        $hostPortsJson = @($hostPortsRaw | ForEach-Object { $_ | ConvertFrom-Json })
        $postgresHostPub = @($hostPortsJson | Where-Object { $_.Service -eq 'postgres' } | ForEach-Object { $_.Publishers } | Where-Object { $_ -and $_.PublishedPort -gt 0 })
        $redisHostPub = @($hostPortsJson | Where-Object { $_.Service -eq 'redis' } | ForEach-Object { $_.Publishers } | Where-Object { $_ -and $_.PublishedPort -gt 0 })
        $hostOk = ($postgresHostPub.Count -eq 1) -and ("$($postgresHostPub[0].URL)" -eq '127.0.0.1') -and
            ($postgresHostPub[0].PublishedPort -eq $hostPostgresPort) -and ($postgresHostPub[0].TargetPort -eq 5432) -and
            ($redisHostPub.Count -eq 1) -and ("$($redisHostPub[0].URL)" -eq '127.0.0.1') -and
            ($redisHostPub[0].PublishedPort -eq $hostRedisPort) -and ($redisHostPub[0].TargetPort -eq 6379)
        Assert $hostOk 'A2-host-access-loopback-and-nondefault' "postgres=$($postgresHostPub | ConvertTo-Json -Compress) redis=$($redisHostPub | ConvertTo-Json -Compress)"
        Assert (Test-TcpConnect '127.0.0.1' $hostPostgresPort) 'A2-host-access-postgres-loopback-reachable' 'the explicitly published PostgreSQL port must accept connections'
        Assert (Test-TcpConnect '127.0.0.1' $hostRedisPort) 'A2-host-access-redis-loopback-reachable' 'the explicitly published Redis port must accept connections'
        # 从非回环的本机地址连接必须失败：只绑 127.0.0.1 的运行证据。
        $nonLoopback = @()
        try {
            $nonLoopback = @([Net.Dns]::GetHostAddresses([Net.Dns]::GetHostName()) |
                Where-Object { $_.AddressFamily -eq 'InterNetwork' -and -not [Net.IPAddress]::IsLoopback($_) } |
                Select-Object -First 1)
        } catch {
            Write-Host "non-loopback address lookup failed: $_"
        }
        if ($nonLoopback.Count -gt 0) {
            $lanIp = $nonLoopback[0].ToString()
            Assert (-not (Test-TcpConnect $lanIp $hostPostgresPort)) 'A2-host-access-postgres-lan-refused' "tcp connect to $lanIp`:$hostPostgresPort unexpectedly succeeded"
            Assert (-not (Test-TcpConnect $lanIp $hostRedisPort)) 'A2-host-access-redis-lan-refused' "tcp connect to $lanIp`:$hostRedisPort unexpectedly succeeded"
        } else {
            Write-Host 'SKIP: no non-loopback IPv4 address on this runner; LAN refusal not probed'
        }
    } finally {
        $results['host-access-cleanup'] = 'FAIL'
        try {
            if (-not (Remove-ContainerCandidate $hostProject $hostEnvFile $hostAccessYml)) {
                throw 'host-access override stack cleanup failed'
            }
            $results['host-access-cleanup'] = 'PASS'
        } finally {
            Remove-Item -LiteralPath $hostEnvFile -Force -ErrorAction SilentlyContinue
        }
    }

    # ---- 验收 4：健康状态与依赖故障（PostgreSQL/Redis 故障、恢复、延迟启动、重试耗尽）----
    Write-Host '=== extra: dependency failure, recovery & retry exhaustion ==='
    Assert ((Get-BackendHealth) -eq 'healthy') 'A4-healthy-before-failure' 'readiness healthy before failure'

    Invoke-Compose @('stop', 'postgres') *> (Join-Path $runDir 'postgres-stop.log')
    # 窗口要覆盖容器探活的重试周期（interval 10s × retries 6）加上单次探活的
    # 阻塞时间；90 秒会卡在边界上误判，150 秒留出余量。
    Assert (Wait-Health 'unhealthy' 150) 'A4-readiness-down-on-postgres-stop' 'readiness must report unhealthy after postgres stops'

    Invoke-Compose @('start', 'postgres') *> (Join-Path $runDir 'postgres-start.log')
    # 恢复等待必须等到 healthy 或超时：unhealthy 是过渡态，不能一次判负
    Assert (Wait-Health 'healthy' 180) 'A4-recovers-after-postgres-restart' 'readiness must return healthy after postgres restarts'

    # Redis 故障同样必须反映到 readiness（redis 组件在 readiness 组内）
    Invoke-Compose @('stop', 'redis') *> (Join-Path $runDir 'redis-stop.log')
    Assert (Wait-Health 'unhealthy' 150) 'A4-readiness-down-on-redis-stop' 'readiness must report unhealthy after redis stops'

    Invoke-Compose @('start', 'redis') *> (Join-Path $runDir 'redis-start.log')
    Assert (Wait-Health 'healthy' 180) 'A4-recovers-after-redis-restart' 'readiness must return healthy after redis restarts'

    # 依赖延迟启动：仅对这两个隔离启动实验关闭 Docker 自动重启，核对同一
    # 应用进程进入连接阶段、等待依赖并恢复，避免崩溃重启替代应用重试。
    # 只停 PostgreSQL：Redis 由 Redisson 在启动早期连接，Redis 缺失会让应用在
    # 到达 Hikari 之前明确失败，无法验证「有限等待后恢复」这条路径。
    & docker update --restart=no "$project-backend-1" *> (Join-Path $runDir 'backend-restart-policy.log')
    if ($LASTEXITCODE -ne 0) { throw 'could not disable automatic restarts for the startup experiments' }
    Invoke-Compose @('stop', 'postgres') *> (Join-Path $runDir 'deps-stop.log')
    Invoke-Compose @('restart', '--no-deps', 'backend') *> (Join-Path $runDir 'backend-restart.log')
    $delayedStart = Get-ContainerInspection 'backend'
    $connectionObserved = Wait-BackendConnectionAttempt $delayedStart 60
    Assert $connectionObserved 'A4-late-connection-attempt-observed' 'this process must enter the Hikari connection stage'
    if ($connectionObserved) { Start-Sleep -Seconds 5 }
    $waiting = Get-ContainerInspection 'backend'
    $sameWaitingProcess = $waiting.State.Running -and
        $waiting.State.StartedAt -eq $delayedStart.State.StartedAt -and $waiting.RestartCount -eq $delayedStart.RestartCount
    Assert $sameWaitingProcess 'A4-late-same-process-waiting' 'backend must remain in the same process while the dependency is absent'
    $dependency = Get-ContainerInspection 'postgres'
    Assert (-not $dependency.State.Running -and -not $dependency.State.Restarting) 'A4-late-postgres-stopped' 'postgres must remain stopped during the delay'
    Invoke-Compose @('start', 'postgres') *> (Join-Path $runDir 'deps-restart.log')
    Assert (Wait-Health 'healthy' 240) 'A4-late-dependency-recovers' 'backend must reach healthy once the dependency starts late'
    $recovered = Get-ContainerInspection 'backend'
    $sameRecoveredProcess = $recovered.State.Running -and
        $recovered.State.StartedAt -eq $delayedStart.State.StartedAt -and $recovered.RestartCount -eq $delayedStart.RestartCount
    Assert $sameRecoveredProcess 'A4-late-same-process-recovered' 'dependency recovery must not replace the application process'

    # 重试耗尽：依赖持续缺失时应用必须明确失败退出（退出码非 0），而非带病运行
    Invoke-Compose @('stop', 'postgres', 'redis') *> (Join-Path $runDir 'deps-stop2.log')
    Invoke-Compose @('stop', 'backend') *> (Join-Path $runDir 'backend-stop2.log')
    $beforeExhaustion = Get-ContainerInspection 'backend'
    # 直接启动既有容器，绝不由 compose up 重新拉起依赖；启动命令本身必须成功。
    # 应用的预期非 0 退出通过后续 inspect 观察，不以 Compose 超时作为退出证据。
    & docker start "$project-backend-1" *> (Join-Path $runDir 'backend-exhaust.log')
    if ($LASTEXITCODE -ne 0) { throw 'could not start backend for retry exhaustion' }
    $exited = Wait-BackendExit 150
    $clearFailure = $null -ne $exited -and -not $exited.State.Running -and -not $exited.State.Restarting -and
        $exited.State.ExitCode -ne 0 -and $exited.State.StartedAt -ne $beforeExhaustion.State.StartedAt -and
        $exited.RestartCount -eq $beforeExhaustion.RestartCount
    Assert $clearFailure 'A4-exhausted-retries-fail-clearly' 'backend must exit nonzero within 150s; timeout, zero exit and missing inspect evidence cannot pass'
    foreach ($service in @('postgres', 'redis')) {
        $dependency = Get-ContainerInspection $service
        Assert (-not $dependency.State.Running -and -not $dependency.State.Restarting) "A4-exhausted-$service-stopped" 'dependency must remain stopped throughout retry exhaustion'
    }

    Write-Host "=== summary (artifacts: $runDir) ==="
    $failed = @($results.GetEnumerator() | Where-Object { $_.Value -ne 'PASS' })
    if ($failed.Count -eq 0) {
        $results['execution'] = 'PASS'
        Write-Host 'ALL PASS'
        exit 0
    }
    $results['execution'] = 'FAIL'
    Write-Host "FAILED: $($failed.Name -join ', ')"
    exit 1
} catch {
    $results['execution'] = 'BLOCKED_ENV'
    Write-Host "BLOCKED_ENV/ERROR: $_"
    Write-Host "local diagnostics: $runDir"
    exit 2
} finally {
    $results['cleanup'] = 'NOT_RUN'
    if ($dockerAvailable) {
        Write-Host '=== cleanup: removing only this run''s resources ==='
        if ($results['execution'] -ne 'PASS') {
            try { Write-StartupSnapshot } catch {
                Add-Content -LiteralPath $startupDiagnostic 'snapshot status=unavailable' -ErrorAction SilentlyContinue
            }
        }
        # 清理前保留完整日志，供本地排查。
        foreach ($service in @('backend', 'postgres', 'redis')) {
            try { [void](Invoke-BoundedCompose @('logs', '--no-color', $service) (Join-Path $runDir "$service.log") 10) } catch {
                Write-Host "local logs unavailable: $service"
            }
        }
        $results['cleanup'] = 'FAIL'
        try {
            if ((Remove-ContainerCandidate) -and $results['host-access-cleanup'] -ne 'FAIL') {
                $results['cleanup'] = 'PASS'
            }
        } catch { Write-Host 'container cleanup unavailable' }
    }
    Remove-Item -LiteralPath $envFile -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $diagnosticsDir | Out-Null
    Copy-Item -LiteralPath $startupDiagnostic -Destination $diagnosticsDir
    @{ head = (git rev-parse HEAD); runId = $(if ($env:VS_SRF_RUN_ID) { $env:VS_SRF_RUN_ID } else { [Guid]::NewGuid().ToString('N') }); infrastructure = 'docker'; results = $results; productionAcceptance = 'NOT_RUN' } |
        ConvertTo-Json | Set-Content (Join-Path $reportDir 'summary.json')
    if ($results['cleanup'] -eq 'FAIL') { exit 1 }
}
