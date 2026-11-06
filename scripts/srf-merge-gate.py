"""Stable required check. Missing/skipped/cancelled constituents are never success."""
import json
import os
import sys

REQUIRED_JOBS = ("acceptance", "browser", "container", "security", "evidence")


def main():
    try:
        jobs = json.loads(os.environ["SRF_JOB_RESULTS"])
        if not isinstance(jobs, dict):
            raise ValueError()
        blocked = [name for name in REQUIRED_JOBS
                   if not isinstance(jobs.get(name), dict) or jobs[name].get("result") != "success"]
    except (KeyError, ValueError, TypeError):
        print("BLOCKED_ENV: required job results are unavailable or malformed")
        return 2
    # Print only our fixed check names. Never echo arbitrary job outputs or input.
    if blocked:
        print("FAIL: required checks: " + ", ".join(blocked))
        return 1
    print("PASS: all required engineering checks succeeded; production release remains BLOCKED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
