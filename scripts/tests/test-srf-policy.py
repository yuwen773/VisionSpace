"""Fail when static Actions wiring or PowerShell discovery drifts from the manifest."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POLICY = json.loads((ROOT / "scripts/srf-matrix.json").read_text(encoding="utf-8"))


class PolicyTest(unittest.TestCase):
    def test_workflow_jobs_and_final_needs_follow_the_manifest(self):
        workflow = (ROOT / ".github/workflows/srf.yml").read_text(encoding="utf-8")
        jobs = workflow.split("\njobs:\n", 1)[1]
        expected = {*POLICY["gates"], "evidence", "merge-gate"}
        self.assertEqual(set(re.findall(r"^  ([a-zA-Z0-9_-]+):", jobs, re.M)), expected)
        final = jobs.split("  merge-gate:\n", 1)[1]
        needs = re.search(r"^    needs: \[([^]]+)\]", final, re.M).group(1)
        self.assertEqual({name.strip() for name in needs.split(",")}, expected - {"merge-gate"})
        self.assertEqual(set(POLICY["entries"]), set(POLICY["gates"]))
        for entry in POLICY["entries"].values():
            self.assertTrue((ROOT / entry["script"]).is_file())

    def test_every_powershell_regression_has_a_manifest_or_workflow_entry(self):
        workflows = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / ".github/workflows").glob("*.yml"))
        wired = {entry["path"] for entry in POLICY["runnerTests"]}
        wired.update(re.findall(r"scripts/tests/test-[a-zA-Z0-9_-]+\.ps1", workflows))
        discovered = {path.relative_to(ROOT).as_posix() for path in (ROOT / "scripts/tests").glob("test-*.ps1")}
        self.assertEqual(discovered, wired)

    def test_artifact_selection_cannot_mix_workflow_attempts(self):
        workflows = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / ".github/workflows").glob("*.yml"))
        names = re.findall(r"^\s+name: (srf-.+)$", workflows, re.M)
        self.assertTrue(names)
        self.assertTrue(all("${{ github.run_attempt }}" in name for name in names))
        self.assertIn("pattern: srf-*-${{ github.run_attempt }}", workflows)


if __name__ == "__main__":
    unittest.main()
