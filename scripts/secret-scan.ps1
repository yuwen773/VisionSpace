# 仓库候选内容的脱敏秘密扫描。
#
# 只报告"文件:行号:规则名"，不输出命中内容，避免扫描结果本身成为泄漏源。
# 找到命中时退出 1；扫描本身无法执行时退出 2（BLOCKED_ENV）。
[CmdletBinding()]
param(
    [string]$Root = (Split-Path $PSScriptRoot -Parent),
    [string[]]$Include = @('*.yml', '*.yaml', '*.properties', '*.env', '*.example', '*.json', '*.md', '*.xml', '*.java', '*.ts', '*.sql')
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

# 规则只匹配"像真实凭据"的形态，不匹配占位说明与测试假值。
$rules = @(
    @{ Name = 'private-key-block'; Pattern = '-----BEGIN [A-Z ]*PRIVATE KEY-----' },
    @{ Name = 'aliyun-access-key-id'; Pattern = 'LTAI[0-9A-Za-z]{12,}' },
    @{ Name = 'aws-access-key-id'; Pattern = '(AKIA|ASIA)[0-9A-Z]{16}' },
    @{ Name = 'dashscope-or-openai-key'; Pattern = 'sk-[A-Za-z0-9_.-]{24,}' },
    @{ Name = 'hardcoded-password'; Pattern = '(?i)\b(password|passwd)\s*[:=]\s*["'']?(?!\$\{|your_|example|placeholder|xxx|changeme|acceptance|test)[^\s"''#]{8,}' },
    @{ Name = 'hardcoded-api-key'; Pattern = '(?i)\b(api[-_]?key|secret[-_]?key|access[-_]?key)\s*[:=]\s*["'']?(?!\$\{|your_|example|placeholder|xxx|changeme|acceptance|test)[^\s"''#]{16,}' }
)

# 候选内容 = 已跟踪文件 + 未被忽略的未跟踪文件。
# 被 .gitignore 排除的本地配置不在提交候选内，另行说明而不在此判定。
# 传入 -Root 且该目录不是 git 仓库时，退化为整树扫描（用于核对导出内容）。
Push-Location $Root
try {
    $insideRepo = (& git rev-parse --is-inside-work-tree 2>$null) -eq 'true'
    if ($insideRepo) {
        $tracked = git ls-files
        $untracked = git ls-files --others --exclude-standard
        $candidates = @($tracked) + @($untracked) | Where-Object { $_ } | Sort-Object -Unique
    } else {
        $candidates = Get-ChildItem -LiteralPath $Root -Recurse -File |
            ForEach-Object { [IO.Path]::GetRelativePath($Root, $_.FullName) }
    }
} catch {
    Write-Host "BLOCKED_ENV: unable to enumerate candidate files"
    exit 2
} finally {
    Pop-Location
}

$extensions = $Include | ForEach-Object { $_.TrimStart('*') }
$files = $candidates |
    Where-Object {
        $_ -notmatch '[\\/](node_modules|target|\.git|\.worktrees)[\\/]' -and
        $extensions -contains ([IO.Path]::GetExtension($_))
    } |
    ForEach-Object { Get-Item -LiteralPath (Join-Path $Root $_) -ErrorAction SilentlyContinue } |
    Where-Object { $_ -ne $null }

$findings = New-Object System.Collections.Generic.List[string]
foreach ($file in $files) {
    $relative = [IO.Path]::GetRelativePath($Root, $file.FullName)
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName -ErrorAction SilentlyContinue) {
        $lineNumber++
        foreach ($rule in $rules) {
            if ($line -match $rule.Pattern) {
                # 仅记录位置与规则名，绝不回显命中文本。
                $findings.Add("${relative}:${lineNumber}: $($rule.Name)")
            }
        }
    }
}

if ($findings.Count -gt 0) {
    Write-Host "SECRET SCAN: $($findings.Count) potential secret(s) found (values withheld):"
    $findings | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host "SECRET SCAN: no credentials detected in $($files.Count) candidate file(s)."
exit 0
