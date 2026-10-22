function Get-BackendTestStatus {
    param([int]$ExitCode, [string]$ReportDirectory)
    if ($ExitCode -eq 0) { return 'PASS' }
    # A log substring cannot distinguish mixed test and infrastructure failures.
    $hasIsolationError = $false
    foreach ($file in Get-ChildItem -LiteralPath $ReportDirectory -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue) {
        try { [xml]$report = Get-Content -Raw -LiteralPath $file.FullName } catch { return 'FAIL' }
        foreach ($case in $report.testsuite.testcase) {
            if ($case.SelectSingleNode('failure')) { return 'FAIL' }
            $testError = $case.SelectSingleNode('error')
            if ($testError) {
                if ($testError.InnerText -match 'BLOCKED_ENV|ApplicationContext failure threshold') {
                    $hasIsolationError = $true
                } else { return 'FAIL' }
            }
        }
    }
    if ($hasIsolationError) { return 'BLOCKED_ENV' }
    return 'FAIL'
}

Export-ModuleMember -Function Get-BackendTestStatus
