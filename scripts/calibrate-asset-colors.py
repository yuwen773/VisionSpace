"""Reproduce #54's synthetic color calibration, keeping label approval explicit.

Only the calibration split chooses the threshold. Validation labels are never
used to select it. Outputs are local artifacts, not production configuration.
"""
import csv
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LABELS = ROOT / "docs/fixtures/asset-color-labels.csv"
RULE = ROOT / "src/test/resources/asset-color-test-rule.json"
OUTPUT = ROOT / "target/asset-color-calibration.json"
ANCHORS = ("#0000FF", "#800080")


def similarity(first, second):
    a = [int(first[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(second[i:i + 2], 16) for i in (1, 3, 5)]
    return 1 - math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b))) / math.sqrt(3 * 255 ** 2)


def main():
    with LABELS.open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))
    assert all(row["split"] in ("calibration", "validation") for row in rows)
    assert all(row["label"] in ("BLUE_OR_PURPLE", "OTHER") for row in rows)
    assert len({row["color"] for row in rows}) == len(rows), "split overlap"
    for row in rows:
        row["score"] = max(similarity(row["color"], anchor) for anchor in ANCHORS)
    train = [row for row in rows if row["split"] == "calibration"]
    threshold = min(row["score"] for row in train if row["label"] == "BLUE_OR_PURPLE")
    assert max(row["score"] for row in train if row["label"] == "OTHER") < threshold
    rule = json.loads(RULE.read_text(encoding="utf-8"))
    assert (rule["blueAnchor"], rule["purpleAnchor"]) == ANCHORS
    assert rule["threshold"] == threshold, "frozen rule differs from calibration"
    for row in rows:
        row["actual"] = "BLUE_OR_PURPLE" if row["score"] >= threshold else "OTHER"
        row["correct"] = row["actual"] == row["label"]
    validation = [row for row in rows if row["split"] == "validation"]
    report = {
        "ruleVersion": rule["version"], "formula": "1 - distanceRGB / sqrt(3 * 255^2)",
        "thresholdSelection": "highest threshold retaining all calibration positives",
        "threshold": threshold, "sort": "max similarity descending; numeric pictureId ascending",
        "labelStatus": sorted({row["labelStatus"] for row in rows}),
        "humanAcceptance": "NOT_RUN" if any(row["labelStatus"] != "CONFIRMED" for row in rows) else "CONFIRMED",
        "realAssetAcceptance": "NOT_RUN",
        "validation": {"correct": sum(row["correct"] for row in validation), "total": len(validation)},
        "labelsSha256": hashlib.sha256(LABELS.read_bytes()).hexdigest(),
        "ruleSha256": hashlib.sha256(RULE.read_bytes()).hexdigest(), "rows": rows,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    assert all(row["correct"] for row in rows), "synthetic validation failed"
    print(json.dumps({key: report[key] for key in ("threshold", "labelStatus", "humanAcceptance", "validation")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
