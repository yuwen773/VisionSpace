# Shared Docker adapter for the existing browser / collaboration acceptance drivers.
$ErrorActionPreference = 'Stop'

function Set-AcceptanceDockerServices($Services) { $script:DockerServices = $Services }

function Get-AcceptanceApplicationArguments([int]$MySqlPort, [int]$RedisPort, [string]$Database, [string]$Nonce) {
    # Child processes must not inherit production property overrides or JVM -D options.
    foreach ($entry in @(Get-ChildItem Env:)) {
        if ($entry.Name -match '^(SPRING_|VISION_SPACE_|ALIYUNAI_|OPENAI_|DASHSCOPE_|DROMARA_)|^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS)$') {
            [Environment]::SetEnvironmentVariable($entry.Name, $null)
        }
    }
    $jdbc = "jdbc:mysql://127.0.0.1:$MySqlPort/$Database`?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    @(
        "--spring.datasource.url=$jdbc", '--spring.datasource.username=vs_acceptance', "--spring.datasource.password=$Nonce",
        "--spring.datasource.hikari.jdbc-url=$jdbc", '--spring.datasource.hikari.username=vs_acceptance', "--spring.datasource.hikari.password=$Nonce",
        '--spring.data.redis.host=127.0.0.1', "--spring.data.redis.port=$RedisPort", "--spring.data.redis.password=$Nonce", '--spring.data.redis.database=15',
        "--spring.data.redis.url=redis://:$Nonce@127.0.0.1:$RedisPort/15",
        '--spring.ai.openai.base-url=http://127.0.0.1:9', '--spring.ai.openai.chat.base-url=http://127.0.0.1:9',
        '--spring.ai.openai.api-key=acceptance-dummy', '--spring.ai.dashscope.api-key=acceptance-dummy',
        '--spring.ai.dashscope.base-url=http://127.0.0.1:9', '--spring.ai.mcp.client.enabled=false',
        '--vision-space.vision-recognition.base-url=http://127.0.0.1:9', '--vision-space.vision-recognition.api-key=acceptance-dummy',
        '--vision-space.vision-recognition.model=acceptance-model', '--vision-space.pexels.api-key=acceptance-dummy', '--aliYunAi.apiKey=acceptance-dummy',
        '--vision-space.aliyun-ai.create-out-painting-task=http://127.0.0.1:9/out-painting',
        '--vision-space.aliyun-ai.get-out-painting-task=http://127.0.0.1:9/tasks/%s'
    )
}

function Invoke-AcceptanceMySql {
    # Keep the existing mysql command boundary, including SQL on stdin. No host services.
    $clientArgs = @('--no-defaults', '--default-character-set=utf8mb4') + @($args | Where-Object { $_ -notmatch '^--(host|port|password)=' -and $_ -ne '--no-defaults' })
    $inputText = @($input) -join "`n"
    $dockerArgs = @('exec', '-i', '-e', "MYSQL_PWD=$($script:DockerServices.Nonce)",
        $script:DockerServices.MySqlContainer, 'mysql') + $clientArgs
    if ($inputText) { $inputText | & docker @dockerArgs } else { & docker @dockerArgs }
}

function Start-AcceptanceDockerServices {
    param([string]$RunDirectory, [string]$Database, [string]$Nonce)
    if ($Database -notmatch '^vs[0-9a-z_]+$' -or $Nonce -notmatch '^[0-9a-f]{32}$') {
        throw 'Invalid isolated database identity.'
    }
    $services = [pscustomobject]@{
        MySqlContainer = "vs47-mysql-$Nonce"
        RedisContainer = "vs47-redis-$Nonce"
        Containers = @("vs47-mysql-$Nonce", "vs47-redis-$Nonce")
        Nonce = $Nonce
        MySqlPort = 0
        RedisPort = 0
    }
    Set-AcceptanceDockerServices $services
    try {
        & docker info *> (Join-Path $RunDirectory 'docker-info.log')
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: Docker engine unavailable.' }
        $repo = Split-Path $PSScriptRoot -Parent
        $ddl = Get-Content -Raw (Join-Path $repo 'sql/create_vision_space.sql')
        $tables = [regex]::Matches($ddl, '(?ms)^CREATE TABLE .*?^\)[^\r\n]*;')
        if ($tables.Count -eq 0 -or $tables.Count -ne [regex]::Matches($ddl, '(?m)^CREATE TABLE ').Count) {
            throw 'FAIL: incomplete tracked schema extraction.'
        }
        $sql = @"
CREATE DATABASE $Database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'vs_acceptance'@'%' IDENTIFIED BY '$Nonce';
GRANT ALL ON $Database.* TO 'vs_acceptance'@'%';
USE $Database;
CREATE TABLE acceptance_instance (nonce varchar(32) NOT NULL);
INSERT INTO acceptance_instance VALUES ('$Nonce');
$(( $tables | ForEach-Object { $_.Value }) -join "`n")
"@
        $bootstrap = Join-Path $RunDirectory 'bootstrap.sql'
        [IO.File]::WriteAllText($bootstrap, $sql, [Text.UTF8Encoding]::new($false))
        & docker run -d --name $services.MySqlContainer -p '127.0.0.1::3306' -e "MYSQL_ROOT_PASSWORD=$Nonce" `
            --mount "type=bind,source=$bootstrap,target=/docker-entrypoint-initdb.d/acceptance.sql,readonly" mysql:8.0.36 *> (Join-Path $RunDirectory 'mysql-container.log')
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: isolated MySQL startup failed.' }
        & docker run -d --name $services.RedisContainer -p '127.0.0.1::6379' redis:7.2.4 redis-server --requirepass $Nonce *> (Join-Path $RunDirectory 'redis-container.log')
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: isolated Redis startup failed.' }
        $services.MySqlPort = [int]((& docker port $services.MySqlContainer 3306) -split ':')[-1]
        $services.RedisPort = [int]((& docker port $services.RedisContainer 6379) -split ':')[-1]
        $deadline = [DateTime]::UtcNow.AddSeconds(180)
        $ready = $false
        while ([DateTime]::UtcNow -lt $deadline) {
            # TCP forces the final server, never entrypoint's temporary bootstrap socket.
            $marker = & docker exec -e "MYSQL_PWD=$Nonce" $services.MySqlContainer mysql --protocol=TCP --host=127.0.0.1 --user=vs_acceptance --batch --skip-column-names $Database -e 'SELECT nonce FROM acceptance_instance' 2>$null
            if ($LASTEXITCODE -eq 0 -and $marker -eq $Nonce) {
                $actual = @(Invoke-AcceptanceMySql --user=vs_acceptance --batch --skip-column-names $Database -e 'SHOW TABLES')
                if ($LASTEXITCODE -eq 0 -and $actual.Count -eq $tables.Count + 1) { $ready = $true; break }
            }
            Start-Sleep -Milliseconds 500
        }
        if (-not $ready) { throw 'BLOCKED_ENV: isolated schema did not become ready.' }
        & docker exec $services.RedisContainer redis-cli -a $Nonce -n 15 SET acceptance:instance $Nonce *> (Join-Path $RunDirectory 'redis-marker.log')
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: Redis marker creation failed.' }
        return $services
    } catch {
        Stop-AcceptanceDockerServices $services
        throw
    }
}

function Stop-AcceptanceDockerServices($Services) {
    foreach ($name in $Services.Containers) {
        if ($name -notmatch '^vs47-(mysql|redis)-[0-9a-f]{32}$') { throw 'Refusing unowned container cleanup.' }
        & docker rm -f -v $name *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Owned isolated container cleanup failed.' }
    }
}

Export-ModuleMember -Function Start-AcceptanceDockerServices, Stop-AcceptanceDockerServices, Set-AcceptanceDockerServices, Invoke-AcceptanceMySql, Get-AcceptanceApplicationArguments
