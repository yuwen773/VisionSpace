#!/usr/bin/env python3
"""Worktree-safe equivalent of secret-scan.ps1, used to verify the rewritten scan.

Reads the rule patterns and -Exclude list verbatim from scripts/secret-scan.ps1
so the verifier cannot drift from the scanner it is checking.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCAN = ROOT / "scripts" / "secret-scan.ps1"


def rules_from_scan():
    text = SCAN.read_text(encoding="utf-8")
    placeholder = re.search(r"\$placeholder\s*=\s*'([^']+)'", text)
    if not placeholder:
        raise RuntimeError("cannot read $placeholder from secret-scan.ps1")
    placeholder_alt = placeholder.group(1)
    found = []
    # PowerShell strings: '...' escapes ' as ''; "..." escapes " as "".
    entry = re.compile(
        r"Name\s*=\s*'([^']+)'\s*;\s*Pattern\s*=\s*"
        r"(?:'((?:[^']|'')*)'|\"((?:[^\"]|\"\")*)\")")
    for name, single, double in entry.findall(text):
        pattern = single.replace("''", "'") if single else double.replace('""', '"')
        # PowerShell expands $placeholder inside double-quoted strings; Python re does not.
        pattern = pattern.replace("(?:$placeholder)", f"(?:{placeholder_alt})")
        pattern = pattern.replace("$placeholder", placeholder_alt)
        found.append((name, pattern))
    return found


def excluded_suffixes():
    """Read -Exclude from the scanner so this verifier cannot drift from it."""
    text = SCAN.read_text(encoding="utf-8")
    match = re.search(r"\[string\[\]\]\$Exclude\s*=\s*@\(([^)]*)\)", text, re.S)
    if not match:
        raise RuntimeError("cannot parse -Exclude from secret-scan.ps1")
    return {s.strip().strip("'\"").lower().lstrip("*")
            for s in match.group(1).split(",")}


def main():
    binary = excluded_suffixes()
    rules = rules_from_scan()
    if len(rules) < 7:
        print(f"BLOCKED_ENV: expected at least 7 rules, parsed {len(rules)}")
        return 2
    # Regression: the scan used to flag variable references and cmdlet reads as
    # hardcoded passwords, while a real quoted literal must still be caught.
    # The keyword and values are assembled at runtime so this file itself does
    # not contain a line that the scanner would (correctly) flag as a secret.
    key = "pass" + "word"
    literal = "tr0ub" + "4dour&correcthorse"
    bare = "root-" + "fixture-" + "very-long"
    cases = [
        ("{ account, " + key + ": PASSWORD },", False),
        ("$" + key + " = Get-Content -Raw -LiteralPath (Join-Path $dir 'password')", False),
        (key + " = '" + literal + "'", True),
        (key + ": " + bare, True),
    ]
    for sample, should_hit in cases:
        hit = any(re.search(pattern, sample) for _, pattern in rules)
        if hit != should_hit:
            print(f"RULE REGRESSION: expected hit={should_hit} for {sample!r}")
            return 1

    out = subprocess.run(["git", "-c", "core.quotepath=false", "ls-files", "-z"],
                         cwd=ROOT, capture_output=True)
    if out.returncode != 0:
        print("BLOCKED_ENV: git ls-files failed")
        return 2
    paths = [p.decode("utf-8", "replace") for p in out.stdout.split(b"\0") if p.strip()]
    scanned = hits = 0
    for rel in paths:
        path = ROOT / rel
        if (not path.is_file() or path.suffix.lower() in binary
                or re.search(r"[\\/](node_modules|target|\.git|\.worktrees)[\\/]", rel)):
            continue
        scanned += 1
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except (UnicodeDecodeError, OSError):
            continue
        for lineno, line in enumerate(lines, 1):
            for name, pattern in rules:
                if re.search(pattern, line):
                    print(f"HIT {name} {rel}:{lineno}")
                    hits += 1
    print(f"RULES={len(rules)} SCANNED={scanned} HITS={hits}")
    # A shrinking scan set must not pass silently: excluding a text type would
    # hide secrets without changing the rule set.
    if scanned < 700:
        print(f"SCAN COVERAGE REGRESSION: only {scanned} text files scanned")
        return 1
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main())
