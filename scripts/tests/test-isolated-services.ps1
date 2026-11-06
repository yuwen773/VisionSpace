$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot '../acceptance-environment.psm1') -Force
$run = Join-Path ([IO.Path]::GetTempPath()) ('vs47-services-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $run | Out-Null
$nonce = [Guid]::NewGuid().ToString('N')
$service = $null
try {
    $service = Start-AcceptanceDockerServices -RunDirectory $run -Database "vs_acceptance_$nonce" -Nonce $nonce
    $marker = Invoke-AcceptanceMySql --user=vs_acceptance --batch --skip-column-names "vs_acceptance_$nonce" -e 'SELECT nonce FROM acceptance_instance'
    if ($LASTEXITCODE -ne 0 -or $marker -ne $nonce) { throw 'Dedicated database marker must be readable through the real client' }
    $tables = Invoke-AcceptanceMySql --user=vs_acceptance --batch --skip-column-names "vs_acceptance_$nonce" -e 'SHOW TABLES'
    if ($LASTEXITCODE -ne 0 -or $tables -notcontains 'picture' -or $tables -notcontains 'user_preference') { throw 'Current tracked schema must be bootstrapped' }
} finally {
    if ($service) {
        Stop-AcceptanceDockerServices $service
        foreach ($name in $service.Containers) {
            & docker inspect $name *> $null
            if ($LASTEXITCODE -eq 0) { throw 'Owned container survived cleanup' }
        }
    }
}
Write-Host 'PASS: isolated service command entry, current schema and owned-resource cleanup'
exit 0
