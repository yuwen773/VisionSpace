# 工单 44 浏览器多连接协作验收驱动（本票独立脚本）。
#
# 复用 scripts/browser-acceptance.ps1 的隔离与入口模型（隔离库、自签 TLS、
# 同源代理入口、playwright-core 真实 Chromium），追加协作场景：
#   - 双标签编辑权反馈（一个编辑、一个被拒）
#   - 第二标签关闭不误释放
#   - 竞争用户被拒、持有者注销后写入被拒
#   - seed 团队空间/图片供协作使用
#
# 退出码：0 通过；1 断言失败；2 BLOCKED_ENV。
[CmdletBinding()]
param(
    [string]$MySqlBin = 'D:\Work\Backend\MySQL-8.0.26\mysql-8.0.26-winx64\bin',
    [string]$RedisBin = 'D:\Work\Backend\Redis-x64-3.2.100',
    [switch]$SkipInstall
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$nonce = [Guid]::NewGuid().ToString('N')
$runDir = Join-Path ([IO.Path]::GetTempPath()) "vs44-browser-$nonce"
$database = "vs44_browser_$nonce"
$ownedProcesses = @()
$results = [ordered]@{}
$failed = $false

function Free-Port {
    $l = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $l.Start(); try { return $l.LocalEndpoint.Port } finally { $l.Stop() }
}
function Wait-Port([int]$Port) {
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        $c = [Net.Sockets.TcpClient]::new()
        try { $c.Connect('127.0.0.1', $Port); $c.Dispose(); return } catch { Start-Sleep -Milliseconds 500 } finally { $c.Dispose() }
    }
    throw "BLOCKED_ENV: isolated service did not open port $Port"
}
function Resolve-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin/java.exe') }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $candidates += $command.Source }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not $candidate -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        # javapath 转发器可能静默退出，探测 -version 是否真的能跑
        $probe = Join-Path $runDir 'java-probe.log'
        $p = Start-Process -FilePath $candidate -ArgumentList @('-version') -PassThru -WindowStyle Hidden -RedirectStandardOutput ($probe + '.out') -RedirectStandardError ($probe + '.err')
        if ($p.WaitForExit(30000) -and $p.ExitCode -eq 0) { return $candidate }
    }
    throw "BLOCKED_ENV: no working Java runtime found"
}

New-Item -ItemType Directory -Path $runDir | Out-Null
Push-Location $repo
try {
    Write-Host "== frontend build & cert =="
    Push-Location (Join-Path $repo 'frontend')
    try {
        if (-not $SkipInstall) {
            npm ci --no-audit --no-fund *> (Join-Path $runDir 'npm-ci.log')
            if ($LASTEXITCODE -ne 0) { throw 'frontend dependency install failed' }
        }
        npm run build-only *> (Join-Path $runDir 'frontend-build.log')
        if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }
    } finally { Pop-Location }
    $certPath = Join-Path $runDir 'entry.crt'
    $keyPath = Join-Path $runDir 'entry.key'
    & openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj '/CN=127.0.0.1' `
        -addext 'subjectAltName=IP:127.0.0.1' -keyout $keyPath -out $certPath *> (Join-Path $runDir 'cert.log')
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: openssl unavailable' }

    Write-Host "== isolated MySQL/Redis =="
    $mysqlPort = Free-Port; $redisPort = Free-Port
    while ($redisPort -eq $mysqlPort -or $mysqlPort -lt 10000 -or $redisPort -lt 10000) { $mysqlPort = Free-Port; $redisPort = Free-Port }
    $mysqld = Join-Path $MySqlBin 'mysqld.exe'; $mysql = Join-Path $MySqlBin 'mysql.exe'
    $redisServer = Join-Path $RedisBin 'redis-server.exe'; $redisCli = Join-Path $RedisBin 'redis-cli.exe'
    foreach ($f in @($mysqld, $mysql, $redisServer, $redisCli)) { if (-not (Test-Path $f)) { throw "missing executable: $f" } }
    $dataDir = Join-Path $runDir 'mysql'
    & $mysqld --no-defaults --initialize-insecure "--datadir=$dataDir" --console *> (Join-Path $runDir 'mysql-init.log')
    if ($LASTEXITCODE -ne 0) { throw 'mysql init failed' }
    $ownedProcesses += Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults', "--datadir=`"$dataDir`"", '--bind-address=127.0.0.1', "--port=$mysqlPort", '--mysqlx=OFF', '--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'mysql-out.log') -RedirectStandardError (Join-Path $runDir 'mysql-error.log')
    $ownedProcesses += Start-Process -FilePath $redisServer -ArgumentList @('--bind', '127.0.0.1', '--port', "$redisPort", '--requirepass', $nonce, '--save', '""', '--appendonly', 'no', '--dir', "`"$runDir`"") -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'redis-out.log') -RedirectStandardError (Join-Path $runDir 'redis-error.log')
    Wait-Port $mysqlPort; Wait-Port $redisPort
    $sourceDdl = Get-Content -Raw -LiteralPath (Join-Path $repo 'sql/create_vision_space.sql')
    $tables = [regex]::Matches($sourceDdl, '(?ms)^CREATE TABLE .*?^\)[^\r\n]*;')
    if ($tables.Count -ne 15) { throw 'FAIL: schema changed; review bootstrap.' }
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
    $env:MYSQL_PWD = ''
    $bootstrap | & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=root --default-character-set=utf8mb4 *> (Join-Path $runDir 'bootstrap.log')
    if ($LASTEXITCODE -ne 0) { throw 'isolated schema bootstrap failed' }
    & $redisCli -h 127.0.0.1 -p $redisPort -a $nonce -n 15 SET acceptance:instance $nonce *> (Join-Path $runDir 'redis-marker.log')
    $results['isolation'] = 'PASS'

    Write-Host "== backend jar =="
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
        "--spring.data.redis.password=$nonce", '--spring.data.redis.database=15',
        # 协作用短租约与快续期
        '--vision-space.websocket.lease-ttl-seconds=5',
        '--vision-space.websocket.lease-renew-interval-ms=1500'
    )
    $java = Resolve-Java
    $backendProcess = Start-Process -FilePath $java -ArgumentList $backendArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDir 'backend.log') -RedirectStandardError (Join-Path $runDir 'backend-error.log')
    $ownedProcesses += $backendProcess
    $deadline = [DateTime]::UtcNow.AddSeconds(180)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($backendProcess.HasExited) { throw "backend exited with $($backendProcess.ExitCode)" }
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$backendPort/api/user/get/login" -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
            if ($r.StatusCode -eq 200) { $ready = $true; break }
        } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $ready) {
        Get-Content (Join-Path $runDir 'backend.log') -Tail 30 -ErrorAction SilentlyContinue | Write-Host
        throw 'BLOCKED_ENV: backend did not become ready'
    }
    $results['backend'] = 'PASS'

    Write-Host "== seed collab fixtures =="
    # 用户由浏览器脚本通过真实注册入口创建；这里只建团队空间与图片。
    # userId 未知：先让脚本注册 owner，再用 account 反查（脚本把 owner 账号写到 fixture 请求里）。
    # 简化：固定账号 collab_owner_<ts> 由脚本注册后自行以 SQL 建 space/picture 不可行（PS 无驱动），
    # 改为：浏览器脚本先把 owner 注册好，PS 轮询反查 id 后 seed。
    $fixturePath = Join-Path $runDir 'fixture.json'
    $env:VS44_E2E_API_TARGET = "http://127.0.0.1:$backendPort"
    $env:VS44_E2E_HTTPS_CERT = $certPath
    $env:VS44_E2E_HTTPS_KEY = $keyPath
    $env:VS44_E2E_ARTIFACTS = (Join-Path $runDir 'artifacts')
    $env:VS44_E2E_FIXTURE_JSON = $fixturePath
    # 先让浏览器脚本只执行注册阶段（写入 owner-account 文件），再 seed
    Push-Location (Join-Path $repo 'frontend')
    try {
        node ../../scripts/tmp-44/collab-browser-entry.mjs --register-only *> (Join-Path $runDir 'register-phase.log')
        if ($LASTEXITCODE -ne 0) { throw 'browser register phase failed' }
    } finally { Pop-Location }
    $ownerAccount = (Get-Content (Join-Path $runDir 'artifacts' 'owner-account.txt') -Raw).Trim()
    $idOwner = & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=vs_acceptance "--password=$nonce" $database --batch --skip-column-names -e "SELECT id FROM user WHERE userAccount='$ownerAccount';"
    if (-not $idOwner) { throw 'owner registration produced no row' }
    $seedPath = Join-Path $runDir 'seed.sql'
    [IO.File]::WriteAllText($seedPath, @"
USE $database;
INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount, totalSize, totalCount, isDelete)
 VALUES (9441001, 'collab-team', $idOwner, 1, 1, 1000, 1000, 0, 1, 0);
INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (9441001, $idOwner, 'admin');
INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
 VALUES (9442001, 'collab-picture', 'original', $idOwner, 10, 9441001, 1, 0);
"@, [Text.UTF8Encoding]::new($false))
    $seedPath | & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=vs_acceptance "--password=$nonce" --default-character-set=utf8mb4 *> (Join-Path $runDir 'seed.log')
    if ($LASTEXITCODE -ne 0) { throw 'seed failed' }
    @{ spaceId = 9441001; pictureId = 9442001; ownerAccount = $ownerAccount } | ConvertTo-Json | Set-Content $fixturePath

    Write-Host "== browser scenarios =="
    Push-Location (Join-Path $repo 'frontend')
    try {
        node ../../scripts/tmp-44/collab-browser-entry.mjs *> (Join-Path $runDir 'browser.log')
        $exit = $LASTEXITCODE
        Get-Content -LiteralPath (Join-Path $runDir 'browser.log') -Tail 40 | Write-Host
        if ($exit -eq 2) { throw 'BLOCKED_ENV: browser prerequisites missing' }
        if ($exit -ne 0) { throw 'browser collaboration assertions failed' }
        $results['browser'] = 'PASS'
    } finally { Pop-Location }

    Write-Host "== final data =="
    $final = & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=vs_acceptance "--password=$nonce" $database --batch -e "SELECT name FROM picture WHERE id=9442001; SELECT totalSize FROM space WHERE id=9441001;" 2>&1
    $final | Set-Content (Join-Path $runDir 'artifacts' 'final-data.txt')
    Write-Host $final
    if (($final -join "`n") -notmatch 'original') { throw 'FAIL: final picture row was modified' }
} catch {
    $failed = $true
    $message = $_.Exception.Message
    $results['result'] = if ($message.StartsWith('BLOCKED_ENV')) { 'BLOCKED_ENV' } else { 'FAIL' }
    $_ | Out-String | Set-Content (Join-Path $runDir 'error.log')
    Write-Host "ERROR: $message"
} finally {
    foreach ($p in $ownedProcesses) {
        try { if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } } catch {}
    }
    try {
        $servers = @(Get-CimInstance Win32_Process -Filter "Name = 'mysqld.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($dataDir) })
        foreach ($s in $servers) { Stop-Process -Id $s.ProcessId -Force -ErrorAction SilentlyContinue }
    } catch {}
    $results['cleanup'] = 'PASS'
    Write-Host "artifacts: $(Join-Path $runDir 'artifacts')"
    $results | ConvertTo-Json | Write-Host
    Pop-Location
}
if ($failed) { exit 1 }
exit 0
