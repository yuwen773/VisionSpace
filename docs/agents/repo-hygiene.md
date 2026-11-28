# 仓库卫生（分支 / 工作树 / stash）

清理前先跑只读报告，它把每一项判成可执行的状态，而不是靠人肉回忆：

```bash
python -I scripts/repo-hygiene.py            # 只读报告
python -I scripts/repo-hygiene.py --export   # 导出 stash/工作树改动到 %TEMP%/visionspace-hygiene-<时间>
```

报告里的判定：

| 判定 | 含义 | 处理 |
|---|---|---|
| `SAFE` | 已合进基线 | 可以直接删 |
| `NEEDS_TAG` | 从未推送、也未合并 | **先 `git tag archive/<分支>-<日期>` 再删**，否则不可恢复 |
| `PUSHED_UNMERGED` | 未合并但远程还有副本 | 确认没在用再删，远程分支单独处理 |
| `PROTECTED` | 受保护分支（默认 `feature/agent-dev`） | 只报告，不动 |
| `EXPORT` | 工作树有未提交内容 | 先 `--export`，确认 patch/副本落盘再删工作树 |

## 删除顺序

1. `--export`，检查导出目录里的 `manifest.json` 与 patch 是否覆盖了所有 `EXPORT`/`NEEDS_TAG` 项。
2. 删除工作树：`git worktree remove <path>`（有未提交内容时 `--force` 会连未跟踪文件一起删，导出后再用）。
3. 删本地分支：`git branch -d`（已合并）；`NEEDS_TAG` 的项打 tag 后用 `-D`。
4. 删远程分支：`git push origin --delete <分支>`，只删报告里 `SAFE` 的。
5. 清 stash：`git stash drop stash@{n}`，从高索引往低索引删。

## 硬规则

- **不推、不合**：`feature/agent-dev` 是本地独有工作，带明文凭据，永不推送、永不删除。
- **先 tag 后删**：无 upstream 的分支删掉即永久丢失，即使内容"看起来像草稿"。归档 tag 用 `archive/<分支>-<YYYY-MM-DD>`，保留在仓库里。
- **导出位置**：`%TEMP%` 会被系统清理，需要长期保留的挪到别处；导出目录命名统一为 `visionspace-hygiene-<时间>`，便于盘点。
- **Docker 资源**：分支清理不涉及容器；若同时收容器/卷，用 `scripts/docker-test-resources.ps1`，禁止 `docker ... prune`。

## 合并 PR 之后的本地同步

```bash
git checkout main
git fetch origin --prune
git merge --ff-only origin/main
git branch -D <刚合的分支>
git push origin --delete <刚合的分支>   # 仓库没开自动删分支
```

判断能不能快进**用 `git merge-base --is-ancestor <ref> origin/main`**：`git merge --ff-only --no-commit` 对快进无效，`--no-commit` 不会阻止分支指针移动，别把它当预演。
