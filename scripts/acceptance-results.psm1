function Get-BackendTestStatus {
    param([int]$ExitCode, [string]$ReportDirectory)
    # A log substring cannot distinguish mixed test and infrastructure failures.
    $hasIsolationError = $false
    $executed = 0
    foreach ($file in Get-ChildItem -LiteralPath $ReportDirectory -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue) {
        try { [xml]$report = Get-Content -Raw -LiteralPath $file.FullName } catch { return 'FAIL' }
        foreach ($case in $report.testsuite.testcase) {
            $executed++
            if ($case.SelectSingleNode('skipped')) { return 'FAIL' }
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
    if ($ExitCode -eq 0 -and $executed -gt 0) { return 'PASS' }
    return 'FAIL'
}

Export-ModuleMember -Function Get-BackendTestStatus
