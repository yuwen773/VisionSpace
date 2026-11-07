"""A dirty candidate must be rejected before any gate command is executed."""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class CandidateTest(unittest.TestCase):
    def test_dirty_source_cannot_be_labelled_as_committed_head(self):
        pwsh = shutil.which("pwsh.exe") or shutil.which("pwsh")
        self.assertIsNotNone(pwsh, "PowerShell 7 is required")
        with tempfile.TemporaryDirectory(prefix="vs47-candidate-") as folder:
            repo = Path(folder)
            (repo / "scripts").mkdir()
            shutil.copyfile(ROOT / "scripts/srf-verification.ps1", repo / "scripts/srf-verification.ps1")
            (repo / "scripts/srf-matrix.json").write_text(json.dumps({"entries": {"acceptance": {"script": "marker.ps1", "report": "marker"}}}))
            (repo / "marker.ps1").write_text("Set-Content -LiteralPath (Join-Path $PSScriptRoot 'marker-called') -Value yes\nexit 0\n")
            subprocess.run(["git", "init", "-q", str(repo)], check=True, capture_output=True)
            result = subprocess.run([pwsh, "-NoLogo", "-NoProfile", "-File", str(repo / "scripts/srf-verification.ps1")],
                                    cwd=repo, capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertFalse((repo / "marker-called").exists(), "an uncommitted candidate must never execute a gate")


if __name__ == "__main__":
    unittest.main()
