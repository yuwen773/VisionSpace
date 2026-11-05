$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$dir = Join-Path ([IO.Path]::GetTempPath()) ('vs45-mysql-init-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $dir | Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'docker/mysql/init/01-create-app-user.sh') -Destination (Join-Path $dir 'init.sh')
$stub = @'
#!/bin/sh
printf '%s' "$MYSQL_PWD" > /fixture/password
printf '%s\n' "$@" > /fixture/arguments
cat > /fixture/statements.sql
'@
[IO.File]::WriteAllText((Join-Path $dir 'mysql'), $stub.Replace("`r`n", "`n"), [Text.UTF8Encoding]::new($false))
try {
    foreach ($mode in @('execute', 'source')) {
        $runner = if ($mode -eq 'source') { '. /fixture/init.sh' } else { 'bash /fixture/init.sh' }
        $command = 'chmod +x /fixture/mysql; export PATH="/fixture:$PATH"; ' + $runner
        docker run --rm --network none --mount "type=bind,source=$dir,target=/fixture" `
            -e MYSQL_ROOT_PASSWORD=root-fixture -e MYSQL_APP_PASSWORD=app-fixture -e MYSQL_DATABASE=fixture `
            mysql:8.0 bash -c $command
        if ($LASTEXITCODE -ne 0) { throw "MySQL init failed when run in $mode mode" }
        $sql = Get-Content -Raw -LiteralPath (Join-Path $dir 'statements.sql')
        $arguments = Get-Content -Raw -LiteralPath (Join-Path $dir 'arguments')
        $password = Get-Content -Raw -LiteralPath (Join-Path $dir 'password')
        if ($sql -notmatch "CREATE USER IF NOT EXISTS 'vision_app'" -or $sql -notmatch "CREATE USER IF NOT EXISTS 'healthcheck'") {
            throw "MySQL init did not create both users in $mode mode"
        }
        if ($password -ne 'root-fixture' -or $arguments -match 'root-fixture|app-fixture') {
            throw "MySQL init did not use the protected client password input in $mode mode"
        }
    }
    Write-Host 'PASS: MySQL init works both executed and sourced'
} finally {
    foreach ($name in @('init.sh', 'mysql', 'password', 'arguments', 'statements.sql')) {
        Remove-Item -LiteralPath (Join-Path $dir $name) -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $dir -Force -ErrorAction SilentlyContinue
}
