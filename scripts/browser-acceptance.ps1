[CmdletBinding()]
param(
    # Optional Windows binaries; omit both to use dedicated Docker containers.
    [string]$MySqlBin,
    [string]$RedisBin,
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
# 退出码：0 通过；1 断言失败；2 BLOCKED_ENV（缺依赖/浏览器/隔离服务）。

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path $PSScriptRoot -Parent
$nonce = [Guid]::NewGuid().ToString('N')
$runDir = Join-Path ([IO.Path]::GetTempPath()) "visionspace-browser-$nonce"
$database = "vs_browser_$nonce"
$results = [ordered]@{}
$ownedProcesses = @()
$ownedContainers = @()
$savedEnvironment = @{}
$environmentNames = @(
    'VS_ACCEPTANCE_NONCE', 'VS_ACCEPTANCE_MYSQL_PORT', 'VS_ACCEPTANCE_REDIS_PORT', 'MYSQL_PWD',
    'VS_E2E_API_TARGET', 'VS_E2E_HTTPS_CERT', 'VS_E2E_HTTPS_KEY', 'VS_E2E_ARTIFACTS'
)
foreach ($name in $environmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name) }

function Free-Port {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}

function Wait-Port([int]$Port) {
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        $client = [Net.Sockets.TcpClient]::new()
        try { $client.Connect('127.0.0.1', $Port); return } catch { Start-Sleep -Milliseconds 500 } finally { $client.Dispose() }
    }
    throw "BLOCKED_ENV: isolated service did not open port $Port"
}

# PATH 上的 java 可能是收尾不完整的转发器（例如 Oracle javapath 在没有注册表项时
# 静默退出），Start-Process 会因此立刻结束且不写日志。这里显式优先 JAVA_HOME，
# 并拒绝无法启动的候选，避免把「java 起不来」误报成「后端启动超时」。
function Resolve-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin/java.exe') }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $candidates += $command.Source }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not $candidate -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $probe = Join-Path $runDir 'java-probe.log'
        $process = Start-Process -FilePath $candidate -ArgumentList @('-version') -PassThru -WindowStyle Hidden -RedirectStandardOutput ($probe + '.out') -RedirectStandardError ($probe + '.err')
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
    if ([bool]$MySqlBin -ne [bool]$RedisBin) { throw 'Specify both MySqlBin and RedisBin, or neither.' }

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
    & openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj '/CN=127.0.0.1' `
        -addext 'subjectAltName=IP:127.0.0.1' -keyout $keyPath -out $certPath *> (Join-Path $runDir 'cert.log')
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: openssl unavailable for a local TLS certificate' }

    # 隔离 MySQL 与 Redis
    $mysqlPort = Free-Port
    $redisPort = Free-Port
    while ($redisPort -eq $mysqlPort -or $mysqlPort -lt 10000 -or $redisPort -lt 10000) {
        $mysqlPort = Free-Port
        $redisPort = Free-Port
    }
    $sourceDdl = Get-Content -Raw -LiteralPath (Join-Path $repo 'sql/create_vision_space.sql')
    $tables = [regex]::Matches($sourceDdl, '(?ms)^CREATE TABLE .*?^\)[^\r\n]*;')
    # 表数量变化必须人工复核：#38 新增幂等台账 picture_action_event_dedupe，
    # #41 新增 agent_run_state / agent_action_record，
    # #35 新增 agent_session_attachment / agent_legacy_attachment，表数由 12 变为 17。
    # #36 新增 feedback_attachment / resource_access_grant / resource_isolation，表数由 17 变为 20。
    if ($tables.Count -ne 20) { throw 'FAIL: schema changed; review acceptance bootstrap before running.' }
    $ddl = ($tables | ForEach-Object { $_.Value }) -join "`n"
    $bootstrap = @"
CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'vs_acceptance'@'%' IDENTIFIED BY '$nonce';
GRANT ALL ON $database.* TO 'vs_acceptance'@'%';
USE $database;
CREATE TABLE acceptance_instance (nonce varchar(32) NOT NULL);
INSERT INTO acceptance_instance VALUES ('$nonce');
$ddl
"@
    $bootstrapPath = Join-Path $runDir 'bootstrap.sql'
    [IO.File]::WriteAllText($bootstrapPath, $bootstrap, [Text.UTF8Encoding]::new($false))
    if ($MySqlBin) {
        if (-not $IsWindows) { throw 'Native binaries mode requires Windows.' }
        $mysqld = Join-Path $MySqlBin 'mysqld.exe'
        $mysql = Join-Path $MySqlBin 'mysql.exe'
        $redisServer = Join-Path $RedisBin 'redis-server.exe'
        $redisCli = Join-Path $RedisBin 'redis-cli.exe'
        foreach ($file in @($mysqld, $mysql, $redisServer, $redisCli)) {
            if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing executable: $file" }
        }
        $dataDir = Join-Path $runDir 'mysql'
        & $mysqld --no-defaults --initialize-insecure "--datadir=$dataDir" --console *> (Join-Path $runDir 'mysql-init.log')
        if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL initialization failed.' }
        $ownedProcesses += Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults', "--datadir=`"$dataDir`"", '--bind-address=127.0.0.1', "--port=$mysqlPort", '--mysqlx=OFF', '--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'mysql-out.log') -RedirectStandardError (Join-Path $runDir 'mysql-error.log')
        $ownedProcesses += Start-Process -FilePath $redisServer -ArgumentList @('--bind', '127.0.0.1', '--port', $redisPort, '--requirepass', $nonce, '--save', '""', '--appendonly', 'no', '--dir', "`"$runDir`"") -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'redis-out.log') -RedirectStandardError (Join-Path $runDir 'redis-error.log')
        Wait-Port $mysqlPort
        Wait-Port $redisPort
        $env:MYSQL_PWD = ''
        $bootstrap | & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=root --default-character-set=utf8mb4 *> (Join-Path $runDir 'bootstrap.log')
        if ($LASTEXITCODE -ne 0) { throw 'FAIL: isolated schema bootstrap failed.' }
        & $redisCli -h 127.0.0.1 -p $redisPort -a $nonce -n 15 SET acceptance:instance $nonce *> (Join-Path $runDir 'redis-marker.log')
        if ($LASTEXITCODE -ne 0) { throw 'Redis marker creation failed.' }
    } else {
        & docker info *> (Join-Path $runDir 'docker-info.log')
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: Docker engine unavailable. Provide MySqlBin/RedisBin on Windows.' }
        $mysqlContainer = "vs-browser-mysql-$nonce"
        $redisContainer = "vs-browser-redis-$nonce"
        $ownedContainers += $mysqlContainer
        & docker run -d --name $mysqlContainer -p "127.0.0.1:${mysqlPort}:3306" -e "MYSQL_ROOT_PASSWORD=$nonce" --mount "type=bind,source=$bootstrapPath,target=/docker-entrypoint-initdb.d/acceptance.sql,readonly" mysql:8.0.36 *> (Join-Path $runDir 'mysql-container.log')
        if ($LASTEXITCODE -ne 0) { throw 'MySQL container startup failed.' }
        $ownedContainers += $redisContainer
        & docker run -d --name $redisContainer -p "127.0.0.1:${redisPort}:6379" redis:7.2.4 redis-server --requirepass $nonce *> (Join-Path $runDir 'redis-container.log')
        if ($LASTEXITCODE -ne 0) { throw 'Redis container startup failed.' }
        Wait-Port $mysqlPort
        Wait-Port $redisPort
        & docker exec $redisContainer redis-cli -a $nonce -n 15 SET acceptance:instance $nonce *> (Join-Path $runDir 'redis-marker.log')
        if ($LASTEXITCODE -ne 0) { throw 'Redis marker creation failed.' }
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
        '-jar', (Join-Path $repo 'target/vision-space-0.0.1-SNAPSHOT.jar'),
        '--spring.profiles.active=browser',
        "--spring.config.additional-location=file:$(Join-Path $repo 'e2e/application-browser.yml')",
        "--server.port=$backendPort",
        "--spring.datasource.url=jdbc:mysql://127.0.0.1:$mysqlPort/$database`?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
        '--spring.datasource.username=vs_acceptance', "--spring.datasource.password=$nonce",
        '--spring.data.redis.host=127.0.0.1', "--spring.data.redis.port=$redisPort",
        "--spring.data.redis.password=$nonce", '--spring.data.redis.database=15'
    )
    $java = Resolve-Java
    $backendProcess = Start-Process -FilePath $java -ArgumentList $backendArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDir 'backend.log') -RedirectStandardError (Join-Path $runDir 'backend-error.log')
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
    if ($MySqlBin -and $ownedProcesses.Count -gt 0) {
        try {
            $ownedServers = @(Get-CimInstance Win32_Process -Filter "Name = 'mysqld.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($dataDir) })
            if ($ownedServers.Count -gt 0) {
                $env:MYSQL_PWD = ''
                $actualDataDir = & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=root --batch --skip-column-names --execute='SELECT @@datadir' 2> (Join-Path $runDir 'mysql-cleanup.log')
                if ($LASTEXITCODE -eq 0 -and [IO.Path]::GetFullPath("$actualDataDir").TrimEnd('\', '/') -eq [IO.Path]::GetFullPath($dataDir).TrimEnd('\', '/')) {
                    & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=root --execute='SHUTDOWN' >> (Join-Path $runDir 'mysql-cleanup.log')
                    if ($LASTEXITCODE -ne 0) { $results['cleanup'] = 'FAIL' }
                    foreach ($server in $ownedServers) {
                        if (Get-Process -Id $server.ProcessId -ErrorAction SilentlyContinue) {
                            try { Wait-Process -Id $server.ProcessId -Timeout 20 -ErrorAction Stop } catch { $results['cleanup'] = 'FAIL' }
                        }
                    }
                } else { $results['cleanup'] = 'FAIL' }
            }
        } catch { $results['cleanup'] = 'FAIL' }
    }
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
    foreach ($container in $ownedContainers) {
        & docker rm -f -v $container *> $null
        if ($LASTEXITCODE -ne 0) { $results['cleanup'] = 'FAIL' }
    }
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name]) }

    $summary = [ordered]@{
        head = (git rev-parse HEAD)
        infrastructure = $(if ($MySqlBin) { 'native-explicit' } else { 'docker' })
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
if ($results.Values -contains 'BLOCKED_ENV') { exit 2 }
if ($failed) { exit 1 }
exit 0
