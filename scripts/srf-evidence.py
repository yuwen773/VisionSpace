"""Combine same-candidate outcome artifacts into the T01-T24 engineering ledger."""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--run-id", default="local")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-f0-9]{40}", args.candidate):
        print("BLOCKED_ENV: invalid candidate identity")
        return 2
    policy = json.loads(Path(__file__).with_name("srf-matrix.json").read_text(encoding="utf-8"))
    gates = {}
    runtime = {}
    for gate, required in policy["gates"].items():
        try:
            summary = json.loads((args.root / gate / "summary.json").read_text(encoding="utf-8-sig"))
            results = summary["results"]
            gates[gate] = "PASS" if (summary["head"] == args.candidate and summary.get("runId") == args.run_id and isinstance(results, dict)
                and all(results.get(key) == "PASS" for key in required)
                and all(value == "PASS" for value in results.values())) else "BLOCKED"
        except (OSError, ValueError, KeyError, TypeError):
            gates[gate] = "BLOCKED"
        try:
            runtime[gate] = json.loads((args.root / gate / "runtime.json").read_text(encoding="utf-8"))
        except (OSError, ValueError):
            runtime[gate] = {"status": "NOT_RUN"}
    suites = {}
    malformed = False
    for path in (args.root / "acceptance").rglob("TEST-*.xml"):
        try:
            cases = ET.parse(path).getroot().findall("testcase")
            name = path.stem.removeprefix("TEST-").split(".")[-1]
            passed = bool(cases) and all(all(case.find(state) is None for state in ("failure", "error", "skipped")) for case in cases)
            # Repeated artifacts must not hide a failing copy.
            suites[name] = passed and suites.get(name, True)
        except (OSError, ET.ParseError):
            malformed = True
    if malformed or not suites or not all(suites.values()):
        gates["acceptance"] = "BLOCKED"
    matrix = []
    for requirement in policy["matrix"]:
        missing = [name for name in requirement["suites"] if not suites.get(name)]
        needed = {"acceptance", *requirement.get("gates", [])}
        status = "PASS" if not missing and all(gates[gate] == "PASS" for gate in needed) else "BLOCKED"
        matrix.append({**requirement, "status": status, "missingSuites": missing})
    passed = all(value == "PASS" for value in gates.values()) and all(item["status"] == "PASS" for item in matrix)
    report = {"candidate": args.candidate, "runId": args.run_id, "engineeringAcceptance": "PASS" if passed else "BLOCKED",
              "productionAcceptance": "BLOCKED", "gates": gates, "matrix": matrix, "runtime": runtime,
              "limits": "Controlled infrastructure and recording substitutes only; #29/#48 and real-environment approvals remain required."}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f'{report["engineeringAcceptance"]}: same-candidate T01-T24 engineering evidence; production BLOCKED')
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
