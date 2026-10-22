$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot '../acceptance-results.psm1') -Force
$fixtures = Join-Path ([IO.Path]::GetTempPath()) ('vs-result-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixtures | Out-Null
try {
    $report = Join-Path $fixtures 'TEST-example.xml'
    '<testsuite><testcase><error>BLOCKED_ENV: instance unavailable</error></testcase></testsuite>' | Set-Content $report
    if ((Get-BackendTestStatus -ExitCode 1 -ReportDirectory $fixtures) -ne 'BLOCKED_ENV') { throw 'Isolation errors must be BLOCKED_ENV' }
    '<testsuite><testcase><error>BLOCKED_ENV: instance unavailable</error></testcase><testcase><failure>expected 7, was 8</failure></testcase></testsuite>' | Set-Content $report
    if ((Get-BackendTestStatus -ExitCode 1 -ReportDirectory $fixtures) -ne 'FAIL') { throw 'An assertion failure must take precedence over isolation errors' }
    '<testsuite><testcase><error>BLOCKED_ENV</error></testcase><testcase><error>NullPointerException</error></testcase></testsuite>' | Set-Content $report
    if ((Get-BackendTestStatus -ExitCode 1 -ReportDirectory $fixtures) -ne 'FAIL') { throw 'A genuine test error must take precedence over isolation errors' }
    if ((Get-BackendTestStatus -ExitCode 0 -ReportDirectory $fixtures) -ne 'PASS') { throw 'Successful execution must be PASS' }
    '<testsuite><testcase>' | Set-Content $report
    if ((Get-BackendTestStatus -ExitCode 1 -ReportDirectory $fixtures) -ne 'FAIL') { throw 'An incomplete test report must remain FAIL' }
    Remove-Item -LiteralPath $report
    if ((Get-BackendTestStatus -ExitCode 1 -ReportDirectory $fixtures) -ne 'FAIL') { throw 'A build failure without test reports must remain FAIL' }
    Write-Host 'PASS: six acceptance result scenarios'
} finally {
    # This exact, freshly-created directory contains only the fixture file.
    if (Test-Path -LiteralPath (Join-Path $fixtures 'TEST-example.xml')) { Remove-Item -LiteralPath (Join-Path $fixtures 'TEST-example.xml') }
    Remove-Item -LiteralPath $fixtures
}
