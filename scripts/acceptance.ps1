[CmdletBinding()]
param(
    # Optional Windows binaries; omit both to use dedicated Docker containers.
    [string]$MySqlBin,
    [string]$RedisBin
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path $PSScriptRoot -Parent
Import-Module (Join-Path $PSScriptRoot 'acceptance-results.psm1') -Force
$nonce = [Guid]::NewGuid().ToString('N')
$runDir = Join-Path ([IO.Path]::GetTempPath()) "visionspace-acceptance-$nonce"
$database = "vs_acceptance_$nonce"
$results = [ordered]@{}
$ownedProcesses = @()
$ownedContainers = @()
$mysql = $null
$savedEnvironment = @{}
$environmentNames = @('VS_ACCEPTANCE_NONCE', 'VS_ACCEPTANCE_MYSQL_PORT', 'VS_ACCEPTANCE_REDIS_PORT', 'MYSQL_PWD')
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

function Run-Gate([string]$Name, [scriptblock]$Command) {
    Write-Host "Running $Name"
    $log = Join-Path $runDir "$Name.log"
    try {
        & $Command *> $log
        if ($LASTEXITCODE -ne 0) { throw "exit $LASTEXITCODE" }
        $results[$Name] = 'PASS'
    } catch {
        $results[$Name] = if ($_.Exception -is [System.Management.Automation.CommandNotFoundException]) {
            'BLOCKED_ENV'
        } elseif ($Name -eq 'backend') {
            Get-BackendTestStatus -ExitCode 1 -ReportDirectory (Join-Path $runDir 'surefire')
        } else { 'FAIL' }
        Get-Content -LiteralPath $log -Tail 25 | Write-Host
    }
}

New-Item -ItemType Directory -Path $runDir | Out-Null
Push-Location $repo
try {
    Run-Gate 'runner-tests' { & pwsh -NoLogo -NoProfile -File (Join-Path $PSScriptRoot 'tests/test-acceptance-results.ps1') }
    # 候选内容脱敏扫描：只记录文件:行号:规则名，不输出命中内容。
    Run-Gate 'secret-scan' { & pwsh -NoLogo -NoProfile -File (Join-Path $PSScriptRoot 'secret-scan.ps1') }
    try {
        if ([bool]$MySqlBin -ne [bool]$RedisBin) { throw 'Specify both MySqlBin and RedisBin, or neither.' }
        $mysqlPort = Free-Port
        $redisPort = Free-Port
        while ($redisPort -eq $mysqlPort -or $mysqlPort -lt 10000 -or $redisPort -lt 10000) {
            $mysqlPort = Free-Port
            $redisPort = Free-Port
        }
        # Reuse tracked DDL only: exclude database selection, DROP statements and any data.
        $sourceDdl = Get-Content -Raw -LiteralPath (Join-Path $repo 'sql/create_vision_space.sql')
        $tables = [regex]::Matches($sourceDdl, '(?ms)^CREATE TABLE .*?^\)[^\r\n]*;')
        # The expected count comes from the DDL itself, so adding a table never needs an edit here.
        # Bootstrap completeness is verified against the live server below.
        if ($tables.Count -eq 0) { throw 'FAIL: no CREATE TABLE statements found in sql/create_vision_space.sql.' }
        $expectedTables = @($tables | ForEach-Object {
                [regex]::Match($_.Value, '(?m)^CREATE TABLE (?:IF NOT EXISTS )?`?([A-Za-z0-9_]+)`?').Groups[1].Value
            })
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
            $actualDataDir = & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=root --batch --skip-column-names --execute='SELECT @@datadir'
            if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath("$actualDataDir").TrimEnd('\', '/') -ne [IO.Path]::GetFullPath($dataDir).TrimEnd('\', '/')) {
                throw 'Isolated MySQL process identity could not be verified before schema bootstrap.'
            }
            $bootstrap | & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=root --default-character-set=utf8mb4 *> (Join-Path $runDir 'bootstrap.log')
            if ($LASTEXITCODE -ne 0) { throw 'FAIL: isolated schema bootstrap failed.' }
            & $redisCli -h 127.0.0.1 -p $redisPort -a $nonce -n 15 SET acceptance:instance $nonce *> (Join-Path $runDir 'redis-marker.log')
            if ($LASTEXITCODE -ne 0) { throw 'Redis marker creation failed.' }
        } else {
            & docker info *> (Join-Path $runDir 'docker-info.log')
            if ($LASTEXITCODE -ne 0) { throw 'Docker engine unavailable. On Windows, explicit MySqlBin/RedisBin may be used.' }
            $mysqlContainer = "vs-acceptance-mysql-$nonce"
            $redisContainer = "vs-acceptance-redis-$nonce"
            $ownedContainers += $mysqlContainer
            & docker run -d --name $mysqlContainer -p "127.0.0.1:${mysqlPort}:3306" -e "MYSQL_ROOT_PASSWORD=$nonce" --mount "type=bind,source=$bootstrapPath,target=/docker-entrypoint-initdb.d/acceptance.sql,readonly" mysql:8.0.36 *> (Join-Path $runDir 'mysql-container.log')
            if ($LASTEXITCODE -ne 0) { throw 'MySQL container startup failed.' }
            $ownedContainers += $redisContainer
            & docker run -d --name $redisContainer -p "127.0.0.1:${redisPort}:6379" redis:7.2.4 redis-server --requirepass $nonce *> (Join-Path $runDir 'redis-container.log')
            if ($LASTEXITCODE -ne 0) { throw 'Redis container startup failed.' }
            Wait-Port $mysqlPort
            Wait-Port $redisPort
            # MySQL opens its final TCP socket only after entrypoint SQL is applied.
            & docker exec $redisContainer redis-cli -a $nonce -n 15 SET acceptance:instance $nonce *> (Join-Path $runDir 'redis-marker.log')
            if ($LASTEXITCODE -ne 0) { throw 'Redis marker creation failed.' }
        }
        # Verify the bootstrap actually created every tracked table, rather than trusting a hand-kept count.
        # The Docker entrypoint still applies the mounted SQL after the TCP port opens, so retry until
        # the schema settles instead of reading a half-initialised server.
        $tableListSql = "SELECT table_name FROM information_schema.tables WHERE table_schema = '$database'"
        $createdTables = @()
        $schemaDeadline = [DateTime]::UtcNow.AddSeconds(180)
        while ([DateTime]::UtcNow -lt $schemaDeadline) {
            $tableRows = if ($MySqlBin) {
                & $mysql --no-defaults --host=127.0.0.1 "--port=$mysqlPort" --user=root --batch --skip-column-names "--execute=$tableListSql" 2>$null
            } else {
                & docker exec $mysqlContainer mysql --user=root "--password=$nonce" --batch --skip-column-names "--execute=$tableListSql" 2>$null
            }
            $createdTables = @($tableRows | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne 'acceptance_instance' })
            if ($createdTables.Count -ge $expectedTables.Count) { break }
            Start-Sleep -Seconds 2
        }
        $missingTables = @($expectedTables | Where-Object { $createdTables -notcontains $_ })
        if ($missingTables.Count -gt 0 -or $createdTables.Count -ne $expectedTables.Count) {
            throw "FAIL: acceptance schema incomplete (expected $($expectedTables.Count) tables, found $($createdTables.Count); missing: $($missingTables -join ', '))."
        }
        $env:VS_ACCEPTANCE_NONCE = $nonce
        $env:VS_ACCEPTANCE_MYSQL_PORT = "$mysqlPort"
        $env:VS_ACCEPTANCE_REDIS_PORT = "$redisPort"
        $results['isolation'] = 'PASS'
        Run-Gate 'backend' { mvn -B --no-transfer-progress "-Dacceptance.reportsDirectory=$runDir/surefire" clean verify }
    } catch {
        $results['isolation'] = if ($_.Exception.Message.StartsWith('FAIL:')) { 'FAIL' } else { 'BLOCKED_ENV' }
        $results['backend'] = 'BLOCKED_ENV'
        $_ | Out-String | Set-Content (Join-Path $runDir 'isolation-error.log')
        Write-Host "BLOCKED_ENV: $_"
    }
    Push-Location (Join-Path $repo 'frontend')
    try {
        Run-Gate 'frontend-install' { npm ci --no-audit --no-fund }
        if ($results['frontend-install'] -eq 'PASS') {
            Run-Gate 'frontend-lint' { npm run lint:check }
            Run-Gate 'frontend-format' { npm run format:check }
            Run-Gate 'frontend-typecheck' { npm run type-check }
            Run-Gate 'frontend-tests' { npm test }
            Run-Gate 'frontend-build' { npm run build-only }
            # 后端接口变更后未重新生成客户端，是反复出现的 review 发现；这里用契约自动对账。
            if ($results['backend'] -eq 'PASS') {
                Run-Gate 'frontend-api-contract' { & pwsh -NoLogo -NoProfile -File (Join-Path $repo 'scripts/openapi-contract.ps1') }
            } else {
                $results['frontend-api-contract'] = 'BLOCKED_ENV'
            }
        } else {
            foreach ($gate in @('frontend-lint', 'frontend-format', 'frontend-typecheck', 'frontend-tests', 'frontend-build', 'frontend-api-contract')) {
                $results[$gate] = 'BLOCKED_ENV'
            }
        }
    } finally { Pop-Location }
} finally {
    $results['cleanup'] = 'PASS'
    # Windows MySQL can spawn a server child. Stop the verified server, not just its launcher.
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
                } else {
                    $results['cleanup'] = 'FAIL'
                }
            }
        } catch {
            $results['cleanup'] = 'FAIL'
            $_ | Out-String | Add-Content (Join-Path $runDir 'mysql-cleanup.log')
        }
    }
    foreach ($process in $ownedProcesses) {
        try {
            if (-not $process.HasExited) {
                Stop-Process -Id $process.Id -ErrorAction Stop
                if (-not $process.WaitForExit(5000)) { throw 'Owned acceptance process did not exit.' }
            }
        } catch {
            $results['cleanup'] = 'FAIL'
            $_ | Out-String | Add-Content (Join-Path $runDir 'process-cleanup.log')
        }
    }
    foreach ($container in $ownedContainers) {
        & docker logs $container *> (Join-Path $runDir "$container-server.log")
        & docker rm -f -v $container *> $null
        if ($LASTEXITCODE -ne 0) { $results['cleanup'] = 'FAIL' }
    }
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name]) }
    $summary = [ordered]@{
        head = (git rev-parse HEAD)
        infrastructure = $(if ($MySqlBin) { 'native-explicit' } else { 'docker' })
        results = $results
        logs = $runDir
        # A failure remains FAIL until baseline comparison establishes it was pre-existing.
        productionAcceptance = 'NOT_RUN'
    }
    $reportDir = Join-Path $repo "target/acceptance/$nonce"
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
    $summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $reportDir 'summary.json')
    Copy-Item -Path (Join-Path $runDir '*.log') -Destination $reportDir
    if (Test-Path -LiteralPath (Join-Path $runDir 'surefire')) {
        Copy-Item -LiteralPath (Join-Path $runDir 'surefire') -Destination $reportDir -Recurse
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $repo 'target/acceptance/summary.json')
    $summary | ConvertTo-Json -Depth 5 | Write-Host
    Pop-Location
}
if ($results.Values -contains 'FAIL') { exit 1 }
if ($results.Values -contains 'BLOCKED_ENV') { exit 2 }
exit 0
