# Shared Docker adapter for the browser / collaboration acceptance drivers.
#
# PostgreSQL 18 起官方镜像把数据目录版本化：卷必须挂到 /var/lib/postgresql
# （PGDATA=/var/lib/postgresql/18/docker），旧的 /var/lib/postgresql/data 已不存在。
# 业务表只由 Flyway（迁移角色 vision_migrator）创建；这里的初始化脚本只建角色、
# 建库、装扩展、写库级注释与 UTC 时区，不建任何业务表。
$ErrorActionPreference = 'Stop'

$script:PostgresImage = 'postgres:18.7'
$script:FlywayVersion = '13.13.0'

function Set-AcceptanceDockerServices($Services) { $script:DockerServices = $Services }

function Get-AcceptanceApplicationArguments([int]$PostgresPort, [int]$RedisPort, [string]$Database, [string]$Nonce) {
    # Child processes must not inherit production property overrides or JVM -D options.
    foreach ($entry in @(Get-ChildItem Env:)) {
        if ($entry.Name -match '^(SPRING_|VISION_SPACE_|ALIYUNAI_|OPENAI_|DASHSCOPE_|DROMARA_)|^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS)$') {
            [Environment]::SetEnvironmentVariable($entry.Name, $null)
        }
    }
    $jdbc = "jdbc:postgresql://127.0.0.1:$PostgresPort/$Database"
    @(
        "--spring.datasource.url=$jdbc", '--spring.datasource.username=vision_app', "--spring.datasource.password=$Nonce",
        "--spring.datasource.hikari.jdbc-url=$jdbc", '--spring.datasource.hikari.username=vision_app', "--spring.datasource.hikari.password=$Nonce",
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

function Invoke-AcceptancePostgres {
    # psql 在容器内执行：口令经环境变量传入，不进命令行。
    # -tA（tuples-only + unaligned）：每行一个裸值，调用方拿到的是可直接比较的结果，
    # 不带表头、分隔线与 "(1 row)" 尾巴。
    param([Parameter(Mandatory = $true)][string]$Sql)
    $services = $script:DockerServices
    & docker exec -i -e "PGPASSWORD=$($services.Nonce)" `
        $services.PostgresContainer 'psql' '--no-psqlrc' '-tA' '-v' 'ON_ERROR_STOP=1' `
        '-U' $services.AppUser '-d' $services.Database '-c' $Sql
}

function Wait-AcceptanceDockerReady([string]$Container, [string]$Database, [string]$Nonce, [string]$LogDirectory) {
    $deadline = [DateTime]::UtcNow.AddSeconds(180)
    while ([DateTime]::UtcNow -lt $deadline) {
        # 运行角色 + 目标库：既验证服务器接受连接，也验证角色与库已经建好。
        & docker exec -e "PGPASSWORD=$Nonce" $Container psql --no-psqlrc -tAc 'SELECT 1' -U vision_app -d $Database 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Milliseconds 500
    }
    throw 'BLOCKED_ENV: isolated PostgreSQL did not become ready.'
}

function Invoke-AcceptanceFlyway([int]$PostgresPort, [string]$Database, [string]$Nonce) {
    # 唯一建表入口：Flyway 以迁移角色 vision_migrator 执行基线。口令走环境变量，
    # 不写进命令行（避免出现在进程列表与日志里）。
    $names = @('FLYWAY_URL', 'FLYWAY_USER', 'FLYWAY_PASSWORD')
    $saved = @{}
    foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name) }
    $repo = Split-Path $PSScriptRoot -Parent
    Push-Location $repo
    try {
        $env:FLYWAY_URL = "jdbc:postgresql://127.0.0.1:$PostgresPort/$Database"
        $env:FLYWAY_USER = 'vision_migrator'
        $env:FLYWAY_PASSWORD = $Nonce
        & mvn -B -N --no-transfer-progress "org.flywaydb:flyway-maven-plugin:$($script:FlywayVersion):migrate"
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: Flyway migrate failed.' }
        & mvn -B -N --no-transfer-progress "org.flywaydb:flyway-maven-plugin:$($script:FlywayVersion):validate"
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: Flyway validate failed.' }
    } finally {
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name]) }
        Pop-Location
    }
}

function Start-AcceptanceDockerServices {
    param([string]$RunDirectory, [string]$Database, [string]$Nonce)
    if ($Database -notmatch '^vs[0-9a-z_]+$' -or $Nonce -notmatch '^[0-9a-f]{32}$') {
        throw 'Invalid isolated database identity.'
    }
    $volume = "vs47-pgdata-$Nonce"
    $services = [pscustomobject]@{
        PostgresContainer = "vs47-postgres-$Nonce"
        RedisContainer    = "vs47-redis-$Nonce"
        Volume            = $volume
        Containers        = @("vs47-postgres-$Nonce", "vs47-redis-$Nonce")
        Nonce             = $Nonce
        Database          = $Database
        AppUser           = 'vision_app'
        PostgresPort      = 0
        RedisPort         = 0
    }
    Set-AcceptanceDockerServices $services
    try {
        & docker info *> (Join-Path $RunDirectory 'docker-info.log')
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: Docker engine unavailable.' }

        # 初始化脚本只做四件事：建角色、建库、装扩展/时区/库注释。不建业务表。
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
        $initPath = Join-Path $RunDirectory 'postgres-init.sh'
        [IO.File]::WriteAllText($initPath, $initScript.Replace("`r`n", "`n"), [Text.UTF8Encoding]::new($false))

        # postgres:18 声明 VOLUME /var/lib/postgresql：显式挂命名卷，避免匿名卷残留。
        & docker volume create --label 'vs.owner=vision-space' --label "vs.run-id=$Nonce" --label 'vs.kind=test' $volume *> (Join-Path $RunDirectory 'postgres-volume.log')
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: isolated PostgreSQL volume creation failed.' }
        # 全量套件会同时存活多个 Spring 上下文（含双实例用例），每个连接池都要连接；
        # 镜像默认 max_connections=100 会被用尽，双实例用例因此拿不到连接。测试实例放宽到 200。
        & docker run -d --name $services.PostgresContainer -p '127.0.0.1::5432' `
            -e "POSTGRES_PASSWORD=$Nonce" -e 'POSTGRES_USER=postgres' -e 'POSTGRES_DB=postgres' `
            -e "ACCEPTANCE_DB=$Database" -e "ACCEPTANCE_NONCE=$Nonce" -e 'TZ=UTC' `
            --label 'vs.owner=vision-space' --label "vs.run-id=$Nonce" --label 'vs.kind=test' `
            -v "${volume}:/var/lib/postgresql" `
            --mount "type=bind,source=$initPath,target=/docker-entrypoint-initdb.d/01-acceptance.sh,readonly" `
            $script:PostgresImage -c max_connections=200 *> (Join-Path $RunDirectory 'postgres-container.log')
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: isolated PostgreSQL startup failed.' }
        & docker run -d --name $services.RedisContainer -p '127.0.0.1::6379' `
            --label 'vs.owner=vision-space' --label "vs.run-id=$Nonce" --label 'vs.kind=test' `
            redis:7.2.4 redis-server --requirepass $Nonce *> (Join-Path $RunDirectory 'redis-container.log')
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED_ENV: isolated Redis startup failed.' }
        $services.PostgresPort = [int]((& docker port $services.PostgresContainer 5432) -split ':')[-1]
        $services.RedisPort = [int]((& docker port $services.RedisContainer 6379) -split ':')[-1]
        Wait-AcceptanceDockerReady $services.PostgresContainer $Database $Nonce $RunDirectory
        Invoke-AcceptanceFlyway $services.PostgresPort $Database $Nonce
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
        if ($name -notmatch '^vs47-(postgres|redis)-[0-9a-f]{32}$') { throw 'Refusing unowned container cleanup.' }
        & docker rm -f -v $name *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Owned isolated container cleanup failed.' }
    }
    # 命名卷不会被 docker rm -v 回收（匿名卷同样不会）：显式删除并断言不存在。
    if ($Services.Volume) {
        if ($Services.Volume -notmatch '^vs47-pgdata-[0-9a-f]{32}$') { throw 'Refusing unowned volume cleanup.' }
        & docker volume rm $Services.Volume *> $null
    }
}

Export-ModuleMember -Function Start-AcceptanceDockerServices, Stop-AcceptanceDockerServices, Set-AcceptanceDockerServices, Invoke-AcceptancePostgres, Invoke-AcceptanceFlyway, Get-AcceptanceApplicationArguments
