[CmdletBinding()]
param(
    # Skip `npm ci` when the worktree already has node_modules.
    [switch]$SkipInstall
)

# 浏览器真实链路验收（工单 32 / 原 30.4）。
#
# 复用验收脚本的隔离原则：随机端口、随机库名与凭据、只绑定回环地址、
# 只清理本次创建的进程与容器。在此之上追加：
#   - `npm run build-only` 的生产构建产物由本地同源入口提供
#   - 自签证书，使页面运行在 https 下，验证 wss 与 Secure Cookie
#   - playwright-core 驱动真实 Chromium，观察请求、Cookie 与最终结果
#
# 隔离服务全部走 Docker：postgres:18.7 + redis:7.2.4（原生二进制模式已移除）。
# 业务表只由 Flyway 以迁移角色 vision_migrator 创建，脚本不再自行执行 DDL；
# PostgreSQL 18 的数据目录版本化，卷挂在 /var/lib/postgresql。
#
# 退出码：0 通过；1 断言失败；2 BLOCKED_ENV（缺依赖/浏览器/隔离服务）。

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path $PSScriptRoot -Parent
$processWindow = @{}
if ($IsWindows) { $processWindow['WindowStyle'] = 'Hidden' }
Import-Module (Join-Path $repo 'scripts/acceptance-environment.psm1') -Force
$services = $null
$savedEnvironment = @{}
foreach ($entry in Get-ChildItem Env:) { $savedEnvironment[$entry.Name] = $entry.Value }
$nonce = [Guid]::NewGuid().ToString('N')
$runDir = Join-Path ([IO.Path]::GetTempPath()) "visionspace-browser-$nonce"
$database = "vs_browser_$nonce"
$results = [ordered]@{}
$ownedProcesses = @()
$environmentNames = @(
    'VS_ACCEPTANCE_NONCE', 'VS_ACCEPTANCE_PG_PORT', 'VS_ACCEPTANCE_REDIS_PORT',
    'VS_E2E_API_TARGET', 'VS_E2E_HTTPS_CERT', 'VS_E2E_HTTPS_KEY', 'VS_E2E_ARTIFACTS'
)
foreach ($name in $environmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name) }

function Free-Port {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}

# psql 裸值读取：口令经 PGPASSWORD 环境变量传入（不进 argv），SQL 走 stdin
# （避免 Windows 参数转义吃掉引号），输出用 -tA（tuples-only + unaligned），
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

# PATH 上的 java 可能是收尾不完整的转发器（例如 Oracle javapath 在没有注册表项时
# 静默退出），Start-Process 会因此立刻结束且不写日志。这里显式优先 JAVA_HOME，
# 并拒绝无法启动的候选，避免把「java 起不来」误报成「后端启动超时」。
function Resolve-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME $(if ($IsWindows) { 'bin/java.exe' } else { 'bin/java' })) }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $candidates += $command.Source }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not $candidate -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $probe = Join-Path $runDir 'java-probe.log'
        $process = Start-Process @processWindow -FilePath $candidate -ArgumentList @('-version') -PassThru -RedirectStandardOutput ($probe + '.out') -RedirectStandardError ($probe + '.err')
        if ($process.WaitForExit(30000) -and $process.ExitCode -eq 0) { return $candidate }
    }
    throw "BLOCKED_ENV: no working Java runtime found (JAVA_HOME=$($env:JAVA_HOME))"
}

function Wait-Http([string]$Url, $Process) {
    $deadline = [DateTime]::UtcNow.AddSeconds(180)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Process -and $Process.HasExited) {
            throw "backend process exited with code $($Process.ExitCode) before $Url became ready"
        }
        try {
            $response = Invoke-WebRequest -Uri $Url -SkipCertificateCheck -TimeoutSec 5 -UseBasicParsing
            if ($response.StatusCode -eq 200) { return }
        } catch {
            try {
                $response = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -UseBasicParsing
                if ($response.StatusCode -eq 200) { return }
            } catch { }
        }
        Start-Sleep -Seconds 2
    }
    throw "BLOCKED_ENV: backend did not become ready at $Url"
}

New-Item -ItemType Directory -Path $runDir | Out-Null
Push-Location $repo
$failed = $false
try {
    # 生产构建产物与自签证书
    Push-Location (Join-Path $repo 'frontend')
    try {
        if (-not $SkipInstall) {
            npm ci --no-audit --no-fund *> (Join-Path $runDir 'npm-ci.log')
            if ($LASTEXITCODE -ne 0) { throw 'frontend dependency install failed' }
        }
        npm run build-only *> (Join-Path $runDir 'frontend-build.log')
        if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }
        $playwrightCheck = node -e "import('playwright-core').then(()=>process.exit(0)).catch(()=>process.exit(3))" 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: playwright-core is not installed; run npm i -D playwright-core' }
    } finally { Pop-Location }

    $certPath = Join-Path $runDir 'entry.crt'
    $keyPath = Join-Path $runDir 'entry.key'
    $certConfig = Join-Path $runDir 'openssl.cnf'
    "[req]`ndistinguished_name = req_dn`n[req_dn]" | Set-Content $certConfig
    & openssl req -config $certConfig -x509 -newkey rsa:2048 -nodes -days 1 -subj '/CN=127.0.0.1' `
        -addext 'subjectAltName=IP:127.0.0.1' -keyout $keyPath -out $certPath *> (Join-Path $runDir 'cert.log')
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: openssl unavailable for a local TLS certificate' }

    # 隔离服务：postgres:18.7 + redis:7.2.4，随机端口、随机库名与凭据。
    # 业务表只由 Flyway（迁移角色 vision_migrator）创建，脚本不再执行 DDL。
    $services = Start-AcceptanceDockerServices -RunDirectory $runDir -Database $database -Nonce $nonce
    $postgresPort = $services.PostgresPort
    $redisPort = $services.RedisPort
    # 核对「库里建出的表数 = 基线里的 CREATE TABLE 数」，而不是手写一个期望值：
    # 基线变更后表数对不上必须人工复核，不能带着半套 schema 继续验收。
    # Flyway 自己的元数据表 flyway_schema_history 也建在 public 里，不算业务表。
    $baselineSql = Get-Content -Raw -LiteralPath (Join-Path $repo 'src/main/resources/db/migration/V1__baseline.sql')
    $expectedTables = [regex]::Matches($baselineSql, '(?m)^CREATE TABLE ').Count
    if ($expectedTables -eq 0) { throw 'FAIL: no CREATE TABLE statements found in the Flyway baseline.' }
    $tableListSql = "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'"
    $tableRows = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql $tableListSql -LogPath (Join-Path $runDir 'schema-count.log'))
    $createdTables = [int]$tableRows[0]
    if ($createdTables -ne $expectedTables) {
        throw "FAIL: acceptance schema incomplete (expected $expectedTables tables, found $createdTables)."
    }
    $results['isolation'] = 'PASS'

    # 后端在随机端口启动，数据源与 Redis 指向本轮实例。
    # 可执行 jar 由本轮构建产生，避免使用其他分支遗留的 target/ 产物。
    Push-Location $repo
    try {
        mvn -B --no-transfer-progress -DskipTests clean package *> (Join-Path $runDir 'mvn-package.log')
        if ($LASTEXITCODE -ne 0) { throw 'backend build failed' }
    } finally { Pop-Location }
    $backendPort = Free-Port
    $backendArgs = @(
        '-Duser.timezone=UTC',
        '-jar', (Join-Path $repo 'target/vision-space-0.0.1-SNAPSHOT.jar'),
        '--spring.profiles.active=browser',
        "--spring.config.additional-location=file:$(Join-Path $repo 'e2e/application-browser.yml')",
        "--server.port=$backendPort"
    ) + @(Get-AcceptanceApplicationArguments $postgresPort $redisPort $database $nonce)
    $java = Resolve-Java
    $backendProcess = Start-Process @processWindow -FilePath $java -ArgumentList $backendArgs -PassThru -RedirectStandardOutput (Join-Path $runDir 'backend.log') -RedirectStandardError (Join-Path $runDir 'backend-error.log')
    $ownedProcesses += $backendProcess
    try {
        Wait-Http "http://127.0.0.1:$backendPort/api/user/get/login" $backendProcess
    } catch {
        Get-Content -LiteralPath (Join-Path $runDir 'backend.log') -Tail 40 -ErrorAction SilentlyContinue | Write-Host
        Get-Content -LiteralPath (Join-Path $runDir 'backend-error.log') -Tail 40 -ErrorAction SilentlyContinue | Write-Host
        throw
    }
    $results['backend'] = 'PASS'

    $env:VS_E2E_API_TARGET = "http://127.0.0.1:$backendPort"
    $env:VS_E2E_HTTPS_CERT = $certPath
    $env:VS_E2E_HTTPS_KEY = $keyPath
    $env:VS_E2E_ARTIFACTS = (Join-Path $runDir 'artifacts')
    Push-Location (Join-Path $repo 'frontend')
    try {
        node e2e/browser-entry.mjs *> (Join-Path $runDir 'browser.log')
        $exit = $LASTEXITCODE
        Get-Content -LiteralPath (Join-Path $runDir 'browser.log') -Tail 40 | Write-Host
        if ($exit -ne 0) {
            # 失败时保留完整 e2e 日志到 artifacts（VS_E2E_ARTIFACTS 随 srf-browser 上传），
            # CI 证据脱敏只保留状态，完整日志是定位断言失败的唯一现场。
            New-Item -ItemType Directory -Force (Join-Path $env:VS_E2E_ARTIFACTS 'diagnostics') | Out-Null
            Copy-Item (Join-Path $runDir 'browser.log') (Join-Path $env:VS_E2E_ARTIFACTS 'diagnostics/browser-full.log')
        }
        if ($exit -eq 2) { throw 'BLOCKED_ENV: browser acceptance prerequisites missing' }
        if ($exit -ne 0) { throw 'browser acceptance assertions failed' }
        $results['browser'] = 'PASS'
    } finally { Pop-Location }
} catch {
    $failed = $true
    $message = $_.Exception.Message
    $results['result'] = if ($message.StartsWith('BLOCKED_ENV')) { 'BLOCKED_ENV' } else { 'FAIL' }
    $_ | Out-String | Set-Content (Join-Path $runDir 'error.log')
    Write-Host "ERROR: $message"
} finally {
    $results['cleanup'] = 'PASS'
    foreach ($process in $ownedProcesses) {
        try {
            if (-not $process.HasExited) {
                Stop-Process -Id $process.Id -ErrorAction Stop
                if (-not $process.WaitForExit(5000)) { throw 'Owned process did not exit.' }
            }
        } catch {
            $results['cleanup'] = 'FAIL'
            $_ | Out-String | Add-Content (Join-Path $runDir 'process-cleanup.log')
        }
    }
    # 隔离容器与命名卷都在本轮自建自删（Stop-AcceptanceDockerServices 负责卷）。
    if ($services) { try { Stop-AcceptanceDockerServices $services } catch { $results['cleanup'] = 'FAIL' } }
    foreach ($entry in @(Get-ChildItem Env:)) {
        if (-not $savedEnvironment.ContainsKey($entry.Name)) { [Environment]::SetEnvironmentVariable($entry.Name, $null) }
    }
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name]) }

    $summary = [ordered]@{
        head = (git rev-parse HEAD)
        runId = $(if ($env:VS_SRF_RUN_ID) { $env:VS_SRF_RUN_ID } else { [Guid]::NewGuid().ToString('N') })
        infrastructure = 'docker'
        results = $results
        logs = $runDir
        productionAcceptance = 'NOT_RUN'
    }
    $reportDir = Join-Path $repo "target/browser-acceptance/$nonce"
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
    $summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $reportDir 'summary.json')
    Copy-Item -Path (Join-Path $runDir '*.log') -Destination $reportDir -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath (Join-Path $runDir 'artifacts')) {
        Copy-Item -LiteralPath (Join-Path $runDir 'artifacts') -Destination $reportDir -Recurse
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $repo 'target/browser-acceptance/summary.json')
    $summary | ConvertTo-Json -Depth 5 | Write-Host
    Pop-Location
}
if ($results.Values -contains 'FAIL') { exit 1 }
if ($results.Values -contains 'BLOCKED_ENV') { exit 2 }
if ($failed) { exit 1 }
exit 0
