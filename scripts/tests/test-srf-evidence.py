import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POLICY = json.loads((ROOT / "scripts/srf-matrix.json").read_text())
HEAD = "1" * 40


class EvidenceTest(unittest.TestCase):
    def fixture(self, directory):
        (directory / "outcomes.json").write_text(json.dumps({gate: "PASS" for gate in POLICY["gates"]}))
        for gate, keys in POLICY["gates"].items():
            path = directory / gate
            path.mkdir()
            (path / "summary.json").write_text(json.dumps({"head": HEAD, "runId": "local", "results": {key: "PASS" for key in keys}}))
        for suite in {name for item in POLICY["matrix"] for name in item["suites"]}:
            (directory / "acceptance" / f"TEST-{suite}.xml").write_text(f'<testsuite><testcase classname="{suite}" name="publicBehavior" /></testsuite>')

    def run_report(self, directory):
        result = subprocess.run([sys.executable, "-I", "scripts/srf-evidence.py", "--root", str(directory), "--candidate", HEAD, "--run-id", "local", "--output", str(directory / "report.json")], cwd=ROOT, capture_output=True, text=True)
        report = json.loads((directory / "report.json").read_text())
        return result, report

    def test_complete_engineering_evidence_never_promotes_manual_release(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.fixture(directory)
            result, report = self.run_report(directory)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(report["productionAcceptance"], "BLOCKED")
            self.assertEqual(report["matrix"][-1]["manual"], "BLOCKED")

    def test_missing_existing_regression_blocks_its_matrix_group(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.fixture(directory)
            (directory / "acceptance/TEST-HttpBaselineTest.xml").unlink()
            result, report = self.run_report(directory)
            self.assertEqual(result.returncode, 1)
            self.assertEqual(next(item for item in report["matrix"] if item["id"] == "T10")["status"], "BLOCKED")

    def test_missing_asset_search_evidence_blocks_acceptance(self):
        for gate in ("asset-color-calibration", "asset-search-browser"):
            with self.subTest(gate=gate), tempfile.TemporaryDirectory() as folder:
                directory = Path(folder)
                self.fixture(directory)
                path = directory / "acceptance/summary.json"
                summary = json.loads(path.read_text())
                summary["results"].pop(gate, None)
                path.write_text(json.dumps(summary))
                result, report = self.run_report(directory)
                self.assertEqual(result.returncode, 1)
                self.assertEqual(report["gates"]["acceptance"], "BLOCKED")

    def test_skipped_coverage_and_other_candidate_cannot_pass(self):
        for mutation in ("skipped", "wrong-head", "wrong-run"):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as folder:
                directory = Path(folder)
                self.fixture(directory)
                if mutation == "skipped":
                    (directory / "acceptance/TEST-HttpBaselineTest.xml").write_text('<testsuite><testcase classname="HttpBaselineTest"><skipped /></testcase></testsuite>')
                elif mutation == "wrong-head":
                    (directory / "browser/summary.json").write_text(json.dumps({"head": "2" * 40, "results": {"https-browser": "PASS"}}))
                else:
                    path = directory / "browser/summary.json"
                    data = json.loads(path.read_text())
                    data["runId"] = "old-run"
                    path.write_text(json.dumps(data))
                result, _ = self.run_report(directory)
                self.assertEqual(result.returncode, 1)

    def test_command_failure_overrides_successful_report(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.fixture(directory)
            (directory / "outcomes.json").write_text(json.dumps({gate: "FAIL" if gate == "acceptance" else "PASS" for gate in POLICY["gates"]}))
            result, report = self.run_report(directory)
            self.assertEqual(result.returncode, 1)
            self.assertEqual(report["gates"]["acceptance"], "BLOCKED")


if __name__ == "__main__":
    unittest.main()
