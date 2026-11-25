# VisionSpace 1.1.0 Production Readiness 待办清单

- 创建：2026-11-26（M4 实机验收收口后）
- 触发来源：`docs/plans/2026-11-24-postgresql-migration.md` 附录 F（F.10 实机发现、F.11 未覆盖与上线前置、F.12 三条判定）
- 适用范围：**1.1.0 首次正式上线**。本清单**独立于** PostgreSQL 迁移收口——迁移本体已通过实机验收，本清单里的每一条都不属于数据库迁移范围。
- 明确不做：不扩 PostgreSQL 迁移范围、不引入 pgvector、不重跑已完成的迁移全量审计。

## 判定口径

| 判定 | 含义 |
|---|---|
| MERGE_READY | 代码可以并进 `main`（与部署、上线无关）|
| RELEASE_READY | 按手册部署到 `121.199.77.168` 能正常提供服务 |
| PRODUCTION_READY | 可以对外接真实用户 |

PostgreSQL 迁移当前为 **MERGE_READY = 是 / RELEASE_READY = 有条件 / PRODUCTION_READY = 否**。下列 10 项全部闭合并复验前，不进入 PRODUCTION_READY。

## 待办清单

| # | 事项 | 现状（实机实测） | 完成标准 | 验证方式 | 阻塞级别 |
|---|---|---|---|---|---|
| 1 | **Agent 首帧延迟专项定位** | 首帧 28–30 秒（一次 65.6 秒）；同供应商 `curl -N` 直连 1.5 秒出首包 → 延迟在应用内、模型调用之前；同期日志只有 `Agent 流式对话请求` 一行，无工具/节点级日志可归因 | 定位到具体阶段（会话建立 / 附件绑定 / MCP 初始化 / 检索 / 提示词装配）并给出根因与量化 | 分段埋点或采样火焰图；首帧 P95 有明确基线 | 体验项，不阻塞上线，但影响 Agent 可用性验收 |
| 2 | **SSE 首帧前失败的错误处理** | 模型在首帧前失败时客户端收到 **HTTP 500 空体**（`HttpMessageNotWritableException`，响应已声明 `text/event-stream`）；`AgentController`/`GlobalExceptionHandler` 相对 `main` 无改动 → 既有行为 | 首帧前失败也返回可解析的错误（SSE `error` 帧或 JSON 错误体），前端能显示原因 | 故障注入用例 + 前端回归；单开 issue 跟踪 | 上线前必修 |
| 3 | **模型供应商额度与配置** | DashScope 免费额度耗尽（`403 AllocationQuota.FreeTierOnly`），兼容模式与原生端点同样 403；`app.env` 缺 `SPRING_AI_OPENAI_BASE_URL` 时会打 `api.openai.com`（大陆不可达） | 额度充足或换供应商；`SPRING_AI_OPENAI_BASE_URL`、`SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` 进部署模板 | 真实一轮对话返回内容 + 30 天额度余量报表 | 上线前必修 |
| 4 | **部署模板完整性** | 实机 `app.env` 24 键；`.env.example` 用的是 compose 口径（`BACKEND_PORT`/`POSTGRES_*`/`REDIS_PASSWORD`），原生部署用 `SPRING_DATASOURCE_*`/`SPRING_DATA_REDIS_PASSWORD`；缺 `SPRING_MVC_ASYNC_REQUEST_TIMEOUT=300000`（与默认 30 秒异步超时撞车）| 一份键名对照表 + 原生部署模板，`app.env` 与 `.env.example` 差异全部登记 | 拿模板在干净机器上装一遍并跑 readiness | 上线前必修 |
| 5 | **真实 COS/OSS 与 domain 配置校验** | 验收期用 moto 顶 S3；存储配置缺 `domain` 时产出 `null` 前缀 URL（`nullacceptance//space/...`），预览失败 | 真实 COS/OSS 桶 + 公开 `domain` 配好；上传后 `picture.url`、预览、缩略图在真实域名下可用 | 端到端上传→预览→删除的真实请求留证 | 上线前必修 |
| 6 | **8081 回环绑定** | 应用绑 `*:8081`；外网直连实测不可达（公网入口只放行 `80`/`22`），但纵深防御有缺口 | 显式绑 `127.0.0.1`（或安全组显式白名单），`ss -lntp` 复核 | 部署后监听面截图 + 外网直连复测 | 上线前必修 |
| 7 | **验收数据清理及生产库初始化** | 验收库 `vision_space` 里留着 4 个账号、1 个空间、38 张图、10 条 agent session；恢复库已删、moto 已停 | 验收数据清空（`TRUNCATE`/重建库）、种子行复位；生产库由 Flyway 从空库建起，角色与授权就位 | 行数断言 + `flyway validate` + readiness | 上线前必修 |
| 8 | **公网域名、正式 HTTPS 证书** | 只有自签证书（CN=`visionspace.space`，30 天），`:80` 是 nginx 默认站点；`:443` 未开 | 域名解析到位、certbot 签发正式证书、强制 HTTPS、`80` 只做跳转 | `curl -I https://<域名>` + 证书链核验 | 上线前必修 |
| 9 | **Redis 持久化、定时备份及监控** | Redis 8.0.5：`requirepass`、`maxmemory 64mb`、`noeviction`、`appendonly no`；PG 备份只演练了手工一次（248 KB dump）| Redis 持久化策略定案（AOF 或 RDB + 周期快照）；每日 `pg_dump -Fc` + 保留 7 天 + 异地；备份失败告警 | 定时任务跑通 + 一次真实定时备份与恢复验证 | 上线前必修 |
| 10 | **微信小程序凭据与真机验收（若纳入首发）** | 验收机 `app.env` 无 `VISION_SPACE_WX_APPID`/`VISION_SPACE_WX_SECRET`，微信登录链路本轮绕开 | 凭据就位、真机登录/上传/浏览跑通、失败有明确提示 | 小程序真机清单逐条留证 | 首发含小程序则必修 |

## 顺序建议

1. 先做 3、4、5、6、7（配置与数据面，改动小、互相耦合，一次装完再验）；
2. 再做 8（域名与证书，需要 DNS 生效时间）；
3. 然后 2（错误处理，需要改代码 + 回归）、1（首帧定位，可能牵动 Agent 架构，放最后但要在正式开放前有结论）；
4. 9 与 10 可并行推进（9 是运维侧，10 依赖小程序侧资源）。

## 复验要求

本清单任何一项改动后：

- 若涉及后端代码：重跑 `pwsh -File ./scripts/acceptance.ps1`（或工作树内 `python -I scripts/drill-isolated-run.py --full`），并让 CI 在最终 HEAD 上全绿；
- 若只涉及部署配置：在 `121.199.77.168` 上重跑对应的实机项，并把证据按 `docs/plans/evidence/2026-11-25-m4/` 的同一格式归档；
- 全部闭完后更新 `docs/plans/2026-11-24-postgresql-migration.md` 附录 F 的 F.12 三条判定，再申请 PRODUCTION_READY 与放生产流量的授权。
