# 工单 44 浏览器多连接协作验收驱动（本票独立脚本）。
#
# 复用 scripts/browser-acceptance.ps1 的隔离与入口模型（隔离库、自签 TLS、
# 同源代理入口、playwright-core 真实 Chromium），追加协作场景：
#   - 双标签编辑权反馈（一个编辑、一个被拒）
#   - 第二标签关闭不误释放
#   - 竞争用户被拒、持有者注销后写入被拒
#   - seed 团队空间/图片供协作使用
#
# 隔离服务全部走 Docker：postgres:18.7 + redis:7.2.4（原生二进制模式已移除）。
# 业务表只由 Flyway 以迁移角色 vision_migrator 创建，脚本不再执行 DDL。
#
# 退出码：0 通过；1 断言失败；2 BLOCKED_ENV。
[CmdletBinding()]
param(
    [switch]$SkipInstall
)
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
$runDir = Join-Path (Join-Path $repo '.vs44-runs') "vs44-browser-$nonce"
$database = "vs44_browser_$nonce"
$ownedProcesses = @()
$results = [ordered]@{}
$failed = $false

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
function Resolve-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME $(if ($IsWindows) { 'bin/java.exe' } else { 'bin/java' })) }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $candidates += $command.Source }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not $candidate -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        # javapath 转发器可能静默退出，探测 -version 是否真的能跑
        $probe = Join-Path $runDir 'java-probe.log'
        $p = Start-Process @processWindow -FilePath $candidate -ArgumentList @('-version') -PassThru -RedirectStandardOutput ($probe + '.out') -RedirectStandardError ($probe + '.err')
        if ($p.WaitForExit(30000) -and $p.ExitCode -eq 0) { return $candidate }
    }
    throw 'BLOCKED_ENV: no working Java runtime found'
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
    $certConfig = Join-Path $runDir 'openssl.cnf'
    "[req]`ndistinguished_name = req_dn`n[req_dn]" | Set-Content $certConfig
    & openssl req -config $certConfig -x509 -newkey rsa:2048 -nodes -days 1 -subj '/CN=127.0.0.1' `
        -addext 'subjectAltName=IP:127.0.0.1' -keyout $keyPath -out $certPath *> (Join-Path $runDir 'cert.log')
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: openssl unavailable' }

    Write-Host "== isolated PostgreSQL/Redis =="
    # 业务表只由 Flyway（迁移角色 vision_migrator）创建，脚本不再执行 DDL。
    $services = Start-AcceptanceDockerServices -RunDirectory $runDir -Database $database -Nonce $nonce
    $postgresPort = $services.PostgresPort
    $redisPort = $services.RedisPort
    $results['isolation'] = 'PASS'

    Write-Host "== backend jar =="
    Push-Location $repo
    try {
        mvn -B --no-transfer-progress -DskipTests clean package *> (Join-Path $runDir 'mvn-package.log')
        if ($LASTEXITCODE -ne 0 -or -not (Select-String -LiteralPath (Join-Path $runDir 'mvn-package.log') -SimpleMatch 'BUILD SUCCESS')) {
            throw 'backend build failed or Maven did not execute'
        }
    } finally { Pop-Location }
    $backendPort = Free-Port
    $backendArgs = @(
        '-Duser.timezone=UTC',
        '-Xmx256m', '-Xms128m',
        '-jar', (Join-Path $repo 'target/vision-space-0.0.1-SNAPSHOT.jar'),
        '--spring.profiles.active=browser',
        "--spring.config.additional-location=file:$(Join-Path $repo 'e2e/application-browser.yml')",
        "--server.port=$backendPort",
        # 协作用短租约与快续期
        '--vision-space.websocket.lease-ttl-seconds=5',
        '--vision-space.websocket.lease-renew-interval-ms=1500'
    ) + @(Get-AcceptanceApplicationArguments $postgresPort $redisPort $database $nonce)
    $java = Resolve-Java
    $backendProcess = Start-Process @processWindow -FilePath $java -ArgumentList $backendArgs -PassThru -RedirectStandardOutput (Join-Path $runDir 'backend.log') -RedirectStandardError (Join-Path $runDir 'backend-error.log')
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
    Invoke-WebRequest -Uri "http://127.0.0.1:$backendPort/api/v3/api-docs" `
        -OutFile (Join-Path $runDir 'openapi.json') -TimeoutSec 30

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
        node (Join-Path $PSScriptRoot 'collab-browser-entry.mjs') --register-only *> (Join-Path $runDir 'register-phase.log')
        if ($LASTEXITCODE -ne 0) { throw 'browser register phase failed' }
    } finally { Pop-Location }
    $accounts = Get-Content -Raw (Join-Path $runDir 'artifacts' 'accounts.json') | ConvertFrom-Json
    $ownerAccount = $accounts.owner
    $idOwner = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql "SELECT id FROM app_user WHERE user_account='$ownerAccount';" -LogPath (Join-Path $runDir 'user-lookup.log'))[0]
    $idEditor = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql "SELECT id FROM app_user WHERE user_account='$($accounts.editor)';" -LogPath (Join-Path $runDir 'user-lookup.log'))[0]
    $idViewer = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql "SELECT id FROM app_user WHERE user_account='$($accounts.viewer)';" -LogPath (Join-Path $runDir 'user-lookup.log'))[0]
    if (-not $idOwner -or -not $idEditor -or -not $idViewer) { throw 'account registration produced no rows' }
    $seedPath = Join-Path $runDir 'seed.sql'
    [IO.File]::WriteAllText($seedPath, @"
INSERT INTO space (id, space_name, user_id, space_level, space_type, max_size, max_count, total_size, total_count, is_delete)
 VALUES (9441001, 'collab-team', $idOwner, 1, 1, 1000, 1000, 0, 1, 0);
INSERT INTO space_user (space_id, user_id, space_role) VALUES
 (9441001, $idOwner, 'admin'), (9441001, $idEditor, 'editor'), (9441001, $idViewer, 'viewer');
INSERT INTO picture (id, url, name, user_id, pic_size, space_id, review_status, is_delete)
 VALUES (9442001, 'collab-picture', 'original', $idOwner, 10, 9441001, 1, 0);
"@, [Text.UTF8Encoding]::new($false))
    # 走 stdin：psql 逐条执行，ON_ERROR_STOP 让第一条失败即停（PostgreSQL 不会在
    # 失败后继续同事务语句），错误明细留在 seed.log。
    $seedRows = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql (Get-Content -Raw -LiteralPath $seedPath) -LogPath (Join-Path $runDir 'seed.log'))
    $seedRows | Set-Content (Join-Path $runDir 'seed-output.log')
    @{ spaceId = 9441001; pictureId = 9442001; ownerAccount = $ownerAccount } | ConvertTo-Json | Set-Content $fixturePath

    Write-Host "== browser scenarios =="
    $env:VS44_E2E_OWNER_ACCOUNT = $accounts.owner
    $env:VS44_E2E_EDITOR_ACCOUNT = $accounts.editor
    $env:VS44_E2E_VIEWER_ACCOUNT = $accounts.viewer
    Push-Location (Join-Path $repo 'frontend')
    try {
        node (Join-Path $PSScriptRoot 'collab-browser-entry.mjs') *> (Join-Path $runDir 'browser.log')
        $exit = $LASTEXITCODE
        Get-Content -LiteralPath (Join-Path $runDir 'browser.log') -Tail 40 | Write-Host
        if ($exit -eq 2) { throw 'BLOCKED_ENV: browser prerequisites missing' }
        if ($exit -ne 0) { throw 'browser collaboration assertions failed' }
        $results['browser'] = 'PASS'
    } finally { Pop-Location }

    Write-Host "== final data =="
    $finalQueries = @"
SELECT name FROM picture WHERE id=9442001;
SELECT total_size, total_count FROM space WHERE id=9441001;
"@
    $final = @(Invoke-PsqlRows -Container $services.PostgresContainer -Database $database -Password $nonce -Sql $finalQueries -LogPath (Join-Path $runDir 'final-query-error.log'))
    $final | Set-Content (Join-Path $runDir 'artifacts' 'final-data.txt')
    Write-Host $final
    # psql -tA 的多列输出用 | 分隔（MySQL 的 batch 输出用制表符）。
    if ($final.Count -ne 2 -or $final[0] -ne 'original' -or $final[1] -ne '0|1') {
        throw 'FAIL: picture row or space quota was modified'
    }
    $results['final-data'] = 'PASS'
} catch {
    $failed = $true
    $message = $_.Exception.Message
    $results['result'] = if ($message.StartsWith('BLOCKED_ENV')) { 'BLOCKED_ENV' } else { 'FAIL' }
    $_ | Out-String | Set-Content (Join-Path $runDir 'error.log')
    Write-Host "ERROR: $message"
} finally {
    $cleanup = 'PASS'
    foreach ($ownedProcess in $ownedProcesses) {
        try {
            if (-not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id -Force -ErrorAction Stop }
            if (-not $ownedProcess.WaitForExit(10000)) { throw 'Owned process did not exit.' }
        } catch { $cleanup = 'FAIL' }
    }
    # 隔离容器与命名卷都在本轮自建自删（Stop-AcceptanceDockerServices 负责卷）。
    if ($services) { try { Stop-AcceptanceDockerServices $services } catch { $cleanup = 'FAIL' } }
    foreach ($entry in @(Get-ChildItem Env:)) {
        if (-not $savedEnvironment.ContainsKey($entry.Name)) { [Environment]::SetEnvironmentVariable($entry.Name, $null) }
    }
    foreach ($key in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key]) }
    $results['cleanup'] = $cleanup
    if ($cleanup -ne 'PASS') { $failed = $true }
    Write-Host "artifacts: $(Join-Path $runDir 'artifacts')"
    $results | ConvertTo-Json | Write-Host
    $report = Join-Path $repo 'target/collab-browser'
    New-Item -ItemType Directory -Force $report | Out-Null
    @{ head = (git rev-parse HEAD); runId = $(if ($env:VS_SRF_RUN_ID) { $env:VS_SRF_RUN_ID } else { [Guid]::NewGuid().ToString('N') }); infrastructure = 'docker'; results = $results; productionAcceptance = 'NOT_RUN' } |
        ConvertTo-Json | Set-Content (Join-Path $report 'summary.json')
    if (Test-Path (Join-Path $runDir 'artifacts/browser-results.json')) {
        Copy-Item (Join-Path $runDir 'artifacts/browser-results.json') $report
    }
    Pop-Location
}
if ($failed) { exit 1 }
exit 0
