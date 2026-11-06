#!/usr/bin/env python3
"""Guardrail for /code-review: the review target must equal the working tree.

A review pinned to `git diff --cached` while files are unstaged reviews stale
code. Run before pinning a fixed point:

    python -I scripts/tests/check-review-target.py

Exits 0 when the tree is clean (review HEAD), 1 when staged or unstaged changes
would make a diff-based review stale, 2 when git cannot be inspected.
"""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def status():
    out = subprocess.run(["git", "status", "--porcelain=v1", "-uall"],
                         cwd=ROOT, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(f"git status failed: {out.stderr.strip()}")
    return [line for line in out.stdout.splitlines() if line.strip()]


def main():
    try:
        lines = status()
    except Exception as exc:
        print(f"BLOCKED_ENV: {exc}")
        return 2
    staged = [line for line in lines if line[0] not in (" ", "?")]
    unstaged = [line for line in lines if len(line) > 1 and line[1] not in (" ", "?")]
    untracked = [line for line in lines if line.startswith("??")]
    if staged or unstaged or untracked:
        print("REVIEW TARGET STALE: commit first, then review HEAD")
        for label, group in (("staged", staged), ("unstaged", unstaged),
                             ("untracked", untracked)):
            for line in group:
                print(f"  {label}: {line[3:]}")
        return 1
    print("PASS: working tree is clean; review HEAD")
    return 0


if __name__ == "__main__":
    sys.exit(main())
