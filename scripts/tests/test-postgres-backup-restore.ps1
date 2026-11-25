$ErrorActionPreference = 'Stop'
# 清理断言靠 $LASTEXITCODE 判断「资源已不存在」；不让原生命令的 stderr 变成终止错误。
$PSNativeCommandUseErrorActionPreference = $false
Import-Module (Join-Path $PSScriptRoot '../acceptance-environment.psm1') -Force

# 备份恢复演练（§5.10 阶段 B 的回滚路径）：
#   1. 起隔离 PostgreSQL，由 Flyway 以 vision_migrator 建出基线；
#   2. 以运行角色 vision_app 写入一行真实数据（时间列显式写成很久以前）；
#   3. pg_dump -Fc 备份；
#   4. 恢复到同一实例里的新库（等价于「从最近备份恢复到新库 + 切连接串」）；
#   5. 在恢复库里以 vision_app 读回数据、写入、并验证触发器与最小权限边界都还在。
# 这条路径一旦坏掉，阶段 B 就没有回滚手段，所以做成必跑的门禁。
$run = Join-Path ([IO.Path]::GetTempPath()) ('vs48-pg-restore-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run | Out-Null
$nonce = [Guid]::NewGuid().ToString('N')
$service = $null
$restoredDatabase = "vs_restored_$nonce"
$markerId = 9160001

# 口令经 PGPASSWORD 环境变量传入，不进 argv。
function Invoke-Psql([string]$User, [string]$Database, [string]$Sql) {
    $value = & docker exec -i -e "PGPASSWORD=$nonce" $service.PostgresContainer psql --no-psqlrc -tA `
        -v ON_ERROR_STOP=1 -U $User -d $Database -c $Sql 2>$null
    if ($LASTEXITCODE -ne 0) { throw "psql failed as $User on $Database" }
    return (@($value | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' }) -join "`n")
}

try {
    $service = Start-AcceptanceDockerServices -RunDirectory $run -Database "vs_acceptance_$nonce" -Nonce $nonce
    $baselineSql = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '../../src/main/resources/db/migration/V1__baseline.sql')
    $expectedTables = [regex]::Matches($baselineSql, '(?m)^CREATE TABLE ').Count
    if ($expectedTables -eq 0) { throw 'FAIL: no CREATE TABLE statements found in the Flyway baseline' }

    # 运行角色写入业务数据：备份里的内容必须来自真实写入路径。
    # 时间列显式写成 2020 年，后面才能确定性地验证触发器在恢复库里仍然刷新。
    Invoke-Psql 'vision_app' $service.Database @"
INSERT INTO picture (id, url, name, user_id, create_time, update_time)
VALUES ($markerId, 'https://acceptance.invalid/backup.png', 'backup-marker', 9160000,
        timestamp '2020-01-01 00:00:00', timestamp '2020-01-01 00:00:00');
"@ | Out-Null

    # 备份：自定义格式（-Fc），与部署手册一致。
    $dumpPath = '/tmp/vs-backup.dump'
    & docker exec -e "PGPASSWORD=$nonce" $service.PostgresContainer `
        pg_dump -U vision_migrator -d $service.Database -Fc -f $dumpPath *> (Join-Path $run 'pg-dump.log')
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed (see pg-dump.log)' }

    # 恢复到新库：阶段 B 的回滚就是这个动作 + 切连接串。
    Invoke-Psql 'postgres' 'postgres' "CREATE DATABASE `"$restoredDatabase`" OWNER vision_migrator" | Out-Null
    & docker exec -e "PGPASSWORD=$nonce" $service.PostgresContainer `
        pg_restore -U postgres -d $restoredDatabase --exit-on-error $dumpPath *> (Join-Path $run 'pg-restore.log')
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed (see pg-restore.log)' }

    # 1) 结构：业务表与 Flyway 元数据都回来了。
    $restoredTables = [int](Invoke-Psql 'vision_app' $restoredDatabase `
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'")
    if ($restoredTables -ne $expectedTables) {
        throw "恢复库结构不完整：期望 $expectedTables 张业务表，实际 $restoredTables"
    }
    $migrations = [int](Invoke-Psql 'vision_app' $restoredDatabase `
            'SELECT count(*) FROM flyway_schema_history WHERE success')
    if ($migrations -lt 1) { throw '恢复库里没有成功的 Flyway 迁移记录：后续 migrate 会重复执行基线' }

    # 2) 数据 + 权限：运行角色能读回备份时的数据，也仍保有 DML。
    $name = Invoke-Psql 'vision_app' $restoredDatabase "SELECT name FROM picture WHERE id = $markerId"
    if ($name -ne 'backup-marker') { throw "恢复库里读不到备份时的数据，实际: '$name'" }
    Invoke-Psql 'vision_app' $restoredDatabase `
        "UPDATE picture SET name = 'backup-restored' WHERE id = $markerId" | Out-Null

    # 3) 触发器：恢复后 update_time 由触发器刷新到现在，而不是停在备份时的 2020 年。
    $refreshed = Invoke-Psql 'vision_app' $restoredDatabase `
        "SELECT update_time > timestamp '2020-01-02 00:00:00' FROM picture WHERE id = $markerId"
    if ($refreshed -ne 't') { throw "恢复库里的更新时间触发器没有生效，实际: '$refreshed'" }

    # 4) 最小权限边界随恢复一起保留：运行角色在新库里仍不能 DDL。
    & docker exec -i -e "PGPASSWORD=$nonce" $service.PostgresContainer psql --no-psqlrc -tA `
        -U vision_app -d $restoredDatabase -c 'CREATE TABLE restored_forbidden (id bigint)' *> (Join-Path $run 'restored-ddl.log')
    if ($LASTEXITCODE -eq 0) { throw '恢复库丢失了最小权限边界：vision_app 竟然能建表' }
} finally {
    if ($service) { Stop-AcceptanceDockerServices $service }
}
Write-Host 'PASS: pg_dump/pg_restore round-trip keeps schema, data, grants and triggers'
exit 0
