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


if __name__ == "__main__":
    unittest.main()
