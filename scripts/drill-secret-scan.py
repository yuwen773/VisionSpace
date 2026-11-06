#!/usr/bin/env python3
"""Re-check the six new #46 files with the exact scripts/secret-scan.ps1 rules."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RULES = [
    ("private-key-block", r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    ("aliyun-access-key-id", r"LTAI[0-9A-Za-z]{12,}"),
    ("aws-access-key-id", r"(AKIA|ASIA)[0-9A-Z]{16}"),
    ("dashscope-or-openai-key", r"sk-[A-Za-z0-9_.-]{24,}"),
    ("hardcoded-password", r"(?i)\b(password|passwd)\s*[:=]\s*[\"']?(?!\$\{|your_|example|placeholder|xxx|changeme|acceptance|test)[^\s\"'#]{8,}"),
    ("hardcoded-api-key", r"(?i)\b(api[-_]?key|secret[-_]?key|access[-_]?key)\s*[:=]\s*[\"']?(?!\$\{|your_|example|placeholder|xxx|changeme|acceptance|test)[^\s\"'#]{16,}"),
]
FILES = [
    "docs/progress/46-maintenance-window-drill.md",
    "scripts/drill-isolated-run.py",
    "scripts/drill-secret-scan.py",
    "sql/migrations/2026-11-05-maintenance-window-blocklist.sql",
    "sql/migrations/maintenance-window.manifest.json",
    "src/test/java/com/yuwen/visionspace/acceptance/MaintenanceWindowDrillTest.java",
    "src/test/java/com/yuwen/visionspace/acceptance/MaintenanceWindowManifestTest.java",
]


def main():
    hits = 0
    for name in FILES:
        path = ROOT / name
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            for rule, pattern in RULES:
                if re.search(pattern, line):
                    print(f"HIT {rule} {name}:{lineno}")
                    hits += 1
    print("SECRET_SCAN_HITS", hits)
    raise SystemExit(1 if hits else 0)


if __name__ == "__main__":
    main()
