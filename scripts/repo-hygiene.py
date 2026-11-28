#!/usr/bin/env python3
"""仓库卫生报告：删分支 / 工作树 / stash 之前先跑它。

默认只读，把「能安全删的、必须先打 tag 的、受保护的、有未提交内容的」分清楚；
加 --export 才把 stash diff、工作树未提交改动导出成 patch/副本，供删除后恢复。

    python -I scripts/repo-hygiene.py
    python -I scripts/repo-hygiene.py --export
    python -I scripts/repo-hygiene.py --base origin/main --protect feature/agent-dev

设计约束（见 docs/agents/repo-hygiene.md）：
  * 没有 upstream、也没合进 base 的分支 = NEEDS_TAG，绝不直接删；
  * 有未提交内容的工作树 = EXPORT，先导出再删；
  * 受保护分支（默认 feature/agent-dev）永远只报告，不给删除建议。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

PROTECTED_DEFAULT = ("feature/agent-dev",)
UNTRACKED_COPY_LIMIT = 5 * 1024 * 1024  # 单个未跟踪文件超过 5MB 只记录不拷贝


def git(*args: str, cwd: Path | None = None, check: bool = True) -> str:
    proc = subprocess.run(
        ["git", *args],
        cwd=str(cwd) if cwd else None,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and proc.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} 失败：\n{proc.stderr.strip()}")
    return proc.stdout.rstrip("\n")


def git_ok(*args: str, cwd: Path | None = None) -> bool:
    return subprocess.run(
        ["git", *args], cwd=str(cwd) if cwd else None, capture_output=True
    ).returncode == 0


def repo_root() -> Path:
    """主工作区根目录（不是当前 worktree 的根目录）。"""
    common = git("rev-parse", "--path-format=absolute", "--git-common-dir")
    return Path(common).parent


def pick_base(requested: str | None) -> str:
    if requested:
        return requested
    for candidate in ("origin/main", "main"):
        if git_ok("rev-parse", "--verify", "--quiet", candidate):
            return candidate
    raise SystemExit("找不到 origin/main 或 main，请用 --base 指定基线")


def is_merged(ref: str, base: str) -> bool:
    return git_ok("merge-base", "--is-ancestor", ref, base)


def ahead_count(ref: str, base: str) -> int:
    out = git("rev-list", "--count", f"{base}..{ref}", check=False)
    return int(out) if out.isdigit() else 0


def parse_worktrees() -> list[dict]:
    blocks = git("worktree", "list", "--porcelain").split("\n\n")
    worktrees = []
    for block in blocks:
        if not block.strip():
            continue
        item = {"path": None, "head": None, "branch": None, "detached": False}
        for line in block.splitlines():
            if line.startswith("worktree "):
                item["path"] = line[len("worktree "):]
            elif line.startswith("HEAD "):
                item["head"] = line[len("HEAD "):][:12]
            elif line.startswith("branch "):
                item["branch"] = line[len("branch refs/heads/"):]
            elif line == "detached":
                item["detached"] = True
        if item["path"]:
            worktrees.append(item)
    return worktrees


def worktree_state(path: Path) -> dict:
    out = git("status", "--porcelain", cwd=path, check=False)
    lines = [ln for ln in out.splitlines() if ln.strip()]
    untracked = [ln[3:] for ln in lines if ln.startswith("?? ")]
    modified = [ln for ln in lines if not ln.startswith("?? ")]
    return {"modified": modified, "untracked": untracked}


def branch_rows(base: str, protected: set[str]) -> list[dict]:
    fmt = "%(refname:short)\t%(upstream:short)\t%(objectname:short)"
    rows = []
    for line in git("for-each-ref", "refs/heads", f"--format={fmt}").splitlines():
        name, upstream, tip = (line.split("\t") + ["", ""])[:3]
        merged = is_merged(name, base)
        own_upstream = bool(upstream) and upstream == f"origin/{name}"
        if name in protected:
            verdict = "PROTECTED"
        elif merged:
            verdict = "SAFE"
        elif own_upstream:
            verdict = "PUSHED_UNMERGED"
        else:
            verdict = "NEEDS_TAG"
        rows.append(
            {
                "branch": name,
                "upstream": upstream,
                "tip": tip,
                "merged": merged,
                "ahead": ahead_count(name, base),
                "verdict": verdict,
            }
        )
    return rows


def remote_rows(base: str) -> list[dict]:
    out = git("for-each-ref", "refs/remotes/origin", "--format=%(refname:short)")
    rows = []
    for ref in out.splitlines():
        short = ref[len("origin/"):]
        if short == "HEAD":
            continue
        rows.append({"branch": short, "merged": is_merged(ref, base)})
    return rows


def stash_rows() -> list[dict]:
    rows = []
    out = git("stash", "list", "--format=%gd\t%gs\t%ci")
    for line in out.splitlines():
        ref, subject, date = (line.split("\t") + ["", "", ""])[:3]
        patch = git("stash", "show", "-p", ref, check=False)
        digest = hashlib.sha256(patch.encode("utf-8")).hexdigest()[:12]
        rows.append({"ref": ref, "subject": subject, "date": date, "digest": digest,
                     "patch": patch})
    return rows


def location_label(path: str, root: Path) -> str:
    p = Path(path).resolve()
    if p == root.resolve():
        return "主工作区"
    try:
        p.relative_to(root.resolve())
        return "仓库内"
    except ValueError:
        return "仓库外"


def print_report(base: str, rows: list[dict], worktrees: list[dict],
                 remotes: list[dict], stashes: list[dict], root: Path) -> None:
    print(f"仓库卫生报告 —— 基线 {base}，主工作区 {root}\n")

    print("本地分支")
    for r in rows:
        flags = []
        if r["ahead"]:
            flags.append(f"{r['ahead']} ahead")
        if r["upstream"]:
            flags.append(f"upstream {r['upstream']}")
        print(f"  {r['verdict']:<15} {r['branch']:<42} {r['tip']}  {' · '.join(flags)}")
    print()

    print("工作树")
    for w in worktrees:
        state = worktree_state(Path(w["path"]))
        dirty = len(state["modified"])
        untracked = len(state["untracked"])
        marker = "EXPORT" if dirty or untracked else "clean"
        branch = w["branch"] or ("detached" if w["detached"] else "?")
        where = location_label(w["path"], root)
        print(f"  {marker:<8} {branch:<24} 改动 {dirty} / 未跟踪 {untracked}  ({where}) {w['path']}")
    print()

    print("远程分支")
    for r in remotes:
        verdict = "SAFE" if r["merged"] else "UNMERGED"
        print(f"  {verdict:<10} origin/{r['branch']}")
    print()

    print("stash")
    seen: dict[str, int] = {}
    for s in stashes:
        dup = seen.get(s["digest"])
        seen[s["digest"]] = seen.get(s["digest"], 0) + 1
        note = f"（与 {dup} 重复）" if dup else ""
        print(f"  {s['ref']:<10} {s['date'][:10]}  {s['subject'][:60]}{note}")
    if not stashes:
        print("  （空）")
    print()

    print("归档 tag（不在基线上的）")
    tags = [t for t in git("tag", "--no-merged", base, check=False).splitlines() if t]
    for t in tags:
        print(f"  {t}")
    if not tags:
        print("  （无）")
    print()

    needs_tag = [r["branch"] for r in rows if r["verdict"] == "NEEDS_TAG"]
    dirty_wts = [w["path"] for w in worktrees
                 if any(worktree_state(Path(w["path"]))[k] for k in ("modified", "untracked"))]
    if needs_tag or dirty_wts:
        print("删除前必须处理：")
        for b in needs_tag:
            print(f"  * 分支 {b} 从未推送且未合并 —— 先 git tag archive/{b}-<日期> 再删")
        for p in dirty_wts:
            print(f"  * 工作树 {p} 有未提交内容 —— 先 --export 导出再删")
    else:
        print("没有必须先备份的项。")


def export_all(root: Path, rows: list[dict], worktrees: list[dict],
               stashes: list[dict], base: str, target: Path | None) -> Path:
    if target is None:
        stamp = time.strftime("%Y%m%d-%H%M%S")
        target = Path(tempfile.gettempdir()) / f"visionspace-hygiene-{stamp}"
    target.mkdir(parents=True, exist_ok=True)
    manifest = {"createdAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
                "base": base, "repo": str(root), "branches": [], "worktrees": [], "stashes": []}

    for r in rows:
        manifest["branches"].append({"branch": r["branch"], "tip": r["tip"],
                                     "verdict": r["verdict"]})

    for s in stashes:
        name = s["ref"].replace("stash@{", "stash-").replace("}", "") + ".patch"
        (target / name).write_text(s["patch"] + "\n", encoding="utf-8")
        manifest["stashes"].append({"ref": s["ref"], "subject": s["subject"],
                                    "file": name, "digest": s["digest"]})

    for w in worktrees:
        state = worktree_state(Path(w["path"]))
        if not state["modified"] and not state["untracked"]:
            continue
        slug = (w["branch"] or Path(w["path"]).name).replace("/", "_")
        diff = git("diff", "--binary", "HEAD", cwd=Path(w["path"]), check=False)
        if diff:
            (target / f"worktree-{slug}.patch").write_text(diff + "\n", encoding="utf-8")
        untracked_dir = target / "untracked" / slug
        copied, skipped = [], []
        for rel in state["untracked"]:
            src = Path(w["path"]) / rel
            if not src.is_file() or src.stat().st_size > UNTRACKED_COPY_LIMIT:
                skipped.append(rel)
                continue
            dst = untracked_dir / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)
            copied.append(rel)
        manifest["worktrees"].append({"path": w["path"], "branch": w["branch"],
                                      "modified": len(state["modified"]),
                                      "copied": copied, "skipped": skipped})

    (target / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return target


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="删除分支/工作树/stash 之前的仓库卫生报告")
    parser.add_argument("--base", default=None, help="比较基线，默认 origin/main")
    parser.add_argument("--protect", action="append", default=None,
                        help="额外受保护分支，可重复；默认已含 main 与 feature/agent-dev")
    parser.add_argument("--export", nargs="?", const="", default=None, metavar="DIR",
                        help="导出 stash/工作树改动；不给 DIR 则写到 %%TEMP%%/visionspace-hygiene-<时间>")
    args = parser.parse_args()

    root = repo_root()
    base = pick_base(args.base)
    protected = set(PROTECTED_DEFAULT) | {"main"} | set(args.protect or [])

    rows = branch_rows(base, protected)
    worktrees = parse_worktrees()
    remotes = remote_rows(base)
    stashes = stash_rows()

    print_report(base, rows, worktrees, remotes, stashes, root)

    if args.export is not None:
        target = Path(args.export).expanduser() if args.export else None
        written = export_all(root, rows, worktrees, stashes, base, target)
        print(f"\n已导出到 {written}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
