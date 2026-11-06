#!/usr/bin/env python3
"""Guardrail: secret-scan.ps1 must not silently skip file types.

The scan once enumerated -Include extensions, so every new file type was invisible.
It now excludes known binary types instead; this check fails if a whitelist returns.
Also bans tautological Jackson assertions in Java tests and keeps M1-M8 in sync.

Run from the repo root:  python -I scripts/tests/check-repo-hygiene.py
Exits 1 on any finding, 2 when the environment cannot be inspected.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCAN = ROOT / "scripts" / "secret-scan.ps1"
PROGRESS = ROOT / "docs" / "progress" / "46-maintenance-window-drill.md"
MANIFEST = ROOT / "sql" / "migrations" / "maintenance-window.manifest.json"
JAVA_TESTS = ROOT / "src" / "test" / "java"

JAVA_TESTS = ROOT / "src" / "test" / "java"


def excluded_suffixes(scan_text):
    """Read -Exclude from the scanner so this guard cannot drift from it."""
    match = re.search(r"\[string\[\]\]\$Exclude\s*=\s*@\(([^)]*)\)", scan_text, re.S)
    if not match:
        raise RuntimeError("cannot parse -Exclude from secret-scan.ps1")
    return {s.strip().strip("'\"").lower().lstrip("*")
            for s in match.group(1).split(",")}


def findings():
    # A whitelist of extensions silently skips every newly added file type. Require
    # the scan to exclude binaries instead, and assert a minimum set is covered.
    scan_text = SCAN.read_text(encoding="utf-8")
    if "[string[]]$Include" in scan_text:
        yield "secret-scan.ps1: -Include whitelist would silently skip new file types"
    declared = excluded_suffixes(scan_text)
    for suffix in (".png", ".pdf", ".jar", ".zip", ".woff", ".mp4", ".xlsx", ".pyc"):
        if suffix not in declared:
            yield f"secret-scan.ps1: binary type {suffix} is not excluded"

    # Tautology: JsonNode.path() returns MissingNode, never null. Restrict to .path(
    # so Map.get()/List.get() assertions (which can genuinely be null) stay legal.
    pattern = re.compile(r"assertNotNull\s*\(\s*[^;]*?\.path\s*\(", re.S)
    for java in JAVA_TESTS.rglob("*.java"):
        for lineno, line in enumerate(java.read_text(encoding="utf-8").splitlines(), 1):
            if pattern.search(line):
                yield (f"{java.relative_to(ROOT)}:{lineno}: assertNotNull on JsonNode.path()/get() "
                       f"is always true (MissingNode is never null)")

    # M1–M8 are mirrored between the manifest and the progress doc; keep the ids in sync.
    if PROGRESS.is_file() and MANIFEST.is_file():
        manifest_ids = {g["id"] for g in json.loads(
            MANIFEST.read_text(encoding="utf-8"))["manualApprovalPoints"]}
        doc_text = PROGRESS.read_text(encoding="utf-8")
        doc_ids = set(re.findall(r"^\|\s*(M\d+)\s*\|", doc_text, re.M))
        if manifest_ids != doc_ids:
            yield (f"approval-point drift: manifest={sorted(manifest_ids)} "
                   f"doc={sorted(doc_ids)}")


def main():
    try:
        items = list(findings())
    except Exception as exc:
        print(f"BLOCKED_ENV: {exc}")
        return 2
    if items:
        print(f"REPO HYGIENE: {len(items)} finding(s):")
        for item in items:
            print(f"  {item}")
        return 1
    print("PASS: extension coverage, Jackson assertions and approval-point ids are in sync")
    return 0


if __name__ == "__main__":
    sys.exit(main())
