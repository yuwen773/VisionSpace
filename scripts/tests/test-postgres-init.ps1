$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$dir = Join-Path ([IO.Path]::GetTempPath()) ('vs45-postgres-init-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $dir | Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'docker/postgres/init/01-roles.sh') -Destination (Join-Path $dir 'init.sh')

# psql 桩：记录参数与 stdin 里的 SQL，不连接任何数据库。
# 生产镜像里同一脚本由 docker-entrypoint 执行；这里只验证脚本自身的收敛性。
$stub = @'
#!/bin/sh
printf '%s\n' "$@" > /fixture/arguments
cat > /fixture/statements.sql
printf '%s' "${PGPASSWORD:-}" > /fixture/password
'@
[IO.File]::WriteAllText((Join-Path $dir 'psql'), $stub.Replace("`r`n", "`n"), [Text.UTF8Encoding]::new($false))
try {
    foreach ($mode in @('execute', 'source')) {
        # 两种调用方式都要成立：镜像 entrypoint 走 bash，运维手工 source 也常见。
        $runner = if ($mode -eq 'source') { '. /fixture/init.sh' } else { 'bash /fixture/init.sh' }
        $command = 'chmod +x /fixture/psql; export PATH="/fixture:$PATH"; ' + $runner
        docker run --rm --network none --mount "type=bind,source=$dir,target=/fixture" `
            -e POSTGRES_USER=postgres -e POSTGRES_DB=fixture `
            -e POSTGRES_MIGRATOR_PASSWORD=migrator-fixture -e POSTGRES_APP_PASSWORD=app-fixture `
            postgres:18.7 bash -c $command *> (Join-Path $dir "$mode.log")
        if ($LASTEXITCODE -ne 0) {
            Get-Content -LiteralPath (Join-Path $dir "$mode.log") -Tail 40 | ForEach-Object { Write-Host $_ }
            throw "postgres init script failed in $mode mode"
        }

        $sql = Get-Content -Raw -LiteralPath (Join-Path $dir 'statements.sql')
        $arguments = Get-Content -Raw -LiteralPath (Join-Path $dir 'arguments')

        # 业务表只能由 Flyway 创建：初始化脚本出现 CREATE TABLE 就是越界。
        if ($sql -match '(?i)\bCREATE\s+TABLE\b') {
            throw "init script must not create business tables ($mode mode)"
        }
        foreach ($required in @(
            'CREATE ROLE vision_migrator LOGIN PASSWORD',
            'NOSUPERUSER NOCREATEDB NOCREATEROLE',
            'CREATE ROLE vision_app LOGIN PASSWORD',
            'NOINHERIT',
            'ALTER DATABASE :"dbname" OWNER TO vision_migrator',
            'REVOKE CREATE ON SCHEMA public FROM PUBLIC',
            'GRANT USAGE ON SCHEMA public TO vision_app',
            'CREATE EXTENSION IF NOT EXISTS pgcrypto',
            "ALTER DATABASE :`"dbname`" SET timezone = 'Asia/Shanghai'"
        )) {
            if (-not $sql.Contains($required)) {
                throw "init script is missing '$required' ($mode mode)"
            }
        }

        # 口令经 psql 变量传入（-v），SQL 里只出现占位符 :'migrator_pw' / :'app_pw'。
        if (-not ($arguments -match '(?m)^-v$') -or -not $arguments.Contains('migrator_pw=migrator-fixture')) {
            throw "init script must pass the migrator password as a psql variable ($mode mode)"
        }
        if (-not $arguments.Contains('app_pw=app-fixture')) {
            throw "init script must pass the app password as a psql variable ($mode mode)"
        }
        if ($sql.Contains('migrator-fixture') -or $sql.Contains('app-fixture')) {
            throw "the password must not be inlined into SQL text ($mode mode)"
        }
        foreach ($placeholder in @(":'migrator_pw'", ":'app_pw'")) {
            if (-not $sql.Contains($placeholder)) { throw "SQL must reference $placeholder ($mode mode)" }
        }
        if (-not ($arguments -match '(?m)^--username$') -or -not ($arguments -match '(?m)^--dbname$')) {
            throw "init script must target an explicit role and database ($mode mode)"
        }
        Write-Host "PASS: postgres init script ($mode mode)"
    }
    Write-Host 'PASS: postgres init creates only roles/extension/settings, never business tables'
} finally {
    Get-ChildItem -LiteralPath $dir -File -ErrorAction SilentlyContinue | Remove-Item -Force
    Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue
}
exit 0
