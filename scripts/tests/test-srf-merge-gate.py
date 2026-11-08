"""Exercise the final check through its CLI, without GitHub credentials."""
import json
import os
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class MergeGateTest(unittest.TestCase):
    def run_gate(self, jobs):
        env = {**os.environ, "SRF_JOB_RESULTS": json.dumps(jobs)}
        return subprocess.run([sys.executable, "-I", "scripts/srf-merge-gate.py"],
                              cwd=ROOT, env=env, capture_output=True, text=True)

    def test_complete_candidate_passes(self):
        jobs = {name: {"result": "success"} for name in ("acceptance", "browser", "container", "security", "evidence")}
        result = self.run_gate(jobs)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("PASS", result.stdout)

    def test_every_unsuccessful_or_missing_job_blocks(self):
        for name in ("acceptance", "browser", "container", "security", "evidence"):
            for state in ("failure", "cancelled", "skipped", "pending", None):
                with self.subTest(name=name, state=state):
                    jobs = {key: {"result": "success"} for key in ("acceptance", "browser", "container", "security", "evidence")}
                    if state is None:
                        del jobs[name]
                    else:
                        jobs[name] = {"result": state, "outputs": {"secret": "do-not-echo-this"}}
                    result = self.run_gate(jobs)
                    self.assertEqual(result.returncode, 1)
                    self.assertNotIn("do-not-echo-this", result.stdout + result.stderr)

    def test_malformed_input_never_passes_or_echoes_input(self):
        result = self.run_gate(["do-not-echo-this"])
        self.assertEqual(result.returncode, 2)
        self.assertNotIn("do-not-echo-this", result.stdout + result.stderr)

    def test_additional_failed_job_never_disappears(self):
        jobs = {name: {"result": "success"} for name in ("acceptance", "browser", "container", "security", "evidence")}
        jobs["new-regression"] = {"result": "failure"}
        self.assertEqual(self.run_gate(jobs).returncode, 1)


if __name__ == "__main__":
    unittest.main()
