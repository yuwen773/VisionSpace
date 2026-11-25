import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class PublishEvidenceTest(unittest.TestCase):
    def test_only_safe_evidence_leaves_raw_directory(self):
        with tempfile.TemporaryDirectory(prefix="vs47-publish-") as directory:
            source, output = Path(directory) / "raw", Path(directory) / "public"
            source.mkdir()
            secret = "private-value-must-never-leave-raw"
            (source / "summary.json").write_text(json.dumps({"head": "a" * 40, "results": {"backend": "PASS"}, "password": secret}))
            (source / "TEST-Example.xml").write_text(f'<testsuite tests="1"><testcase name="rejectUnauthorized" classname="Example"><failure message="{secret}">{secret}</failure><system-out>{secret}</system-out></testcase></testsuite>')
            for name in (".env.secrets", "bootstrap.sql", "entry.key", "backend.log"):
                (source / name).write_text(secret)
            result = subprocess.run([sys.executable, "-I", "scripts/publish-evidence.py", str(source), str(output)], cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            files = list(output.rglob("*"))
            self.assertTrue((output / "summary.json").is_file())
            self.assertTrue((output / "TEST-Example.xml").is_file())
            for path in files:
                if path.is_file():
                    self.assertNotIn(secret, path.read_text())
            self.assertNotIn(secret, result.stdout + result.stderr)

    def test_structured_failure_reason_travels_but_value_shaped_ones_do_not(self):
        with tempfile.TemporaryDirectory(prefix="vs47-failures-") as directory:
            source, output = Path(directory) / "raw", Path(directory) / "public"
            source.mkdir()
            (source / "summary.json").write_text(json.dumps({
                "head": "a" * 40,
                "results": {"frontend-build": "FAIL"},
                "gateFailures": {
                    "frontend-build": "scripts/tests/test-postgres-persistence.ps1 exited 1",
                    "backend": "credential=leaked-value",
                },
            }))
            result = subprocess.run([sys.executable, "-I", "scripts/publish-evidence.py", str(source), str(output)], cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            summary = json.loads((output / "summary.json").read_text())
            self.assertEqual(summary["gateFailures"], {"frontend-build": "scripts/tests/test-postgres-persistence.ps1 exited 1"})
            self.assertNotIn("leaked-value", (output / "summary.json").read_text())

    def test_test_coverage_counts_travel_but_class_names_do_not(self):
        with tempfile.TemporaryDirectory(prefix="vs47-coverage-") as directory:
            source, output = Path(directory) / "raw", Path(directory) / "public"
            source.mkdir()
            (source / "summary.json").write_text(json.dumps({
                "head": "a" * 40,
                "results": {"test-coverage-map": "PASS"},
                "testCoverage": {
                    "baseRef": "7545610",
                    "baseTestClasses": 132,
                    "currentTestClasses": 140,
                    "added": 9,
                    "retired": 1,
                    "migrated": 51,
                    "problems": 0,
                    "addedClasses": ["com.example.internal.SecretPathTest"],
                },
            }))
            result = subprocess.run([sys.executable, "-I", "scripts/publish-evidence.py", str(source), str(output)], cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            text = (output / "summary.json").read_text()
            coverage = json.loads(text)["testCoverage"]
            self.assertEqual(coverage["migrated"], 51)
            self.assertEqual(coverage["baseRef"], "7545610")
            self.assertNotIn("addedClasses", coverage)
            self.assertNotIn("SecretPathTest", text)


if __name__ == "__main__":
    unittest.main()
