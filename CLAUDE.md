# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Verification — the only entry point that can run the backend tests.
# `mvn test` alone aborts with BLOCKED_ENV: the test suite refuses to start
# without the isolated PostgreSQL/Redis instances this script provisions.
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1   # ~5-15 min; see ACCEPTANCE.md

# Backend (Spring Boot 3.5.16 + Java 21)
mvn spring-boot:run          # Run application (port 8081, context-path /api)
# 密钥全部走环境变量：Spring Boot 不原生读 .env，本地启动前先导出。
# Git Bash / Linux / macOS:  set -a; . ./.env; set +a; mvn spring-boot:run
# PowerShell:                 Get-Content .env | ForEach-Object {
#   if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
#     [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2])
#   }
# }
# 复制 .env.example 为 .env 后填写；.env 已 gitignore，禁止提交真实凭据。
mvn compile                  # Compile
mvn test -Dtest=ClassName    # Run one test class (still needs the acceptance env above)

# Frontend (Vue 3 + Vite)
cd frontend
npm install
npm run dev                  # Development server (localhost:5173)
npm run build                # Production build (type-check + vite build)
npm run build-only           # Just vite build, no type-check
npm run type-check           # vue-tsc --build
npm run lint                 # ESLint --fix
npm run format               # Prettier --write src/
npm run openapi2ts           # Regenerate API clients (needs a backend on :8081; see API regeneration)

# Docker
docker-compose up -d         # PostgreSQL 18 + Redis 7 + backend（向量库默认不启动）
# 业务表只由 Flyway 建，backend 起来前必须先迁移一次（否则 Hikari 启动期查不到表就退出）：
#   docker-compose up -d postgres
#   docker compose --profile tools run --rm flyway migrate
#   docker-compose up -d
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.5.16, Java 21 (virtual threads), MyBatis Plus 3.5.16, Sa-Token 1.45.0 |
| Database | PostgreSQL 18 (Flyway-managed schema), Redis 7 (session + cache + Caffeine local) |
| AI | Spring AI 1.1.8 + Spring AI Alibaba 1.1.2.4-security-fix (DashScope), native MCP SDK 1.1.5 |
| Frontend | Vue 3.5, TypeScript 5.6, Vite 6, Ant Design Vue 4, Pinia, ECharts 6 |
| Icons | lucide-vue-next (stroke-based, `currentColor` inherits theme) |
| Storage | x-file-storage (COS/OSS/MinIO) |

## Architecture Overview

### Backend (`src/main/java/com/yuwen/visionspace/`)

Multi-layered architecture with cross-cutting managers:

- `controller/` — 20 REST controllers. **Do NOT add `/api` prefix** — it's in `server.servlet.context-path: /api`
- `service/` — Interface + Impl pattern. Business logic and transactions.
- `mapper/` — MyBatis Plus mappers. XML in `resources/mapper/`.
- `manager/` — Cross-cutting concerns:
  - `auth/` — `SpaceAuthority` owns the ADR-0001 decision matrix (team role, platform admin, private ownership); `SpaceUserAuthManager` only maps roles to permission codes
  - `upload/` — Template Method pattern (`PictureUploadTemplate` → `FilePictureUpload`, `UrlPictureUpload`)
  - `storage/` — Multi-platform storage via x-file-storage
  - `websocket/` — Real-time collaboration (Disruptor event queue)
  - `cache/` — Recommendation cache (Redis + Caffeine)
- `agent/` — AI Agent (ReactAgent via Spring AI Alibaba). Tools in `tools/`, hooks in `hooks/`.
- `model/` — DTOs (`dto/`), DB entities (`entity/`), response VOs (`vo/`), enums
- `aop/` — `AuthInterceptor` for `@AuthCheck` role-based access

**Key patterns:**
- Response: `ResultUtils.success(data)` → `BaseResponse<T>` with code/data/message
- Exceptions: `ThrowUtils.throwIf(condition, ErrorCode)` → `BusinessException`
- Auth: `@AuthCheck(mustRole = "admin")` for role gating; `StpKit` for space permissions
- Strategy: Color extraction (`ColorExtractStrategy` → `KMeansStrategy` / `ColorThiefStrategy`)
- Custom config: `vision-space:` prefix in `application.yml` (agent, colors, recommend weights)

### Frontend (`frontend/src/`)

- `pages/` — Route pages (Home, AddPicture, PictureDetail, SpaceDetail, AgentChat, admin/*)
- `components/` — Shared components (agent chat UI, analyze charts, admin panel, user-center tabs)
- `composables/` — `useTheme` (dual theme toggle), `useAgentStream` (SSE), `useMarkdown`, `useDiagramRenderer`
- `api/` — **Auto-generated** from OpenAPI spec via `@umijs/openapi` (regenerate from the exported contract, see Conventions → API regeneration). Hand-written clients live in `api-custom/`; never edit `api/` by hand.
- `stores/` — Pinia store (`userLogin.ts`)
- `styles/` — Dual theme: `design-tokens.css` (aurora/Deep Space) + `design-tokens-ziyan.css` (紫漾/Pop Art), toggled via `useTheme`
- `request.ts` — Axios instance with `withCredentials: true` (cookie-based auth via Sa-Token)

**Theme system:** CSS custom properties in `design-tokens.css` and `design-tokens-ziyan.css`. Switched at runtime by `useTheme` composable changing `<html>` class. Lucide icons auto-adapt via `currentColor`.

### Database

- PostgreSQL 18. Schema is created only by Flyway (`src/main/resources/db/migration`, baseline `V1__baseline.sql`); the app never runs DDL at startup (`spring.flyway.enabled=false`). Two roles: `vision_migrator` (DDL/owner) and `vision_app` (DML only) — applications and tests always connect as `vision_app`.
- **Column naming: snake_case** (`user_id`, `action_type`, `created_time`); Java fields and JSON stay camelCase. MyBatis Plus uses `map-underscore-to-camel-case: true`, but raw `QueryWrapper` column strings must be written snake_case by hand.
- `timestamp(0)` without time zone; SQL "now" is `statement_timestamp()`, never `now()` (PG `now()` is transaction time). Text uniqueness: account is case-insensitive via `lower(user_account)`; opaque IDs/hashes are case-sensitive.
- Global logic delete: `isDelete` field
- SQL schemas in `sql/`

## Conventions

- **Shell paths** — Git Bash `/tmp` is `%TEMP%`. Tools that are not Git Bash (gh, pwsh, docker) need the Windows path: pass `$TEMP/x.md`, not `/tmp/x.md`.
- **Jakarta EE imports** — Use `jakarta.*` not `javax.*` (Spring Boot 3)
- **Commit style** — Use `git-commit-cn` skill for Chinese conventional commits
- **Feature development** — Create a worktree, PR to `main`, never commit directly. Fetch `origin main` first: local `main` is often stale; base the worktree on `origin/main`.
- **Repo hygiene** — Before deleting branches, worktrees or stashes, run `python -I scripts/repo-hygiene.py` and follow `docs/agents/repo-hygiene.md`. Never delete an unpushed branch without an `archive/*` tag first.
- **Worktree test runs** — Use `python -I scripts/drill-isolated-run.py` for single classes or `--full` for the backend suite; it provisions isolated PostgreSQL 18 / Redis in Docker and removes its volume on exit.
- **Docker test resources** — New tests create services through `scripts/docker-test-harness.psm1` (named volumes + `vs.*` labels + run-id teardown); manual `docker run` must mount a named volume and carry the labels. Reclaim only via `scripts/docker-test-resources.ps1`; never `docker ... prune`.
- **Before code review** — Run `python -I scripts/tests/check-review-target.py`; commit first so the review sees HEAD, not a stale index. Judgment-call standards are in `CODING_STANDARDS.md`.
- **Design docs** — Plans saved to `docs/plans/` (gitignored, use `git add -f`)
- **API regeneration** — After backend endpoint changes, regenerate `frontend/src/api` from the contract that acceptance exports to `target/openapi-contract.json`; no live backend, PostgreSQL or Redis needed. From `frontend/`: `OPENAPI_SCHEMA_PATH="$PWD/../target/openapi-contract.json" OPENAPI_SERVERS_PATH="$PWD/src" node scripts/openapi-check.mjs`. Only if that contract is missing, fall back to `npm run openapi2ts` (needs a backend on :8081)

## Agent skills

### Issue tracker

Issues and specs live in GitHub Issues for `yuwen773/VisionSpace`.
See `docs/agents/issue-tracker.md`.

### Triage labels

Use the five default triage labels.
See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: root `CONTEXT.md` and `docs/adr/`.
See `docs/agents/domain.md`.
