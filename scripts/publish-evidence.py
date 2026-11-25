"""Export an allowlisted evidence projection; raw logs/configuration stay local.

Diagnostics can contain Cookie values, SQL parameters and full request bodies.
They are deliberately not CI artifacts. JUnit keeps test identity and outcomes,
not failure messages or system-out. This is a projection, not heuristic redaction.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

STATES = {"PASS", "FAIL", "BLOCKED", "BLOCKED_ENV", "NOT_RUN"}
# 失败原因只接受结构化短句：字母数字、空格与少量标点。
# 不允许 '=' 出现在里面——任何“键=值”形态（口令、令牌、连接串）都会被整体丢弃，
# 这比逐个字段做脱敏更可靠。
FAILURE_REASON = re.compile(r"[A-Za-z0-9 _.:/()\-]{1,200}")


def project_failures(value):
    failures = value.get("gateFailures")
    if not isinstance(failures, dict):
        return {}
    out = {}
    for name, reason in failures.items():
        if not re.fullmatch(r"[a-zA-Z0-9_-]{1,80}", str(name)):
            continue
        if not isinstance(reason, str) or not FAILURE_REASON.fullmatch(reason):
            continue
        out[name] = reason
    return out


def project_test_coverage(value):
    """覆盖对照只投影计数（计划 §5.8）：类名清单不进证据，只留得出「有没有净删除」的几个数。"""
    if not isinstance(value, dict):
        return {}
    out = {}
    for key in ("baseTestClasses", "currentTestClasses", "added", "retired", "migrated", "problems"):
        number = value.get(key)
        if isinstance(number, int) and not isinstance(number, bool) and 0 <= number <= 100_000:
            out[key] = number
    if re.fullmatch(r"[0-9a-f]{7,40}", str(value.get("baseRef", ""))):
        out["baseRef"] = value["baseRef"]
    return out if "migrated" in out else {}


def project(value):
    if isinstance(value, list):
        return [project(item) for item in value]
    if not isinstance(value, dict):
        return {}
    out = {}
    for key in ("head", "candidate"):
        if re.fullmatch(r"[a-f0-9]{40}", str(value.get(key, ""))):
            out[key] = value[key]
    if re.fullmatch(r"[A-Za-z0-9_-]{1,80}", str(value.get("runId", ""))):
        out["runId"] = value["runId"]
    if value.get("infrastructure") in {"docker", "native-explicit"}:
        out["infrastructure"] = value["infrastructure"]
    for key in ("status", "productionAcceptance"):
        if value.get(key) in STATES:
            out[key] = value[key]
    if isinstance(value.get("ok"), bool):
        out["ok"] = value["ok"]
    for key in ("results", "scenarios"):
        content = value.get(key)
        if isinstance(content, list):
            out[key] = [project(item) for item in content]
        elif isinstance(content, dict):
            out[key] = {name: status for name, status in content.items()
                        if re.fullmatch(r"[a-zA-Z0-9_-]{1,80}", name) and isinstance(status, str) and status in STATES}
    projected_failures = project_failures(value)
    if projected_failures:
        out["gateFailures"] = projected_failures
    projected_coverage = project_test_coverage(value.get("testCoverage"))
    if projected_coverage:
        out["testCoverage"] = projected_coverage
    return out


def advisories(value):
    safe = []
    for item in value if isinstance(value, list) else []:
        if not isinstance(item, dict) or not re.fullmatch(r"(?:CVE-\d{4}-\d+|GHSA-[A-Za-z0-9-]+)", str(item.get("id", ""))):
            continue
        safe.append({key: str(item.get(key, "")) for key in ("id", "package", "installed", "fixed", "severity")
                     if re.fullmatch(r"[A-Za-z0-9@_./:,+ -]{0,200}", str(item.get(key, "")))})
    return safe


def publish(source, destination):
    if not source.is_dir():
        raise ValueError("missing source")
    if destination.resolve().is_relative_to(source.resolve()):
        raise ValueError("destination must be outside source")
    for path in source.rglob("*"):
        if not path.is_file() or path.is_symlink():
            continue
        content = None
        if path.name in {"summary.json", "results.json", "browser-results.json", "protocol-results.json"}:
            content = json.dumps(project(json.loads(path.read_text(encoding="utf-8-sig"))), indent=2)
        elif "diagnostics" in path.parts and path.suffix == ".log":
            # 失败现场的完整 e2e 日志：文本、由验收脚本自产、不含凭据（脚本已脱敏配置）。
            # 没有它，CI 证据只剩状态码，断言失败无法定位（历史教训：#106/#108 连环排查）。
            content = path.read_text(encoding="utf-8", errors="replace")
        elif path.name == "advisories.json":
            content = json.dumps(advisories(json.loads(path.read_text(encoding="utf-8-sig"))), indent=2)
        elif path.name.startswith("TEST-") and path.suffix == ".xml":
            original = ET.parse(path).getroot()
            clean = ET.Element("testsuite", {key: original.attrib[key] for key in ("name", "tests", "failures", "errors", "skipped", "time") if key in original.attrib})
            for case in original.findall("testcase"):
                node = ET.SubElement(clean, "testcase", {key: case.attrib[key] for key in ("name", "classname", "time") if key in case.attrib})
                for state in ("failure", "error", "skipped"):
                    if case.find(state) is not None:
                        ET.SubElement(node, state)
            content = ET.tostring(clean, encoding="unicode")
        if content is not None:
            target = destination / path.relative_to(source)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")


def main():
    try:
        if len(sys.argv) != 3:
            raise ValueError("arguments")
        publish(Path(sys.argv[1]), Path(sys.argv[2]))
    except (OSError, ValueError, ET.ParseError):
        print("BLOCKED_ENV: evidence could not be projected safely")
        return 2
    print("PASS: exported outcome evidence; raw diagnostics and configuration withheld")
    return 0


if __name__ == "__main__":
    sys.exit(main())
