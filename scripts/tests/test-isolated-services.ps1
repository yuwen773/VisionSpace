$ErrorActionPreference = 'Stop'
# 清理断言要靠 $LASTEXITCODE 判断「资源已不存在」；不让原生命令的 stderr 变成终止错误。
$PSNativeCommandUseErrorActionPreference = $false
Import-Module (Join-Path $PSScriptRoot '../acceptance-environment.psm1') -Force
$run = Join-Path ([IO.Path]::GetTempPath()) ('vs47-services-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $run | Out-Null
$nonce = [Guid]::NewGuid().ToString('N')
$service = $null

# psql 裸值读取：-tA（tuples-only + unaligned）每行一个裸值，多列用 | 分隔。
# 口令经 PGPASSWORD 环境变量传入，不进 argv。
function Get-PsqlScalar([string]$Sql) {
    $rows = & docker exec -e "PGPASSWORD=$nonce" $service.PostgresContainer psql --no-psqlrc -tA -v ON_ERROR_STOP=1 `
        -c $Sql -U vision_app -d $service.Database 2>$null
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $Sql" }
    $lines = @($rows | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
    if ($lines.Count -eq 0) { throw "psql returned no rows: $Sql" }
    return $lines[0]
}

try {
    $service = Start-AcceptanceDockerServices -RunDirectory $run -Database "vs_acceptance_$nonce" -Nonce $nonce
    # 隔离标记：PostgreSQL 用库级注释承载（MySQL 时代的 acceptance_instance 表已不存在）。
    $marker = Get-PsqlScalar "SELECT shobj_description(oid, 'pg_database') FROM pg_database WHERE datname = current_database();"
    if ($marker -ne $nonce) { throw 'Dedicated database marker must be readable through the real client' }
    # 当前 schema 必须由 Flyway 建出来（业务表只由迁移角色 vision_migrator 创建）。
    $tables = & docker exec -e "PGPASSWORD=$nonce" $service.PostgresContainer psql --no-psqlrc -tA `
        -c "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;" -U vision_app -d $service.Database 2>$null
    if ($LASTEXITCODE -ne 0 -or $tables -notcontains 'picture' -or $tables -notcontains 'user_preference') { throw 'Current tracked schema must be bootstrapped' }
    # 导出的命令入口必须可用：容器内 psql，口令走环境变量。
    # 入口用 -tA 输出裸值，这里只需要断言「读到的第一行是一个 ≥1 的整数」。
    $probe = @(Invoke-AcceptancePostgres -Sql "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'" |
        ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
    if ($probe.Count -eq 0 -or [int]$probe[0] -lt 1) { throw 'Invoke-AcceptancePostgres must read the isolated database' }
} finally {
    if ($service) {
        Stop-AcceptanceDockerServices $service
        foreach ($name in $service.Containers) {
            & docker inspect $name *> $null
            if ($LASTEXITCODE -eq 0) { throw 'Owned container survived cleanup' }
        }
        # 命名卷不会被 docker rm -v 回收：清理后必须显式断言它也不存在。
        & docker volume inspect $service.Volume *> $null
        if ($LASTEXITCODE -eq 0) { throw 'Owned volume survived cleanup' }
    }
}
Write-Host 'PASS: isolated service command entry, current schema and owned-resource cleanup'
exit 0
