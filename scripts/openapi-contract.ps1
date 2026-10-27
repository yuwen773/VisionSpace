# 校验前端生成客户端与后端契约一致。
#
# 后端接口变更后必须重新生成 frontend/src/api（CLAUDE.md 的约定），但 npm run openapi2ts
# 默认要求 localhost:8081 有活后端，review 时无法自动判定是否漏做。本脚本从隔离验收
# 已导出的 target/openapi-contract.json 生成到临时目录，再与入库产物逐字节比较。
#
# 用法（先跑一次 scripts/acceptance.ps1，它会产出契约）：
#   pwsh.exe -NoLogo -NoProfile -File ./scripts/openapi-contract.ps1
# 退出码：0 一致；1 漂移；2 缺少契约或前端依赖（BLOCKED_ENV）。
[CmdletBinding()]
param(
    [string]$Contract = 'target/openapi-contract.json'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$contractPath = if ([IO.Path]::IsPathRooted($Contract)) { $Contract } else { Join-Path $repo $Contract }

if (-not (Test-Path -LiteralPath $contractPath -PathType Leaf)) {
    Write-Host "BLOCKED_ENV: 未找到契约 $contractPath；先运行 scripts/acceptance.ps1。"
    exit 2
}
$frontend = Join-Path $repo 'frontend'
if (-not (Test-Path -LiteralPath (Join-Path $frontend 'node_modules') -PathType Container)) {
    Write-Host 'BLOCKED_ENV: frontend/node_modules 缺失，先运行 npm ci。'
    exit 2
}

$scratch = Join-Path ([IO.Path]::GetTempPath()) "visionspace-openapi-$([Guid]::NewGuid().ToString('N'))"
$tracked = Join-Path $frontend 'src/api'
try {
    # 生成器会 rimraf serversPath/api，所以只允许它写临时目录。
    New-Item -ItemType Directory -Path $scratch -Force | Out-Null
    Push-Location $frontend
    try {
        $env:OPENAPI_SCHEMA_PATH = $contractPath
        $env:OPENAPI_SERVERS_PATH = $scratch
        & node (Join-Path $frontend 'scripts/openapi-check.mjs')
        if ($LASTEXITCODE -ne 0) { throw "生成器退出码 $LASTEXITCODE" }
    } finally {
        Pop-Location
        Remove-Item Env:OPENAPI_SCHEMA_PATH -ErrorAction SilentlyContinue
        Remove-Item Env:OPENAPI_SERVERS_PATH -ErrorAction SilentlyContinue
    }

    $generated = Join-Path $scratch 'api'
    if (-not (Test-Path -LiteralPath $generated -PathType Container)) {
        Write-Host 'BLOCKED_ENV: 生成器没有产出 api 目录。'
        exit 2
    }
    $diff = & git --no-pager diff --no-index --stat -- $tracked $generated 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'FAIL: 前端生成客户端与后端契约不一致，运行 npm run openapi2ts 后提交。'
        $diff | Select-Object -Last 40 | Write-Host
        exit 1
    }
    Write-Host 'PASS: 生成客户端与后端契约一致。'
    exit 0
} finally {
    Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue
}
