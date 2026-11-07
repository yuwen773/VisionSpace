"""Apply #47 governance and verify rejection on an expendable branch, never main.

No source branch is pushed or merged. Without --apply this prints the policy.
Uses the existing gh login; credentials never enter files or command arguments.
"""
import argparse
import copy
import json
import re
import subprocess
import sys
import uuid
from pathlib import Path

NAME = "SRF required engineering checks"
CHECK = "SRF merge gate"


def api(method, endpoint, body=None):
    command = ["gh", "api", "--include", "--method", method, endpoint]
    if body is not None:
        command += ["--input", "-"]
    result = subprocess.run(command, input=json.dumps(body) if body is not None else None,
                            capture_output=True, text=True, encoding="utf-8")
    header, _, content = result.stdout.replace("\r\n", "\n").partition("\n\n")
    status = re.search(r"^HTTP/\S+ (\d+)", header)
    if not status:
        raise RuntimeError("GitHub response status unavailable")
    return int(status.group(1)), json.loads(content) if content.strip() else None


def require(method, endpoint, body=None, statuses=(200,)):
    status, data = api(method, endpoint, body)
    if status not in statuses:
        raise RuntimeError(f"GitHub {method} {endpoint.split('/')[-1]} returned HTTP {status}")
    return data


def policy(app_id):
    return {"name": NAME, "target": "branch", "enforcement": "active", "bypass_actors": [],
            "conditions": {"ref_name": {"include": ["refs/heads/main"], "exclude": []}},
            "rules": [{"type": "deletion"}, {"type": "non_fast_forward"},
                      {"type": "pull_request", "parameters": {"required_approving_review_count": 0,
                       "dismiss_stale_reviews_on_push": True, "require_code_owner_review": False,
                       "require_last_push_approval": False, "required_review_thread_resolution": True}},
                      {"type": "required_status_checks", "parameters": {
                       "strict_required_status_checks_policy": True,
                       "required_status_checks": [{"context": CHECK, "integration_id": app_id}]}}]}


def snapshot(repo):
    data = require("GET", f"repos/{repo}")
    status, _ = api("GET", f"repos/{repo}/vulnerability-alerts")
    return {"defaultBranch": data["default_branch"], "security": data.get("security_and_analysis", {}),
            "dependabotAlerts": status == 204,
            "rulesets": require("GET", f"repos/{repo}/rulesets"), "admin": data.get("permissions", {}).get("admin", False)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default="yuwen773/VisionSpace")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--output", type=Path, default=Path(".srf-runs/governance.json"))
    args = parser.parse_args()
    if not args.apply:
        print(json.dumps(policy(None), indent=2))
        return 0
    evidence = {"status": "BLOCKED", "repository": args.repo, "productionAcceptance": "NOT_RUN"}
    probe = "srf47-governance-probe-" + uuid.uuid4().hex[:12]
    rule_id = None
    desired = None
    created_probe = False
    try:
        before = snapshot(args.repo)
        evidence["before"] = before
        if not before["admin"] or before["defaultBranch"] != "main":
            raise RuntimeError("Expected admin access and main default branch")
        base = require("GET", f"repos/{args.repo}/git/ref/heads/main")["object"]["sha"]
        checks = require("GET", f"repos/{args.repo}/commits/{base}/check-runs")["check_runs"]
        app_ids = {check["app"]["id"] for check in checks if check["app"]["slug"] == "github-actions"}
        if len(app_ids) != 1:
            raise RuntimeError("Cannot establish the trusted Actions check source")
        desired = policy(app_ids.pop())
        own = [item for item in before["rulesets"] if item["name"] == NAME]
        if len(own) > 1:
            raise RuntimeError("Multiple owned Rulesets; refusing ambiguous update")
        if own:
            rule_id = own[0]["id"]
            require("PUT", f"repos/{args.repo}/rulesets/{rule_id}", desired)
        else:
            rule_id = require("POST", f"repos/{args.repo}/rulesets", desired, (201,))["id"]
        evidence["rulesetId"] = rule_id
        # Security coverage complements the candidate content scanner and Dependabot config.
        require("PUT", f"repos/{args.repo}/vulnerability-alerts", statuses=(204,))
        require("PUT", f"repos/{args.repo}/automated-security-fixes", statuses=(204,))
        require("PATCH", f"repos/{args.repo}", {"security_and_analysis": {
            "secret_scanning": {"status": "enabled"},
            "secret_scanning_push_protection": {"status": "enabled"}}})
        # Set up a disposable ref before extending the same rule to it.
        require("POST", f"repos/{args.repo}/git/refs", {"ref": f"refs/heads/{probe}", "sha": base}, (201,))
        created_probe = True
        temporary = copy.deepcopy(desired)
        temporary["conditions"]["ref_name"]["include"].append(f"refs/heads/{probe}")
        require("PUT", f"repos/{args.repo}/rulesets/{rule_id}", temporary)
        tree = require("GET", f"repos/{args.repo}/git/commits/{base}")["tree"]["sha"]
        commit = require("POST", f"repos/{args.repo}/git/commits", {
            "message": "test(governance): controlled SRF missing-check probe", "tree": tree, "parents": [base]}, (201,))
        status, rejection = api("PATCH", f"repos/{args.repo}/git/refs/heads/{probe}", {"sha": commit["sha"], "force": False})
        check_named = CHECK in json.dumps(rejection)
        evidence["controlledProbe"] = {"httpStatus": status, "requiredCheckNamed": check_named,
                                       "sameTreeAsMain": True, "mainUpdateAttempted": False}
        if status not in (403, 422) or not check_named:
            raise RuntimeError("Probe did not prove rejection by the required check")
        evidence["status"] = "PASS"
    except (RuntimeError, OSError, ValueError, KeyError, TypeError) as error:
        # Only our fixed diagnostic labels are retained, never raw HTTP/token contents.
        evidence["error"] = str(error) if isinstance(error, RuntimeError) else "Governance evidence incomplete"
    finally:
        try:
            if rule_id is not None and desired is not None:
                require("PUT", f"repos/{args.repo}/rulesets/{rule_id}", desired)
                effective = require("GET", f"repos/{args.repo}/rules/branches/main")
                evidence["effectiveMainRules"] = effective
                active_check = any(rule["type"] == "required_status_checks" and
                    rule["parameters"].get("strict_required_status_checks_policy") and
                    any(check["context"] == CHECK and check.get("integration_id") == desired["rules"][-1]["parameters"]["required_status_checks"][0]["integration_id"] for check in rule["parameters"]["required_status_checks"])
                    for rule in effective)
                if not active_check:
                    evidence["status"] = "BLOCKED"
            if created_probe:
                require("DELETE", f"repos/{args.repo}/git/refs/heads/{probe}", statuses=(204,))
            evidence["probeCleanup"] = "PASS"
            evidence["after"] = snapshot(args.repo)
            security = evidence["after"]["security"]
            for feature in ("secret_scanning", "secret_scanning_push_protection", "dependabot_security_updates"):
                if security.get(feature, {}).get("status") != "enabled":
                    evidence["status"] = "BLOCKED"
            if not evidence["after"]["dependabotAlerts"]:
                evidence["status"] = "BLOCKED"
            evidence["genericPatternCoverage"] = {"hosted": security.get("secret_scanning_non_provider_patterns", {}).get("status", "unavailable"),
                                                  "candidateScanner": "scripts/secret-scan.ps1"}
        except (RuntimeError, OSError, ValueError, KeyError, TypeError):
            evidence["status"] = "BLOCKED"
            evidence["probeCleanup"] = "BLOCKED"
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
    print(f'{evidence["status"]}: SRF governance and controlled merge rejection; no main update attempted')
    return 0 if evidence["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
