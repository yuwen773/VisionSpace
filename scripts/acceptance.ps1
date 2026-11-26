[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path $PSScriptRoot -Parent
Import-Module (Join-Path $PSScriptRoot 'acceptance-results.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'docker-test-harness.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'acceptance-environment.psm1') -Force
$nonce = [Guid]::NewGuid().ToString('N')
$runDir = Join-Path ([IO.Path]::GetTempPath()) "visionspace-acceptance-$nonce"
# 关卡脚本把失败现场写进 <runDir>/diagnostics/，位置经环境变量传下去，收尾时再拷进报告目录。
# publish-evidence.py 只发布路径里带 diagnostics 目录的 .log——原始 gate 日志
# （可能含 Cookie、SQL 参数）仍然不出仓库，但断言失败能定位。
# 目录本身只在下面「New-Item ... diagnostics -Force」那一处创建。
$env:VS_ACCEPTANCE_RUN_DIR = $runDir
# Docker 资源身份：CI 用证据 run-id，本地用本轮 nonce。同一个值既打标签又写进
# summary.json，硬杀之后可以从 `docker ps` 直接回到报告目录。
$runId = if ($env:VS_SRF_RUN_ID) { $env:VS_SRF_RUN_ID } else { $nonce }
$dockerResources = [ordered]@{ containers = @(); volumes = @() }
$database = "vs_acceptance_$nonce"
$results = [ordered]@{}
# 每个失败关卡的“为什么”。只记结构化原因（脚本路径 + 退出码这类），
# 不记日志正文：日志里可能有 Cookie、SQL 参数与请求体。
# 没有它，CI 上只会看到一个红灯状态码，无法定位是哪一步、哪条规则挂的。
$failures = [ordered]@{}
$savedEnvironment = @{}
$environmentNames = @('VS_ACCEPTANCE_NONCE', 'VS_ACCEPTANCE_PG_PORT', 'VS_ACCEPTANCE_REDIS_PORT', 'JAVA_TOOL_OPTIONS')
foreach ($name in $environmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name) }
# PostgreSQL 容器与库都跑 UTC。Match every application/test JVM, regardless of host timezone.
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'

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

function Get-AcceptancePython {
    foreach ($candidate in @('python', 'python3')) {
        $command = Get-Command $candidate -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $command -or $command.Source -match '[\\/]WindowsApps[\\/]') { continue }
        $supported = & $command.Source -I -c 'import sys; print(sys.version_info >= (3, 9))' 2>$null
        if ($LASTEXITCODE -eq 0 -and $supported -eq 'True') { return $command.Source }
    }
    throw [System.Management.Automation.CommandNotFoundException]::new('BLOCKED_ENV: working Python 3.9+ is required')
}

function Run-Gate([string]$Name, [scriptblock]$Command) {
    Write-Host "Running $Name"
    $log = Join-Path $runDir "$Name.log"
    try {
        & $Command *> $log
        if ($LASTEXITCODE -ne 0) { throw "exit $LASTEXITCODE" }
        if ($Name -eq 'backend' -and (Get-BackendTestStatus -ExitCode 0 -ReportDirectory (Join-Path $runDir 'surefire')) -ne 'PASS') {
            throw 'Backend reports are missing, skipped or unsuccessful.'
        }
        $results[$Name] = 'PASS'
    } catch {
        $results[$Name] = if ($_.Exception -is [System.Management.Automation.CommandNotFoundException]) {
            'BLOCKED_ENV'
        } elseif ($Name -eq 'backend') {
            Get-BackendTestStatus -ExitCode 1 -ReportDirectory (Join-Path $runDir 'surefire')
        } else { 'FAIL' }
        # 失败原因只留结构化短句；清洗掉任何看起来像“键=值”的片段。
        $reason = [string]$_.Exception.Message
        $reason = ($reason -replace '(?i)(password|passwd|pwd|secret|token|api[-_]?key|authorization)\s*[:=]\s*\S+', '$1=***').Trim()
        if ($reason.Length -gt 200) { $reason = $reason.Substring(0, 200) }
        if ($reason) { $failures[$Name] = $reason }
        Get-Content -LiteralPath $log -Tail 25 | Write-Host
    }
}

# runDir 及其 diagnostics/ 只在这一处创建，-Force 保证幂等：曾经在文件头先建一次
# diagnostics，这里的无 -Force 版本随即抛「已存在」，整轮验收没跑任何关卡就退出了。
New-Item -ItemType Directory -Path (Join-Path $runDir 'diagnostics') -Force | Out-Null
Push-Location $repo
try {
    Run-Gate 'runner-tests' {
        $policy = Get-Content -Raw (Join-Path $PSScriptRoot 'srf-matrix.json') | ConvertFrom-Json
        foreach ($test in $policy.runnerTests) {
            & pwsh -NoLogo -NoProfile -File (Join-Path $repo $test.path)
            if ($LASTEXITCODE -ne 0) { throw "$($test.path) exited $LASTEXITCODE" }
        }
        # 仓库护栏回归：评审基点漂移、秘密扫描漏文件类型、恒真断言、文档/manifest 漂移。
        # 纯 Python，不需要隔离数据库或 PowerShell 执行策略。
        $python = Get-AcceptancePython
        & $python -I (Join-Path $PSScriptRoot 'tests/check-repo-hygiene.py')
        if ($LASTEXITCODE -ne 0) { throw "check-repo-hygiene exited $LASTEXITCODE" }
        & $python -I (Join-Path $PSScriptRoot 'tests/verify-secret-scan.py')
        if ($LASTEXITCODE -ne 0) { throw "verify-secret-scan exited $LASTEXITCODE" }
        foreach ($test in Get-ChildItem (Join-Path $PSScriptRoot 'tests/test-*.py')) {
            & $python -I $test.FullName
            if ($LASTEXITCODE -ne 0) { throw "$($test.Name) exited $LASTEXITCODE" }
        }
    }
    # §5.8 的覆盖对照计数要进 summary。runner-tests 里的那次通配执行只留退出码，
    # 证据看不出「退役几个、新增几个」，也就判断不了是不是靠净删除换绿；
    # 这里再跑一次（纯文件扫描，秒级），把计数落成 JSON 供 summary 引用。
    Run-Gate 'test-coverage-map' {
        $python = Get-AcceptancePython
        & $python -I (Join-Path $PSScriptRoot 'tests/test-pg-test-coverage-map.py') `
            --json-out (Join-Path $runDir 'test-coverage-map.json')
        if ($LASTEXITCODE -ne 0) { throw "test-pg-test-coverage-map exited $LASTEXITCODE" }
    }
    # 候选内容脱敏扫描：只记录文件:行号:规则名，不输出命中内容。
    Run-Gate 'secret-scan' { & pwsh -NoLogo -NoProfile -File (Join-Path $PSScriptRoot 'secret-scan.ps1') }
    try {
        # 隔离服务全部走 Docker：postgres:18.7 + redis:7.2.4。
        # PostgreSQL 18 起数据目录版本化（PGDATA=/var/lib/postgresql/18/docker），
        # 卷必须挂到 /var/lib/postgresql；旧路径 /var/lib/postgresql/data 已不存在。
        # 业务表只由 Flyway 以迁移角色 vision_migrator 创建；初始化脚本只建角色/库/扩展/库级注释。
        & docker info *> (Join-Path $runDir 'docker-info.log')
        if ($LASTEXITCODE -ne 0) { throw 'Docker engine unavailable.' }
        $runId = Initialize-VsDockerRun -RunId $runId
        $postgresContainer = "vs-acceptance-postgres-$nonce"
        $redisContainer = "vs-acceptance-redis-$nonce"
        # postgres:18 声明 VOLUME /var/lib/postgresql：显式挂命名卷，避免匿名卷残留。
        $postgresVolume = New-VsVolume -Suffix 'postgres'
        $initScript = @'
#!/bin/bash
set -euo pipefail
psql --no-psqlrc -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -c "CREATE ROLE vision_migrator LOGIN PASSWORD '$ACCEPTANCE_NONCE' NOSUPERUSER NOCREATEDB NOCREATEROLE"
psql --no-psqlrc -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -c "CREATE ROLE vision_app LOGIN PASSWORD '$ACCEPTANCE_NONCE' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT"
psql --no-psqlrc -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -c "CREATE DATABASE \"$ACCEPTANCE_DB\" OWNER vision_migrator"
psql --no-psqlrc -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$ACCEPTANCE_DB" \
  -c "REVOKE CREATE ON SCHEMA public FROM PUBLIC" \
  -c "GRANT USAGE ON SCHEMA public TO vision_app" \
  -c "CREATE EXTENSION IF NOT EXISTS pgcrypto" \
  -c "ALTER DATABASE \"$ACCEPTANCE_DB\" SET timezone = 'UTC'" \
  -c "COMMENT ON DATABASE \"$ACCEPTANCE_DB\" IS '$ACCEPTANCE_NONCE'"
'@
        $initPath = Join-Path $runDir 'postgres-init.sh'
        [IO.File]::WriteAllText($initPath, $initScript.Replace("`r`n", "`n"), [Text.UTF8Encoding]::new($false))
        $postgresStarted = Start-VsDockerContainer -Name $postgresContainer -LogPath (Join-Path $runDir 'postgres-container.log') -Arguments @(
            '-p', '127.0.0.1::5432',
            '-e', "POSTGRES_PASSWORD=$nonce",
            '-e', 'POSTGRES_USER=postgres',
            '-e', 'POSTGRES_DB=postgres',
            '-e', "ACCEPTANCE_DB=$database",
            '-e', "ACCEPTANCE_NONCE=$nonce",
            '-e', 'TZ=UTC',
            '-v', "${postgresVolume}:/var/lib/postgresql",
            '--mount', "type=bind,source=$initPath,target=/docker-entrypoint-initdb.d/01-acceptance.sh,readonly",
            # 全量套件同时存活多个 Spring 上下文（含双实例用例）；默认 100 个连接不够用。
            'postgres:18.7', '-c', 'max_connections=200')
        if (-not $postgresStarted) { throw 'PostgreSQL container startup failed.' }
        $redisVolume = New-VsVolume -Suffix 'redis'
        $redisStarted = Start-VsDockerContainer -Name $redisContainer -LogPath (Join-Path $runDir 'redis-container.log') -Arguments @(
            '-p', '127.0.0.1::6379',
            '-v', "${redisVolume}:/data",
            'redis:7.2.4', 'redis-server', '--requirepass', $nonce)
        if (-not $redisStarted) { throw 'Redis container startup failed.' }
        $postgresPort = [int]((& docker port $postgresContainer 5432) -split ':')[-1]
        $redisPort = [int]((& docker port $redisContainer 6379) -split ':')[-1]
        # psql 就绪（服务器接受连接 + 运行角色与库已建好）后才允许迁移：端口通不代表可用。
        $readyDeadline = [DateTime]::UtcNow.AddSeconds(180)
        $ready = $false
        while ([DateTime]::UtcNow -lt $readyDeadline) {
            # -h 127.0.0.1：与服务实际连接路径一致。psql 默认的 unix socket 在镜像
            # initdb 阶段的临时服务上也能连（listen_addresses=''），会提前判就绪。
            & docker exec -e "PGPASSWORD=$nonce" $postgresContainer psql --no-psqlrc -tAc 'SELECT 1' -h 127.0.0.1 -U vision_app -d $database *> (Join-Path $runDir 'postgres-ready.log')
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
            Start-Sleep -Milliseconds 500
        }
        if (-not $ready) { throw 'BLOCKED_ENV: isolated PostgreSQL did not become ready.' }
        # 唯一建表入口：Flyway 以 vision_migrator 执行 V1 基线（口令走环境变量，不进 argv）。
        $env:FLYWAY_URL = "jdbc:postgresql://127.0.0.1:$postgresPort/$database"
        $env:FLYWAY_USER = 'vision_migrator'
        $env:FLYWAY_PASSWORD = $nonce
        try {
            # Flyway 的 locations 是相对路径（filesystem:src/main/resources/db/migration），
            # 必须在仓库根目录执行，否则换个工作目录就会「找不到迁移文件」。
            Push-Location $repo
            try {
                & mvn -B -N --no-transfer-progress 'org.flywaydb:flyway-maven-plugin:13.13.0:migrate' *> (Join-Path $runDir 'flyway-migrate.log')
                if ($LASTEXITCODE -ne 0) { throw 'FAIL: Flyway migrate failed.' }
                & mvn -B -N --no-transfer-progress 'org.flywaydb:flyway-maven-plugin:13.13.0:validate' *> (Join-Path $runDir 'flyway-validate.log')
                if ($LASTEXITCODE -ne 0) { throw 'FAIL: Flyway validate failed.' }
            } finally {
                Pop-Location
            }
        } finally {
            Remove-Item Env:FLYWAY_URL, Env:FLYWAY_USER, Env:FLYWAY_PASSWORD -ErrorAction SilentlyContinue
        }
        & docker exec $redisContainer redis-cli -a $nonce -n 15 SET acceptance:instance $nonce *> (Join-Path $runDir 'redis-marker.log')
        if ($LASTEXITCODE -ne 0) { throw 'Redis marker creation failed.' }

        # 核对「库里建出的表数 = 基线里的 CREATE TABLE 数」，而不是手写一个期望值。
        # Flyway 自己的元数据表 flyway_schema_history 也建在 public 里，不算业务表。
        $baselineSql = Get-Content -Raw -LiteralPath (Join-Path $repo 'src/main/resources/db/migration/V1__baseline.sql')
        $expectedTables = [regex]::Matches($baselineSql, '(?m)^CREATE TABLE ').Count
        if ($expectedTables -eq 0) { throw 'FAIL: no CREATE TABLE statements found in the Flyway baseline.' }
        $tableListSql = "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'"
        $createdTables = -1
        $schemaDeadline = [DateTime]::UtcNow.AddSeconds(120)
        while ([DateTime]::UtcNow -lt $schemaDeadline) {
            $row = & docker exec -e "PGPASSWORD=$nonce" $postgresContainer psql --no-psqlrc -tAc $tableListSql -U vision_app -d $database 2>$null
            if ($LASTEXITCODE -eq 0 -and "$row".Trim() -match '^[0-9]+$') {
                $createdTables = [int]"$row".Trim()
                if ($createdTables -ge $expectedTables) { break }
            }
            Start-Sleep -Seconds 1
        }
        if ($createdTables -ne $expectedTables) {
            throw "FAIL: acceptance schema incomplete (expected $expectedTables tables, found $createdTables)."
        }
        $env:VS_ACCEPTANCE_NONCE = $nonce
        $env:VS_ACCEPTANCE_PG_PORT = "$postgresPort"
        $env:VS_ACCEPTANCE_REDIS_PORT = "$redisPort"
        $results['isolation'] = 'PASS'


        # #39 双实例验收：先 clean test-compile（复用旧 target 时，已删除的
        # 资源/测试类可能残留进构件），再以真实第二 JVM 启动共享同一隔离
        # PostgreSQL/Redis 的应用进程；第二实例端口经就绪文件握手后传给 surefire。
        # 进程句柄用脚本作用域变量传递：Run-Gate 的 scriptblock 是子作用域，
        # 普通赋值不会传回外层 finally，会让清理逻辑永远看不见该进程。
        $script:SecondProcess = $null
        $script:SecondOwned = $false
        $secondReadyFile = Join-Path $runDir 'second-instance.port'
        $secondStopFile = "$secondReadyFile.stop"
        Run-Gate 'backend' {
            mvn -B --no-transfer-progress "-Dacceptance.reportsDirectory=$runDir/surefire" clean test-compile
            if ($LASTEXITCODE -ne 0) { throw "clean test-compile exit $LASTEXITCODE" }
            $testClasspath = mvn -B --no-transfer-progress -q dependency:build-classpath "-Dmdep.outputFile=$runDir/test-cp.txt" -DincludeScope=test
            if ($LASTEXITCODE -ne 0) { throw "build-classpath exit $LASTEXITCODE" }
            $cp = (Resolve-Path (Join-Path $repo 'target/classes')).Path + [IO.Path]::PathSeparator +
                  (Resolve-Path (Join-Path $repo 'target/test-classes')).Path + [IO.Path]::PathSeparator +
                  (Get-Content -Raw (Join-Path $runDir 'test-cp.txt')).Trim()
            # PATH 里的 java 可能是 Oracle javapath 桩（无法独立运行）；优先取 JAVA_HOME
            $javaBin = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
                (Join-Path $env:JAVA_HOME 'bin/java.exe')
            } elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java'))) {
                (Join-Path $env:JAVA_HOME 'bin/java')
            } else { 'java' }
            # -WindowStyle Hidden 仅 Windows 支持；Linux 没有窗口概念，直接启动
            $startArgs = @{
                FilePath               = $javaBin
                ArgumentList           = @('-cp', "`"$cp`"", 'com.yuwen.visionspace.acceptance.VipSecondInstanceMain', $secondReadyFile)
                PassThru               = $true
                RedirectStandardOutput = (Join-Path $runDir 'second-instance-out.log')
                RedirectStandardError  = (Join-Path $runDir 'second-instance-error.log')
            }
            if ($IsWindows) { $startArgs['WindowStyle'] = 'Hidden' }
            $script:SecondProcess = Start-Process @startArgs
            $script:SecondOwned = $true
            # 就绪握手：端口文件最多等 120 秒
            $deadline = [DateTime]::UtcNow.AddSeconds(120)
            while ([DateTime]::UtcNow -lt $deadline) {
                if ($script:SecondProcess.HasExited) { throw "second instance exited early (code $($script:SecondProcess.ExitCode)); see second-instance-error.log" }
                if (Test-Path $secondReadyFile) { break }
                Start-Sleep -Milliseconds 500
            }
            if (-not (Test-Path $secondReadyFile)) { throw 'BLOCKED_ENV: second instance did not become ready in 120s' }
            $secondPort = (Get-Content -Raw $secondReadyFile).Trim()
            mvn -B --no-transfer-progress "-Dacceptance.reportsDirectory=$runDir/surefire" "-Dvs.acceptance.second-port=$secondPort" verify
        }
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
            if ($results['frontend-build'] -eq 'PASS' -and $results['backend'] -eq 'PASS') {
                Run-Gate 'asset-color-calibration' {
                    $colorPython = Get-AcceptancePython
                    & $colorPython -I (Join-Path $repo 'scripts/calibrate-asset-colors.py')
                }
                Run-Gate 'asset-search-browser' {
                    Push-Location $repo
                    try {
                        mvn -B --no-transfer-progress "-Dacceptance.reportsDirectory=$runDir/browser-surefire" "-Dtest=AssetSearchBrowserIT,AssetBoardBrowserIT,BoardProposalBrowserIT" test
                    } finally { Pop-Location }
                }
            } else {
                $results['asset-color-calibration'] = 'BLOCKED_ENV'
                $results['asset-search-browser'] = 'BLOCKED_ENV'
            }
            # 后端接口变更后未重新生成客户端，是反复出现的 review 发现；这里用契约自动对账。
            if ($results['backend'] -eq 'PASS') {
                Run-Gate 'frontend-api-contract' { & pwsh -NoLogo -NoProfile -File (Join-Path $repo 'scripts/openapi-contract.ps1') }
            } else {
                $results['frontend-api-contract'] = 'BLOCKED_ENV'
            }
        } else {
            foreach ($gate in @('frontend-lint', 'frontend-format', 'frontend-typecheck', 'frontend-tests', 'frontend-build', 'frontend-api-contract', 'asset-color-calibration', 'asset-search-browser')) {
                $results[$gate] = 'BLOCKED_ENV'
            }
        }
    } finally { Pop-Location }
} finally {
    $results['cleanup'] = 'PASS'
    # #39 第二实例：写 stop 文件让其自行退出，再兜底强杀；
    # 成功与失败路径都必须没有残留进程——最后再按命令行特征兜底扫描一次。
    try {
        if ($script:SecondOwned) {
            try { New-Item -ItemType File -Path $secondStopFile -Force | Out-Null } catch {}
            if ($script:SecondProcess -and -not $script:SecondProcess.HasExited) {
                if (-not $script:SecondProcess.WaitForExit(10000)) {
                    Stop-Process -Id $script:SecondProcess.Id -ErrorAction Stop
                    $script:SecondProcess.WaitForExit(5000) | Out-Null
                }
            }
        }
    } catch {
        $results['cleanup'] = 'FAIL'
        $_ | Out-String | Add-Content (Join-Path $runDir 'second-instance-cleanup.log')
    }
    try {
        # 兜底扫描只针对本运行自己的第二实例：命令行里带着本轮就绪文件路径
        # （runDir 含逐次生成的 nonce），不会命中其他验收运行或用户进程。
        # 平台差异：Win32_Process 仅 Windows；Linux 用 /proc 扫描命令行。
        $stale = @()
        if ($IsWindows) {
            $stale = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
                Where-Object { $_.CommandLine -and $_.CommandLine.Contains('VipSecondInstanceMain') -and $_.CommandLine.Contains($runDir) })
        } else {
            $stale = @()
            foreach ($pidDir in (Get-ChildItem '/proc' -Directory -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -match '^[0-9]+$' })) {
                $cmdline = Get-Content -Raw -LiteralPath (Join-Path $pidDir.FullName 'cmdline') -ErrorAction SilentlyContinue
                if ($cmdline -and $cmdline.Contains('VipSecondInstanceMain') -and $cmdline.Contains($runDir)) {
                    $stale += [pscustomobject]@{ ProcessId = [int]$pidDir.Name }
                }
            }
        }
        foreach ($process in $stale) {
            try { Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop } catch {}
            $results['cleanup'] = 'FAIL'
            "swept stale second instance PID $($process.ProcessId)" |
                Add-Content (Join-Path $runDir 'second-instance-cleanup.log')
        }
    } catch {
        $results['cleanup'] = 'FAIL'
        $_ | Out-String | Add-Content (Join-Path $runDir 'second-instance-cleanup.log')
    }
    # Docker 资源：先快照本轮身份再清理，summary 才能指向容器/卷（失败时同样要留）。
    # Complete-VsDockerRun 按 run-id 标签删除容器、命名卷，并在最后断言无残留；
    # 本轮一步不跳过：隔离服务与验收实例全部是 Docker 资源。
    # 注意这里不能写成裸 `{ ... }`：PowerShell 里那是 scriptblock 字面量，不会执行，
    # 只会把源码打到输出里——之前正是这个原因让 docker 清理整段被跳过，
    # 报告显示 cleanup=PASS，容器却一直留在机器上。
    try {
        $dockerSnapshot = Get-VsDockerRunResources -RunId $runId
        $dockerResources.containers = @($dockerSnapshot.ContainerNames)
        $dockerResources.volumes = @($dockerSnapshot.Volumes)
        $logNames = @(@($postgresContainer, $redisContainer) | Where-Object { $_ })
        if (-not (Complete-VsDockerRun -RunId $runId -LogDirectory $runDir -LogNames $logNames)) {
            $results['cleanup'] = 'FAIL'
        }
        # Complete 的返回值不能代替一次独立核对：这一步以前被省掉，结果出现过
        # 「报告 cleanup=PASS、docker ps 里容器还在跑」的静默残留。清理后必须再数一遍。
        $afterCleanup = Get-VsDockerRunResources -RunId $runId
        $leaked = $afterCleanup.ContainerNames.Count + $afterCleanup.Volumes.Count
        "runId=$runId leaked=$leaked containers=$($afterCleanup.ContainerNames -join ',') volumes=$($afterCleanup.Volumes -join ',')" |
            Add-Content (Join-Path $runDir 'docker-cleanup.log')
        if ($leaked -gt 0) { $results['cleanup'] = 'FAIL' }
    } catch {
        $results['cleanup'] = 'FAIL'
        $_ | Out-String | Add-Content (Join-Path $runDir 'docker-cleanup.log')
    }
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name]) }
    # 覆盖对照计数：即使该关卡 FAIL 也照抄（失败时 problems 非空，同样是证据）。
    $testCoverage = $null
    $coveragePath = Join-Path $runDir 'test-coverage-map.json'
    if (Test-Path -LiteralPath $coveragePath) {
        $coverage = Get-Content -Raw -LiteralPath $coveragePath | ConvertFrom-Json
        $testCoverage = [ordered]@{
            baseRef = [string]$coverage.baseRef
            baseTestClasses = [int]$coverage.baseTestClasses
            currentTestClasses = [int]$coverage.currentTestClasses
            added = @($coverage.added).Count
            retired = @($coverage.retired).Count
            migrated = [int]$coverage.migrated
            problems = @($coverage.problems).Count
        }
    }
    $summary = [ordered]@{
        head = (git rev-parse HEAD)
        runId = $runId
        nonce = $nonce
        infrastructure = 'docker'
        docker = $dockerResources
        results = $results
        gateFailures = $failures
        testCoverage = $testCoverage
        logs = $runDir
        # A failure remains FAIL until baseline comparison establishes it was pre-existing.
        productionAcceptance = 'NOT_RUN'
    }
    $reportDir = Join-Path $repo "target/acceptance/$nonce"
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
    $summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $reportDir 'summary.json')
    Copy-Item -Path (Join-Path $runDir '*.log') -Destination $reportDir
    if (Test-Path -LiteralPath (Join-Path $runDir 'diagnostics')) {
        Copy-Item -LiteralPath (Join-Path $runDir 'diagnostics') -Destination $reportDir -Recurse
    }
    if (Test-Path -LiteralPath (Join-Path $runDir 'surefire')) {
        Copy-Item -LiteralPath (Join-Path $runDir 'surefire') -Destination $reportDir -Recurse
    }
    if (Test-Path -LiteralPath (Join-Path $runDir 'browser-surefire')) {
        Copy-Item -LiteralPath (Join-Path $runDir 'browser-surefire') -Destination $reportDir -Recurse
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $repo 'target/acceptance/summary.json')
    $summary | ConvertTo-Json -Depth 5 | Write-Host
    Pop-Location
}
if ($results.Values -contains 'FAIL') { exit 1 }
if ($results.Values -contains 'BLOCKED_ENV') { exit 2 }
exit 0
