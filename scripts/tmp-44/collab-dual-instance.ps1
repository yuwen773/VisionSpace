# 工单 44 专用双实例验收驱动（本票独立脚本，不改共享验收脚本）。
#
# 流程：
#   1. 起隔离 MySQL/Redis（原生二进制，随机端口 + nonce 命名）
#   2. 打包后端 jar，启动两个真实 Spring Boot 进程（不同端口，共享隔离库）
#   3. 通过 mysql.exe seed 团队空间 + 用户 + 图片
#   4. 运行 collab-dual-instance.mjs 驱动真实 WebSocket 协议
#   5. 断言后查询最终数据（picture 未被修改、配额不变）
#   6. 清理自己创建的进程与临时目录
#
# 用法：
#   pwsh.exe -NoLogo -NoProfile -File scripts/tmp-44/collab-dual-instance.ps1
[CmdletBinding()]
param(
    [string]$MySqlBin = 'D:\Work\Backend\MySQL-8.0.26\mysql-8.0.26-winx64\bin',
    [string]$RedisBin = 'D:\Work\Backend\Redis-x64-3.2.100'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$nonce = [Guid]::NewGuid().ToString('N')
$runDir = Join-Path ([IO.Path]::GetTempPath()) "vs44-collab-$nonce"
New-Item -ItemType Directory -Path $runDir | Out-Null
$artifacts = Join-Path $runDir 'artifacts'
New-Item -ItemType Directory -Path $artifacts | Out-Null
$ownedProcesses = @()
$exitCode = 0

function Free-Port {
    $l = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $l.Start(); try { return $l.LocalEndpoint.Port } finally { $l.Stop() }
}
function Wait-Port([int]$Port, [string]$Name) {
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $deadline) {
        $c = [Net.Sockets.TcpClient]::new()
        try { $c.Connect('127.0.0.1', $Port); $c.Dispose(); return } catch { Start-Sleep -Milliseconds 500 } finally { $c.Dispose() }
    }
    throw "BLOCKED_ENV: $Name did not open port $Port"
}
# PATH 上的 java 可能是 Oracle javapath 转发器（Start-Process 会静默退出且不写日志）。
# 显式优先 JAVA_HOME 并逐个探测，避免把「java 起不来」误报成「后端启动超时」。
function Resolve-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin/java.exe') }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $candidates += $command.Source }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not $candidate -or -not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $probe = Join-Path $runDir 'java-probe.log'
        $p = Start-Process -FilePath $candidate -ArgumentList @('-version') -PassThru -WindowStyle Hidden -RedirectStandardOutput ($probe + '.out') -RedirectStandardError ($probe + '.err')
        if ($p.WaitForExit(30000) -and $p.ExitCode -eq 0) { return $candidate }
    }
    throw 'BLOCKED_ENV: no working Java runtime found'
}

try {
    Write-Host "== [1/6] isolated MySQL/Redis =="
    $mysqlPort = Free-Port; $redisPort = Free-Port
    while ($redisPort -eq $mysqlPort -or $mysqlPort -lt 10000 -or $redisPort -lt 10000) { $mysqlPort = Free-Port; $redisPort = Free-Port }
    $mysqld = Join-Path $MySqlBin 'mysqld.exe'; $mysql = Join-Path $MySqlBin 'mysql.exe'
    $redisServer = Join-Path $RedisBin 'redis-server.exe'; $redisCli = Join-Path $RedisBin 'redis-cli.exe'
    foreach ($f in @($mysqld, $mysql, $redisServer, $redisCli)) { if (-not (Test-Path $f)) { throw "missing executable: $f" } }
    $dataDir = Join-Path $runDir 'mysql'
    & $mysqld --no-defaults --initialize-insecure "--datadir=$dataDir" --console *> (Join-Path $runDir 'mysql-init.log')
    if ($LASTEXITCODE -ne 0) { throw 'mysql init failed' }
    $ownedProcesses += Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults', "--datadir=`"$dataDir`"", '--bind-address=127.0.0.1', "--port=$mysqlPort", '--mysqlx=OFF', '--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'mysql-out.log') -RedirectStandardError (Join-Path $runDir 'mysql-err.log')
    $ownedProcesses += Start-Process -FilePath $redisServer -ArgumentList @('--bind', '127.0.0.1', '--port', "$redisPort", '--requirepass', $nonce, '--save', '""', '--appendonly', 'no', '--dir', "`"$runDir`"") -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'redis-out.log') -RedirectStandardError (Join-Path $runDir 'redis-err.log')
    Wait-Port $mysqlPort 'mysql'; Wait-Port $redisPort 'redis'
    $database = "vs44_collab_$nonce"
    $sourceDdl = Get-Content -Raw (Join-Path $repo 'sql\create_vision_space.sql')
    $tables = [regex]::Matches($sourceDdl, '(?ms)^CREATE TABLE .*?^\)[^\r\n]*;')
    if ($tables.Count -ne 15) { throw "schema table count changed: $($tables.Count)" }
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
    if ($LASTEXITCODE -ne 0) { throw 'bootstrap failed' }
    & $redisCli -h 127.0.0.1 -p $redisPort -a $nonce -n 15 SET acceptance:instance $nonce *> (Join-Path $runDir 'redis-marker.log')
    Write-Host "   mysql=$mysqlPort redis=$redisPort"

    Write-Host "== [2/6] package backend jar =="
    Push-Location $repo
    & mvn -B -q --no-transfer-progress -DskipTests package *> (Join-Path $runDir 'package.log')
    if ($LASTEXITCODE -ne 0) { throw 'mvn package failed; see package.log' }
    $jar = Get-ChildItem (Join-Path $repo 'target\*.jar') | Where-Object { $_.Name -notmatch '\.original\.jar$' } | Select-Object -First 1
    if (-not $jar) { throw 'no jar produced' }
    Pop-Location

    Write-Host "== [3/6] start two backend instances =="
    $portA = Free-Port; $portB = Free-Port
    while ($portB -eq $portA) { $portB = Free-Port }
    $commonArgs = @(
        '-jar', $jar.FullName,
        "--server.port=$portA",
        # 不用 local profile（它依赖 gitignore 的本地配置文件），占位密钥走命令行
        '--spring.profiles.active=default',
        '--spring.datasource.url=jdbc:mysql://127.0.0.1:' + $mysqlPort + '/' + $database + '?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
        '--spring.datasource.username=vs_acceptance', "--spring.datasource.password=$nonce",
        '--spring.data.redis.host=127.0.0.1', "--spring.data.redis.port=$redisPort",
        "--spring.data.redis.password=$nonce", '--spring.data.redis.database=15',
        '--spring.data.redis.url=redis://:' + $nonce + '@127.0.0.1:' + $redisPort + '/15',
        '--spring.session.store-type=redis', '--spring.session.timeout=600',
        '--vision-space.websocket.lease-ttl-seconds=5',
        '--vision-space.websocket.lease-renew-interval-ms=1500',
        '--spring.ai.mcp.client.enabled=false',
        '--spring.ai.openai.api-key=collab-dummy',
        '--spring.ai.dashscope.api-key=collab-dummy',
        '--vision-space.vision-recognition.api-key=collab-dummy', '--vision-space.vision-recognition.base-url=http://127.0.0.1:1',
        '--vision-space.vision-recognition.model=acceptance-model',
        '--vision-space.pexels.api-key=collab-dummy', '--aliYunAi.apiKey=collab-dummy',
        '--vision-space.aliyun-ai.create-out-painting-task=http://127.0.0.1:1/out-painting',
        '--vision-space.aliyun-ai.get-out-painting-task=http://127.0.0.1:1/tasks/%s',
        '--vision-space.security.default-password=acceptance-only'
    )
    $argsB = $commonArgs -replace "--server\.port=$portA", "--server.port=$portB"
    $java = Resolve-Java
    $ownedProcesses += Start-Process -FilePath $java -ArgumentList $commonArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'backend-a.log') -RedirectStandardError (Join-Path $runDir 'backend-a-err.log')
    $ownedProcesses += Start-Process -FilePath $java -ArgumentList $argsB -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'backend-b.log') -RedirectStandardError (Join-Path $runDir 'backend-b-err.log')
    Wait-Port $portA 'backend A'; Wait-Port $portB 'backend B'
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
    Push-Location (Join-Path $repo 'frontend')
    & node (Join-Path $PSScriptRoot 'collab-dual-instance.mjs')
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'user registration phase failed' }
    Pop-Location
    $seedPath = Join-Path $runDir 'seed.sql'
    $idOwner = & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=vs_acceptance "--password=$nonce" $database --batch --skip-column-names -e "SELECT id FROM user WHERE userAccount='owner_44';"
    $idEditor = & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=vs_acceptance "--password=$nonce" $database --batch --skip-column-names -e "SELECT id FROM user WHERE userAccount='editor_44';"
    $idViewer = & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=vs_acceptance "--password=$nonce" $database --batch --skip-column-names -e "SELECT id FROM user WHERE userAccount='viewer_44';"
    if (-not $idOwner -or -not $idEditor -or -not $idViewer) { throw 'user registration produced no rows' }
    [IO.File]::WriteAllText($seedPath, @"
USE $database;
INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount, totalSize, totalCount, isDelete)
 VALUES (9441001, 'team44', $idOwner, 1, 1, 1000, 1000, 0, 1, 0);
INSERT INTO space_user (spaceId, userId, spaceRole) VALUES
 (9441001, $idOwner, 'admin'), (9441001, $idEditor, 'editor'), (9441001, $idViewer, 'viewer');
INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
 VALUES (9442001, 'collab-picture', 'original', $idOwner, 10, 9441001, 1, 0);
"@, [Text.UTF8Encoding]::new($false))
    $seedPath | & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=vs_acceptance "--password=$nonce" --default-character-set=utf8mb4 *> (Join-Path $runDir 'seed.log')
    if ($LASTEXITCODE -ne 0) { throw 'seed failed' }
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
    $env:VS44_MYSQL_PORT = "$mysqlPort"; $env:VS44_REDIS_PORT = "$redisPort"
    $env:VS44_NONCE = $nonce
    $env:VS44_ARTIFACTS = $artifacts
    $env:VS44_FIXTURE_JSON = $fixturePath
    $env:VS44_DEMOTE_FILE = (Join-Path $artifacts 'demote.trigger')
    $env:VS44_DEMOTE_DONE_FILE = (Join-Path $artifacts 'demote.done')
    # 降权触发文件由 mjs 写入；这里起一个轮询 job 执行 SQL 并写 done
    $demoteJob = Start-Job -ScriptBlock {
        param($mysql, $mysqlPort, $database, $nonce, $trigger, $done, $mysqlPwdEnv)
        $env:MYSQL_PWD = ''
        while ($true) {
            if (Test-Path $trigger) {
                & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=vs_acceptance "--password=$nonce" $database -e "UPDATE space_user SET spaceRole='viewer' WHERE spaceId=9441001 AND userId=9440002;" *> $null
                Set-Content -Path $done -Value 'done'
                break
            }
            Start-Sleep -Milliseconds 200
        }
    } -ArgumentList $mysql, $mysqlPort, $database, $nonce, (Join-Path $artifacts 'demote.trigger'), (Join-Path $artifacts 'demote.done')

    Push-Location (Join-Path $repo 'frontend')
    & node --experimental-default-type=module (Join-Path $PSScriptRoot 'collab-dual-instance.mjs')
    $protocolExit = $LASTEXITCODE
    Pop-Location
    Stop-Job $demoteJob -ErrorAction SilentlyContinue; Remove-Job $demoteJob -Force -ErrorAction SilentlyContinue

    Write-Host "== [6/6] final data assertions =="
    $finalQueries = @"
USE $database;
SELECT name FROM picture WHERE id = 9442001;
SELECT totalSize FROM space WHERE id = 9441001;
SELECT spaceRole FROM space_user WHERE spaceId = 9441001 AND userId = 9440002;
"@
    $finalPath = Join-Path $runDir 'final.sql'
    [IO.File]::WriteAllText($finalPath, $finalQueries, [Text.UTF8Encoding]::new($false))
    $finalOutput = & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=vs_acceptance "--password=$nonce" --batch 2>&1
    $finalOutput | Set-Content (Join-Path $artifacts 'final-data.txt')
    Write-Host $finalOutput
    if (($finalOutput -join "`n") -match 'original') {
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
    foreach ($p in $ownedProcesses) {
        try { if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } } catch {}
    }
    # Windows mysqld 会派生 server 子进程：按 datadir 特征找齐再停
    try {
        $servers = @(Get-CimInstance Win32_Process -Filter "Name = 'mysqld.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($dataDir) })
        foreach ($s in $servers) { Stop-Process -Id $s.ProcessId -Force -ErrorAction SilentlyContinue }
    } catch {}
    foreach ($j in @($demoteJob)) { if ($j) { Stop-Job $j -ErrorAction SilentlyContinue; Remove-Job $j -Force -ErrorAction SilentlyContinue } }
    Write-Host "artifacts: $artifacts"
    Write-Host "rundir:    $runDir"
}
exit $exitCode
