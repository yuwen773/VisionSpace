#!/usr/bin/env python3
"""迁移期测试覆盖对照门禁（计划 §5.8）。

迁移最容易出现的假绿是「测试被删掉/被 @Disabled，然后套件变绿」。这个门禁以
`scripts/pg-migration-test-map.json` 的 `baseRef`（迁移前最后一个 MySQL 提交）为准，
逐个类核对：

  1. 基线里存在、当前工作树里没有的测试类，必须登记在 `retired` 或 `migrated` 里；
  2. 登记项不能指向基线里根本不存在的类（防止清单被写成自我安慰）；
  3. 登记项的 `replacement` 必须是当前存在的测试类（退役要有承担者）；
  4. 新增测试类数量 >= 退役数量（防止净删除换绿）。

不读写数据库、不联网，可用 `python -I scripts/tests/test-pg-test-coverage-map.py` 单独运行。
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

TEST_FILE = re.compile(r"^(?:.*Test|.*Tests|.*IT)\.java$")
TESTS_ROOT = Path("src/test/java")
MAP_PATH = Path("scripts/pg-migration-test-map.json")


def fqcn(path: str) -> str:
    relative = path.replace("\\", "/")
    if relative.startswith("src/test/java/"):
        relative = relative[len("src/test/java/"):]
    if relative.endswith(".java"):
        relative = relative[:-5]
    return relative.replace("/", ".")


def base_test_classes(root: Path, base_ref: str) -> set[str]:
    result = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", base_ref, "--", str(TESTS_ROOT).replace("\\", "/")],
        cwd=root, capture_output=True, text=True, check=False,
    )
    if result.returncode != 0:
        raise SystemExit(f"FAIL: 无法读取基线 {base_ref} 的测试列表: {result.stderr.strip()}")
    return {
        fqcn(line.strip())
        for line in result.stdout.splitlines()
        if line.strip().endswith(".java") and TEST_FILE.match(line.strip().rsplit("/", 1)[-1])
    }


def current_test_classes(root: Path) -> set[str]:
    test_root = root / TESTS_ROOT
    if not test_root.is_dir():
        raise SystemExit(f"FAIL: 找不到测试目录 {test_root}")
    return {
        fqcn(str(path.relative_to(root)))
        for path in test_root.rglob("*.java")
        if TEST_FILE.match(path.name)
    }


def load_map(root: Path, map_path: str) -> dict:
    path = Path(map_path)
    if not path.is_absolute():
        path = root / path
    if not path.is_file():
        raise SystemExit(f"FAIL: 缺少迁移测试对照清单 {map_path}")
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    # Windows 控制台默认 GBK，中文摘要会变成乱码；统一按 UTF-8 输出。
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".", help="仓库根目录（默认当前目录）")
    parser.add_argument("--map", default=str(MAP_PATH))
    args = parser.parse_args()

    root = Path(args.root).resolve()
    mapping = load_map(root, args.map)
    base_ref = str(mapping.get("baseRef", "")).strip()
    if not base_ref:
        print("FAIL: 清单缺少 baseRef")
        return 1

    base = base_test_classes(root, base_ref)
    current = current_test_classes(root)
    missing = sorted(base - current)
    added = sorted(current - base)

    retired = mapping.get("retired", [])
    migrated = mapping.get("migrated", [])
    problems: list[str] = []

    registered: dict[str, str] = {}
    for kind, entries in (("retired", retired), ("migrated", migrated)):
        for entry in entries:
            name = str(entry.get("test", "")).strip()
            replacement = str(entry.get("replacement", "")).strip()
            if not name:
                problems.append(f"{kind} 里有缺少 test 字段的条目")
                continue
            if name in registered:
                problems.append(f"{name} 同时出现在 {registered[name]} 与 {kind}，只能登记一次")
            registered[name] = kind
            if name not in base:
                problems.append(f"{kind} 登记的 {name} 在基线 {base_ref} 里不存在")
            if not replacement:
                problems.append(f"{kind} 条目 {name} 缺少 replacement")
            elif replacement not in current:
                problems.append(f"{kind} 条目 {name} 的 replacement {replacement} 在当前工作树里不存在")

    for name in missing:
        if name not in registered:
            problems.append(
                f"{name} 在基线里存在、当前已不存在，但没有登记为 retired 或 migrated"
                f"（删除测试换绿必须显式说明）"
            )

    if len(retired) > len(added):
        problems.append(
            f"退役 {len(retired)} 个测试类，但只新增 {len(added)} 个：不允许净删除测试类"
        )

    summary = {
        "baseRef": base_ref,
        "baseTestClasses": len(base),
        "currentTestClasses": len(current),
        "added": added,
        "missing": missing,
        "retired": [entry.get("test") for entry in retired],
        "migrated": len(migrated),
        "problems": problems,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))

    if problems:
        for problem in problems:
            print(f"FAIL: {problem}", file=sys.stderr)
        return 1
    print(
        f"OK: {len(base)} 个基线测试类全部有下落；退役 {len(retired)}、"
        f"迁移登记 {len(migrated)}、新增 {len(added)}。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
