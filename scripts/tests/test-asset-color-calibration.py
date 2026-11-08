"""Exercise the actual calibration gate with an unusable Python Store alias."""
import os
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class CalibrationGateTest(unittest.TestCase):
    def test_store_alias_does_not_hide_working_python(self):
        script = r'''
$ErrorActionPreference = 'Stop'
$repo = $env:VS_TEST_REPO
$ast = [Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $repo 'scripts/acceptance.ps1'), [ref]$null, [ref]$null)
$function = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -eq 'Get-AcceptancePython'
}, $true)
if ($function) { Invoke-Expression $function.Extent.Text }
function Get-Command {
    param($Name, $CommandType, $ErrorAction)
    if ($Name -eq 'python3') {
        [pscustomobject]@{ Source = 'C:/Users/test/WindowsApps/python3.exe' }
    } elseif ($Name -eq 'python') {
        [pscustomobject]@{ Source = $env:VS_TEST_PYTHON }
    } else { throw "Unexpected command discovery: $Name" }
}
function python3 { throw 'The unusable Store alias was invoked' }
$gate = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.CommandAst] -and
    $node.GetCommandName() -eq 'Run-Gate' -and
    $node.CommandElements[1].Value -eq 'asset-color-calibration'
}, $true)
if (-not $gate) { throw 'Missing calibration gate' }
& $gate.CommandElements[-1].ScriptBlock.GetScriptBlock()
exit $LASTEXITCODE
'''
        env = {**os.environ, "VS_TEST_REPO": str(ROOT), "VS_TEST_PYTHON": sys.executable}
        result = subprocess.run(
            ["pwsh.exe" if os.name == "nt" else "pwsh", "-NoLogo", "-NoProfile", "-Command", script],
            cwd=ROOT, env=env, capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('"validation": {"correct": 6, "total": 6}', result.stdout)


if __name__ == "__main__":
    unittest.main()
