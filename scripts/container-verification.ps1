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

# 所有 compose 调用统一走这里：固定 -f / -p / --env-file，避免漏带
function Compose([string[]]$ExtraArgs) {
    & docker compose -f $composeYml -p $project --env-file $envFile @ExtraArgs
    return $LASTEXITCODE
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

function Wait-Readiness([int]$TimeoutSeconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $state = (& docker inspect --format '{{.State.Health.Status}}' "$project-backend-1" 2>$null)
        if ($state -eq 'healthy') { return $true }
        if ($state -eq 'unhealthy') { return $false }
        Start-Sleep -Seconds 3
    }
    return $false
}

$results = [ordered]@{}
$backendPort = Free-Port

# ---- 随机测试秘密：写入忽略的临时文件，不回显 ----
$secret = {
    param($len) $bytes = [byte[]]::new($len); [Security.Cryptography.RandomNumberGenerator]::Fill($bytes);
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Substring(0, $len)
}
$mysqlRoot = & $secret 24
$dbPassword = & $secret 24
$redisPassword = & $secret 24
$dashscopeKey = "test-" + (& $secret 20)
$defaultPassword = & $secret 20

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
        Compose @('build', '--pull=false') *> (Join-Path $runDir 'build.log')
        if ($LASTEXITCODE -ne 0) { throw "image build failed (see build.log)" }
    }
    Compose @('up', '-d') *> (Join-Path $runDir 'up.log')
    if ($LASTEXITCODE -ne 0) { throw "compose up failed (see up.log)" }

    # ---- 验收 1：容器实际 UID 与写权限 ----
    Write-Host '=== [2/4] identity & writable paths ==='
    $uid = (Compose @('exec', '-T', 'backend', 'sh', '-c', 'id -u') | Out-String).Trim()
    Assert ($uid -eq '10001') 'A1-backend-nonroot-uid' "uid=$uid"

    $writeTmp = Compose @('exec', '-T', 'backend', 'sh', '-c', 'touch /app/tmp/probe && rm /app/tmp/probe && echo OK') 2>$null
    Assert ("$writeTmp".Trim() -eq 'OK') 'A1-tmp-writable' 'write to /app/tmp'

    $writeApp = Compose @('exec', '-T', 'backend', 'sh', '-c', 'touch /app/forbidden 2>/dev/null && echo WROTE || echo DENIED') 2>$null
    Assert ("$writeApp".Trim() -eq 'DENIED') 'A1-app-readonly' 'write to /app must be denied'

    $writeRoot = Compose @('exec', '-T', 'backend', 'sh', '-c', 'touch /forbidden 2>/dev/null && echo WROTE || echo DENIED') 2>$null
    Assert ("$writeRoot".Trim() -eq 'DENIED') 'A1-root-readonly' 'write to / must be denied'

    # ---- 验收 2：端口发布与网络可达范围 ----
    Write-Host '=== [3/4] port publishing & network reach ==='
    $portsRaw = Compose @('ps', '--format', 'json') | Out-String
    $portsJson = $portsRaw | ConvertFrom-Json
    $published = @($portsJson | ForEach-Object { $_.Publishers } | Where-Object { $_ })
    $mysqlPublished = @($published | Where-Object { $_.TargetPort -eq 3306 -or $_.PublishedPort -eq 3306 })
    $redisPublished = @($published | Where-Object { $_.TargetPort -eq 6379 -or $_.PublishedPort -eq 6379 })
    $backendPub = @($published | Where-Object { $_.TargetPort -eq 8081 })
    Assert (($mysqlPublished.Count -eq 0) -and ($redisPublished.Count -eq 0)) 'A2-db-redis-not-published' 'mysql/redis must publish no ports'
    $backOk = ($backendPub.Count -eq 1) -and ($backendPub[0].PublishedPort -eq $backendPort) -and ("$($backendPub[0].HostIp)" -eq '127.0.0.1')
    Assert $backOk 'A2-backend-loopback-only' "backend published: $($backendPub | ConvertTo-Json -Compress)"

    $netInspect = & docker network inspect "$($project)_internal" --format '{{.Internal}}' 2>$null
    Assert ("$netInspect" -eq 'true') 'A2-internal-network-isolated' "internal network Internal=$netInspect"

    $hostListen = (netstat -an | Select-String ":3306\s|:6379\s" | Measure-Object).Count
    Assert ($hostListen -eq 0) 'A2-host-no-db-listener' "host listeners on 3306/6379: $hostListen"

    # ---- 验收 3：健康端点不泄露秘密 + profile 与日志卫生 ----
    Write-Host '=== [4/4] profile, log hygiene & health ==='
    $activeProfile = (Compose @('exec', '-T', 'backend', 'sh', '-c', 'echo $SPRING_PROFILES_ACTIVE') | Out-String).Trim()
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
    $backendLog = Compose @('logs', 'backend') 2>&1 | Out-String
    $sqlLeak = ($backendLog -match 'Parameters:') -or ($backendLog -match 'Preparing:')
    Assert (-not $sqlLeak) 'A3-no-sql-param-logging' 'backend log must not contain MyBatis SQL parameter lines'
    $secretLeak = $backendLog.Contains($dbPassword) -or $backendLog.Contains($redisPassword)
    Assert (-not $secretLeak) 'A3-no-secret-in-logs' 'backend log must not contain injected secrets'

    # ---- 验收 4：健康状态与依赖故障 ----
    Write-Host '=== extra: dependency failure & recovery ==='
    $healthyBefore = Wait-Readiness 5
    Assert $healthyBefore 'A4-healthy-before-failure' 'readiness healthy before failure'

    Compose @('stop', 'mysql') *> (Join-Path $runDir 'mysql-stop.log')
    $unhealthyAfter = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        $state = & docker inspect --format '{{.State.Health.Status}}' "$project-backend-1" 2>$null
        if ($state -eq 'unhealthy') { $unhealthyAfter = $true; break }
        Start-Sleep -Seconds 5
    }
    Assert $unhealthyAfter 'A4-readiness-down-on-mysql-stop' 'readiness must report unhealthy after mysql stops'

    Compose @('start', 'mysql') *> (Join-Path $runDir 'mysql-start.log')
    $recovered = Wait-Readiness 120
    Assert $recovered 'A4-recovers-after-mysql-restart' 'readiness must return healthy after mysql restarts'

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
