$ErrorActionPreference = 'Stop'
# 清理断言靠 $LASTEXITCODE 判断「资源已不存在」；不让原生命令的 stderr 变成终止错误。
$PSNativeCommandUseErrorActionPreference = $false
Import-Module (Join-Path $PSScriptRoot '../docker-test-harness.psm1') -Force

# PostgreSQL 18 官方镜像把数据目录版本化：
#   * 镜像声明 VOLUME /var/lib/postgresql；
#   * PGDATA=/var/lib/postgresql/18/docker；
#   * 旧路径 /var/lib/postgresql/data 在 18 镜像里不存在。
# 旧路径写错不会在启动时报错——容器会安静地在一个新的空目录上初始化，
# 重启/重建才发现数据全没了。所以这里做一次真实的「销毁容器 → 重建 → 数据仍在」。
# run-id 必须是小写形式：harness 会把 run-id 归一化成小写写进 vs.run-id 标签，
# 而清理与残留断言都按标签精确匹配，用原始大写值会让「清理干净」变成空断言。
$runId = Initialize-VsDockerRun -RunId ('vs-pg-persist-' + [Guid]::NewGuid().ToString('N'))
$logDir = Join-Path ([IO.Path]::GetTempPath()) ('vs48-pg-persist-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $logDir | Out-Null
$image = 'postgres:18.7'
$nonce = [Guid]::NewGuid().ToString('N').Substring(0, 24)
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$first = "vs-pg-persist-a-$suffix"
$second = "vs-pg-persist-b-$suffix"
$volume = $null

function Wait-Postgres([string]$Container) {
    # 必须等一次真实查询成功，不能只等 pg_isready：
    # 官方镜像初始化时先起一个只监听 unix socket 的临时服务器，
    # pg_isready 在那个窗口会返回 0，随后临时服务器退出、正式服务器才起来，
    # 此时的第一次真实查询会失败——CI 机器负载高时这个竞态很容易被撞上
    # （实测并发四份时约四分之一概率失败，报 psql failed: SHOW data_directory）。
    $deadline = [DateTime]::UtcNow.AddSeconds(180)
    while ([DateTime]::UtcNow -lt $deadline) {
        & docker exec -e "PGPASSWORD=$nonce" $Container psql --no-psqlrc -tA -v ON_ERROR_STOP=1 `
            -U postgres -d postgres -c 'SELECT 1' *> $null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "BLOCKED_ENV: $Container did not accept connections."
}

# 裸值读取：-tA（tuples-only + unaligned），口令经 PGPASSWORD，不进 argv。
# 重试几次：服务器刚切到正式实例的瞬间，一次连接失败不代表数据有问题。
function Invoke-Probe([string]$Container, [string]$User, [string]$Sql) {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $value = & docker exec -e "PGPASSWORD=$nonce" $Container psql --no-psqlrc -tA -v ON_ERROR_STOP=1 `
            -U $User -d postgres -c $Sql 2>$null
        if ($LASTEXITCODE -eq 0) {
            return (@($value | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' }) -join "`n")
        }
        Start-Sleep -Seconds 2
    }
    throw "psql failed: $Sql"
}

$cleanTeardown = $false
try {
    if (-not (Test-VsDockerAvailable)) { throw 'BLOCKED_ENV: Docker engine unavailable.' }

    $volume = New-VsVolume -Suffix 'pgdata'
    # 18 镜像必须挂 /var/lib/postgresql（不是 .../data）。
    $started = Start-VsDockerContainer -Name $first -LogPath (Join-Path $logDir "$first.log") -Arguments @(
        '-e', "POSTGRES_PASSWORD=$nonce", '-e', 'POSTGRES_USER=postgres', '-e', 'POSTGRES_DB=postgres',
        '-e', 'TZ=UTC', '-v', "${volume}:/var/lib/postgresql", $image
    )
    if (-not $started) { throw 'BLOCKED_ENV: first PostgreSQL container failed to start.' }
    Wait-Postgres $first

    $dataDirectory = Invoke-Probe $first 'postgres' 'SHOW data_directory'
    if ($dataDirectory -ne '/var/lib/postgresql/18/docker') {
        throw "PG 18 的 PGDATA 应为 /var/lib/postgresql/18/docker，实际: $dataDirectory"
    }

    # 显式只挂一个命名卷：镜像声明的 VOLUME 若没被覆盖，Docker 会再配一个匿名卷，
    # 重建容器时数据就散落在无人认领的卷里。
    $mounts = @(& docker inspect --format '{{range .Mounts}}{{.Type}}|{{.Name}}{{println}}{{end}}' $first |
        ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
    if ($mounts.Count -ne 1 -or $mounts[0] -ne "volume|$volume") {
        throw "容器挂载必须恰好是本轮命名卷 ${volume}，实际: $($mounts -join ', ')"
    }

    # 运行角色：集群级角色也在数据卷里，重建后必须仍然可用。
    Invoke-Probe $first 'postgres' "CREATE ROLE persistence_probe LOGIN PASSWORD '$nonce'" | Out-Null
    Invoke-Probe $first 'postgres' @"
CREATE TABLE persistence_probe (id integer PRIMARY KEY, note text NOT NULL);
INSERT INTO persistence_probe (id, note) VALUES (1, 'survives-container-recreation');
GRANT SELECT ON persistence_probe TO persistence_probe;
"@ | Out-Null

    # 销毁容器，保留卷 —— 模拟镜像升级 / 编排重建。
    & docker rm -f -v $first *> (Join-Path $logDir "$first-removed.log")
    if ($LASTEXITCODE -ne 0) { throw 'failed to remove the first container' }
    & docker inspect $first *> $null
    if ($LASTEXITCODE -eq 0) { throw 'first container must be gone before recreation' }

    $restarted = Start-VsDockerContainer -Name $second -LogPath (Join-Path $logDir "$second.log") -Arguments @(
        '-e', "POSTGRES_PASSWORD=$nonce", '-e', 'POSTGRES_USER=postgres', '-e', 'POSTGRES_DB=postgres',
        '-e', 'TZ=UTC', '-v', "${volume}:/var/lib/postgresql", $image
    )
    if (-not $restarted) { throw 'recreated PostgreSQL container failed to start on the same volume' }
    Wait-Postgres $second

    # 以运行角色（非超级用户）读取：证明数据与授权都随卷保留。
    $note = Invoke-Probe $second 'persistence_probe' 'SELECT note FROM persistence_probe WHERE id = 1'
    if ($note -ne 'survives-container-recreation') {
        throw "容器重建后数据丢失：实际读到 '$note'"
    }
    if ((Invoke-Probe $second 'postgres' 'SHOW data_directory') -ne $dataDirectory) {
        throw 'recreated container must use the same PGDATA path'
    }

    # harness 契约：命名卷 + vs.* 标签，清理只按本轮 run-id。
    # 期望值取自 harness 自己的归一化 run-id（标签里写的是小写化后的值）。
    $labels = @(& docker volume inspect --format '{{range $k, $v := .Labels}}{{$k}}={{$v}}{{println}}{{end}}' $volume |
        ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
    foreach ($expected in @("vs.run-id=$(Get-VsRunId)", 'vs.owner=vision-space', 'vs.kind=test')) {
        if ($labels -notcontains $expected) { throw "卷缺少 harness 标签 ${expected}：$($labels -join ', ')" }
    }
} finally {
    $cleanTeardown = Complete-VsDockerRun -RunId $runId -LogDirectory $logDir -LogNames @($first, $second)
}

if (-not $cleanTeardown) { throw 'Docker resources survived teardown for this run' }
Assert-VsClean -RunId $runId | Out-Null
Write-Host 'PASS: PG 18 data volume survives container recreation and is reclaimed by run-id'
exit 0
