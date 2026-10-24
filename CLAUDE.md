# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Backend (Spring Boot 3.5.8 + Java 21)
mvn spring-boot:run          # Run application (port 8081, context-path /api)
mvn compile                  # Compile
mvn test                     # Run all tests
mvn test -Dtest=ClassName    # Run specific test class

# Frontend (Vue 3 + Vite)
cd frontend
npm install
npm run dev                  # Development server (localhost:5173)
npm run build                # Production build (type-check + vite build)
npm run build-only           # Just vite build, no type-check
npm run type-check           # vue-tsc --build
npm run lint                 # ESLint --fix
npm run format               # Prettier --write src/
npm run openapi2ts           # Regenerate API clients from OpenAPI spec

# Docker
docker-compose up -d         # MySQL 8.0 + Redis 7 + backend
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.5.8, Java 21 (virtual threads), MyBatis Plus 3.5.16, Sa-Token 1.45.0 |
| Database | MySQL 8.0, Redis 7 (session + cache + Caffeine local) |
| AI | Spring AI 1.1.2 + Spring AI Alibaba (DashScope), MCP client |
| Frontend | Vue 3.5, TypeScript 5.6, Vite 6, Ant Design Vue 4, Pinia, ECharts 6 |
| Icons | lucide-vue-next (stroke-based, `currentColor` inherits theme) |
| Storage | x-file-storage (COS/OSS/MinIO) |

## Architecture Overview

### Backend (`src/main/java/com/yuwen/visionspace/`)

Multi-layered architecture with cross-cutting managers:

- `controller/` — 16 REST controllers. **Do NOT add `/api` prefix** — it's in `server.servlet.context-path: /api`
- `service/` — Interface + Impl pattern. Business logic and transactions.
- `mapper/` — MyBatis Plus mappers. XML in `resources/mapper/`.
- `manager/` — Cross-cutting concerns:
  - `auth/` — Sa-Token auth + space-level permissions (`@SaSpaceCheckPermission`)
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
- `api/` — **Auto-generated** from OpenAPI spec via `@umijs/openapi` (run `npm run openapi2ts` to regenerate). Hand-written: `pictureRecommend.ts`, `pictureAction.ts`
- `stores/` — Pinia store (`userLogin.ts`)
- `styles/` — Dual theme: `design-tokens.css` (aurora/Deep Space) + `design-tokens-ziyan.css` (紫漾/Pop Art), toggled via `useTheme`
- `request.ts` — Axios instance with `withCredentials: true` (cookie-based auth via Sa-Token)

**Theme system:** CSS custom properties in `design-tokens.css` and `design-tokens-ziyan.css`. Switched at runtime by `useTheme` composable changing `<html>` class. Lucide icons auto-adapt via `currentColor`.

### Database

- MySQL 8.0. MyBatis Plus with `map-underscore-to-camel-case: false` (uses resultMap manually)
- **Field naming: camelCase** (`userId`, `actionType`, `createdTime`)
- Global logic delete: `isDelete` field
- SQL schemas in `sql/`

## Conventions

- **Jakarta EE imports** — Use `jakarta.*` not `javax.*` (Spring Boot 3)
- **Commit style** — Use `git-commit-cn` skill for Chinese conventional commits
- **Feature development** — Create a worktree, PR to `main`, never commit directly
- **Design docs** — Plans saved to `docs/plans/` (gitignored, use `git add -f`)
- **Prettier** — `semi: false`, `singleQuote: true`, `printWidth: 100`
- **API regeneration** — After backend endpoint changes, run `npm run openapi2ts` in frontend

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
