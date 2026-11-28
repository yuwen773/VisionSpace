# VisionSpace

云端 AI 图片管理、检索与协作平台：把图片放进个人空间或团队空间，用颜色、语义与长期偏好把它们重新找回来，并通过 SpaceMind Agent 继续创作。

[![CI](https://github.com/yuwen773/VisionSpace/actions/workflows/srf.yml/badge.svg?branch=main)](https://github.com/yuwen773/VisionSpace/actions/workflows/srf.yml)
[![Release](https://img.shields.io/github/v/release/yuwen773/VisionSpace?include_prereleases&label=release)](https://github.com/yuwen773/VisionSpace/releases)

当前发布线 **1.1.0**。PostgreSQL 18 迁移、原生部署模板、素材检索与候选板、公开图库、内容审核隔离已并入主线；Agent 首帧延迟与首帧前 500 仍在 [#168](https://github.com/yuwen773/VisionSpace/issues/168) / [#169](https://github.com/yuwen773/VisionSpace/issues/169) 跟踪。

## 功能特性

### 账号与会员

- 注册、登录、注销；浏览器走 Sa-Token Session Cookie，小程序走同一套账号的令牌通道
- 个人中心：资料、头像、我的图片与统计、会员权益与兑换码
- 长期偏好：Agent 跨会话记忆，可查看、修改、清除；自动学习默认关闭
- 意见反馈与附件

### 图片与检索

- 本地上传、URL 上传、批量上传；编辑、裁剪、标签与分类
- 图片审核（待审核 / 通过 / 拒绝）与违规资源隔离、恢复（操作者与理由留痕）
- 主色调提取：K-Means / Median Cut 双策略，按颜色检索相似图片
- 以图搜图：基于外部图库的视觉相似检索
- **公开图库**：匿名浏览已审核的公开图片
- **素材检索**：团队空间内按条件筛选，结果以快照形式保留判定依据
- **候选素材板**：在团队空间内挑选、排序、临时分组；保存草稿不修改原图，也不自动共享

### 推荐

- 首页瀑布流：热门 / 最新 / 质量 / 随机
- 多维加权评分（互动、时效、质量、转化、手动）+ 多样性重排（作者去重、分类打散）
- 稳定分页上下文；Redis + Caffeine 二级缓存

### AI 能力

- **AI 扩图**：阿里云 Outpainting
- **SpaceMind Agent**：基于 ReactAgent 的多工具智能体，SSE 流式对话、工具调用可视化
- **Agent 工具**：站内图库检索、Pexels 内容图搜索、百度以图搜图、文生图、图片特征分析、质量评估
- **MCP 支持**：按用户接入个人 MCP 工具
- **会话附件**：图片绑定到 Agent 会话，未绑定的临时图片按保留期回收
- **RAG（可选）**：Chroma 站内语义检索，默认关闭（`spring.ai.vectorstore.type=none`）
- 会话恢复、待核对动作、运行状态持久化

### 空间协作

- 私有空间 / 团队空间；空间级别（普通 / 专业 / 旗舰）
- 成员角色：viewer（查看）、editor（上传与编辑）、admin（空间与成员管理）
- 平台管理员、违规处置员与团队成员三类身份互不继承
- 资源查看授权：向指定用户授予限时只读权限，可随时撤销
- WebSocket 实时协作编辑（Disruptor 事件队列）与协作监控

### 数据与存储

- 空间分析：使用量、分类、标签、大小、用户、排名 6 维图表
- 行为事件：曝光 / 点击 / 浏览 / 点赞 / 收藏 / 下载 / 分享；幂等键去重，异步聚合入账
- 多平台存储：腾讯云 COS / 阿里云 OSS / MinIO，支持切换与候选探活、删除重试与清理任务
- 管理后台：Dashboard、用户 / 图片 / 空间 / 存储 / 反馈管理、违规处置台
- 双主题：极光（深空暗色）/ 紫漾（浅紫粉）

## 技术栈

### 后端

| 类别 | 技术 / 版本 |
|------|-------------|
| 框架 | Spring Boot 3.5.16 |
| Java | 21（虚拟线程） |
| 数据库 | PostgreSQL 18（Flyway 管理 schema，基线 `V1__baseline.sql`） |
| ORM | MyBatis Plus 3.5.16 |
| 缓存 | Redis（compose 7 / 生产机 8）+ Caffeine |
| 认证 | Sa-Token 1.46.0（默认会话 + space 会话双 StpLogic） |
| AI | Spring AI 1.1.8 + Spring AI Alibaba 1.1.2.4-security-fix（DashScope） |
| 向量库 | Chroma（可选 profile，默认关闭） |
| 接口文档 | springdoc 2.8.17 + Knife4j 4.4.0 |
| 高性能队列 | LMAX Disruptor 3.4.2 |
| 文件存储 | x-file-storage 2.2.1（COS / OSS / MinIO） |
| MCP 客户端 | MCP Java SDK 1.1.5 |
| 迁移工具 | Flyway 13.13.0（CLI / Maven 插件；运行时禁用） |

### 前端

| 类别 | 技术 / 版本 |
|------|-------------|
| 框架 | Vue 3.5 + TypeScript 5.6 |
| 构建工具 | Vite 6 |
| UI 组件 | Ant Design Vue 4 |
| 状态管理 | Pinia 2 |
| 路由 | Vue Router 4 |
| 图表 | ECharts 6 + vue-echarts 8 |
| 图标 | lucide-vue-next |
| 内容渲染 | markdown-it + highlight.js + Mermaid + PlantUML / MxGraph |
| 测试 | Vitest 5 + Playwright（浏览器场景校验） |

### 微信小程序

| 类别 | 技术 / 版本 |
|------|-------------|
| 平台 | 原生小程序 + TypeScript |
| 逻辑层测试 | Vitest（无 DOM 纯 TS） |
| 主题 | 跟随系统：深色极光 / 浅色紫漾 |

### 部署

- **Docker Compose**：PostgreSQL 18.7 + Redis 7 + 后端；Chroma 走 `rag` profile
- **原生部署**：`deploy/` 提供 systemd + nginx + 本机 PostgreSQL 18 / Redis 8 模板，生产守卫（`ProductionSecretsGuard`）在启动期校验必填配置

## 项目结构

```
src/main/java/com/yuwen/visionspace/
├── agent/                  # SpaceMind Agent
│   ├── config/             # Agent 装配
│   ├── controller/         # Agent 接口
│   ├── hook/               # 迭代控制、消息裁剪、摘要、偏好加载
│   ├── interceptor/        # 模型调用拦截（MCP 等）
│   ├── manager/            # 会话与运行状态管理
│   ├── mcp/                # MCP 客户端适配
│   ├── model/              # Agent 领域模型
│   ├── recovery/           # 会话恢复与待核对动作
│   ├── service/            # Agent 业务服务
│   └── tools/              # 检索、生成、分析、评估、以图搜图
├── annotation/             # @AuthCheck 等自定义注解
├── aop/                    # 权限拦截
├── api/                    # 外部 AI 能力（aliyunai、imagesearch）
├── common/                 # BaseResponse、分页、排序、结果工具
├── config/                 # Knife4j、MyBatis Plus、Sa-Token 等配置
├── constant/               # 常量与枚举入口
├── controller/             # REST 控制器（20 个）
├── exception/              # BusinessException、全局异常处理
├── job/                    # 定时任务（附件回收、索引重试、统计聚合、缓存刷新、授权过期、存储清理）
├── manager/                # 跨切面管理器
│   ├── auth/               # 权限判定与角色映射
│   ├── cache/              # 推荐缓存
│   ├── storage/            # 多平台存储
│   ├── upload/             # 上传模板方法
│   └── websocket/          # 实时协作（Disruptor）
├── mapper/                 # MyBatis Plus Mapper（XML 在 resources/mapper/）
├── model/                  # dto / entity / enums / vo
├── rag/                    # 公开图库向量索引与健康检查
├── service/                # 业务服务（接口 + impl）
├── tools/                  # 辅助工具（vip 等）
└── utils/                  # 通用工具

src/main/resources/
├── application.yml         # 主配置（端口 8081，context-path /api）
├── application-prod.yml    # 生产 profile：回环绑定、必填项、连接池
├── db/migration/           # Flyway 迁移（V1 基线已冻结）
└── mapper/                 # MyBatis XML

frontend/src/
├── api/                    # OpenAPI 生成客户端（勿手改）
├── api-custom/             # 手写客户端（检索、素材板、存储、MCP 等）
├── components/             # agent / admin / analyze / auth / picture / user-center / vue-bits 等
├── composables/            # 主题、SSE、Markdown、图表、推荐分页、可撤销操作等
├── layouts/                # 用户端 / 管理端布局
├── pages/                  # Landing、Explore、MySpace、SpaceDetail、SpaceAnalyze、
│                           # AddPicture、PictureDetail、AgentChat、user/、admin/、moderation/
├── stores/                 # Pinia
├── styles/                 # 双主题 design tokens
├── request.ts              # Axios 实例（withCredentials）
└── access.ts               # 路由鉴权

miniprogram/src/
├── pages/                  # 发现 / 登录 / 我的 / 作品 / 空间 / 空间详情 / 上传
├── services/               # 请求、会话、上传、颜色、埋点
├── logic/                  # 无 DOM 纯 TS：分页、展示、错误文案、上传队列
├── styles/                 # 两套主题的 WXSS 变量
└── app.* / theme.json      # 全局配置与深色模式

deploy/                     # 原生部署模板（systemd、nginx、PG、初始化、自检、备份）
docker/                     # 容器构建相关
scripts/                    # 验收、隔离测试、仓库卫生、门禁
```

## 快速开始

### 前置条件

- JDK 21+
- Node.js 18+
- Maven 3.9+
- PostgreSQL 18+、Redis 7+
- 或 Docker / Docker Compose

### Docker Compose 一键启动

```bash
git clone https://github.com/yuwen773/VisionSpace.git
cd VisionSpace

# 1. 准备环境变量（数据库与 Redis 口令是必填，缺了会直接启动失败）
cp .env.example .env
# 编辑 .env，至少填 POSTGRES_SUPERUSER_PASSWORD、POSTGRES_MIGRATOR_PASSWORD、
# SPRING_DATASOURCE_PASSWORD、REDIS_PASSWORD，以及模型 Key

# 2. 先起数据库，再跑 Flyway 建表（业务表只由 Flyway 创建）
docker-compose up -d postgres
docker compose --profile tools run --rm flyway migrate

# 3. 启动 PostgreSQL + Redis + 后端
docker-compose up -d

# 4. 启动前端
cd frontend
npm install
npm run dev
```

需要站内语义检索时再加 `rag` profile（Chroma）；默认不启用以节省内存。

### 原生部署（systemd + nginx）

适用于低内存机器（如 2 vCPU / 1.7 GB）。完整步骤、键名对照与授权边界见 [deploy/README.md](deploy/README.md)：

```bash
# 角色、目录、独立空库、迁移、unit、nginx、证书、自检
sudo VS_DB_NAME=vision_space_prod ./deploy/init-production-db.sh
VS_DB_NAME=vision_space_prod ./deploy/flyway-migrate.sh
install -m 644 deploy/visionspace.service /etc/systemd/system/visionspace.service
systemctl daemon-reload && systemctl enable --now visionspace
```

生产 profile 的契约写在 [ADR-0005](docs/adr/0005-production-deployment-contract.md)：业务端口只绑 `127.0.0.1`，模型端点与模型名必填，模板与代码的一致性由门禁保证。

### 后端（本地手动）

```bash
cp .env.example .env      # 填写本机数据库 / Redis / 模型配置
set -a; . ./.env; set +a  # Git Bash / Linux / macOS
mvn spring-boot:run       # http://localhost:8081/api
```

schema 由 Flyway 显式创建，应用启动不执行 DDL。凭据走环境变量，Flyway 读取 `FLYWAY_URL` / `FLYWAY_USER` / `FLYWAY_PASSWORD`。

### 前端

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173，/api 反代到 8081
npm run build      # 类型检查 + 生产构建
```

### 微信小程序

用微信开发者工具导入 `miniprogram/`，把 `project.config.json` 的 `appid` 换成自己的小程序 AppID（默认 `touristappid` 只能游客预览）。真机与体验版要求合法域名为已备案的 HTTPS 域名。详见 [miniprogram/README.md](miniprogram/README.md)。

### 接口文档

启动后访问 `http://localhost:8081/api/doc.html`。

## 配置

| 文件 | 用途 |
|------|------|
| `application.yml` | 主配置：端口、context-path、数据源、AI、推荐权重等 |
| `application-prod.yml` | 生产 profile：回环绑定、连接池、SSE 超时、必填项默认值 |
| `application-local.yml` | 本地覆盖（不入库） |
| `application-server.yml` | 服务器本地覆盖（不入库） |

两套环境变量口径**不能混用**：

- **compose 口径**（`.env.example`）：`POSTGRES_*`、`REDIS_PASSWORD`、`BACKEND_PORT`
- **原生口径**（`deploy/app.env.example`）：`SPRING_DATASOURCE_*`、`SPRING_DATA_REDIS_*`

键名对照表在 [deploy/README.md §2](deploy/README.md)。生产启动时 `ProductionSecretsGuard` 会拒绝缺失、空白、开发回退值和未替换的 `<占位>`。密钥纪律见 [SECRETS.md](SECRETS.md)。

业务自定义配置统一在 `application.yml` 的 `vision-space:` 前缀下（存储候选探活、初始口令策略、可信来源与 CSRF、小程序令牌、Agent、颜色提取、推荐权重等）。

## 核心模块

### 颜色检索

```java
// 主色调提取（K-Means / Median Cut 由策略选择）
String dominantColor = ColorExtractUtils.extractDominantColor(image);

// 颜色相似度
double similarity = ColorSimilarUtils.calculateSimilarity("#FF5733", "#FF0000");
```

### 公开图库与站内语义检索

- 公开图库只展示已审核公开图片，匿名可读；写入与查询都实时核对数据库状态。
- 站内语义检索走 Chroma，`spring.ai.vectorstore.type=chroma` 时才装配；默认 `none`，此时检索工具不存在，Agent 回退到数据库筛选与外部图库。

### 推荐引擎

```
综合得分 = 互动分×35% + 时效分×25% + 质量分×20% + 转化分×10% + 手动分×10%
→ 作者去重 → 分类打散 → 稳定分页上下文 → 推荐列表
```

### Agent

| 工具 | 功能 |
|------|------|
| `searchContentImages` | Pexels 内容图搜索 |
| `searchInternalImages` | 站内语义检索（需开启 RAG） |
| `searchSimilarImages` | 百度以图搜图 |
| `generateImages` | 文生图（生成前需用户确认） |
| `analyzeImageFeatures` | 多模态结构化特征分析 |
| `evaluateImageQuality` | 结果与意图匹配度评估 |

Hook 负责迭代控制、消息裁剪、对话摘要与长期偏好加载；会话与运行状态持久化，支持恢复与待核对动作。

### 权限

| 入口 | 作用 |
|------|------|
| `@AuthCheck(mustRole = "admin")` | 全局角色校验 |
| `SpaceAuthority`（服务层） | 团队角色、平台管理员、私人归属分别判定 |

空间/图片级判定在服务层完成，控制器只负责取得当前用户与资源。判定矩阵见 [ADR-0001](docs/adr/0001-resource-authorization-boundaries.md)。

### 存储

基于 x-file-storage 统一抽象 COS / OSS / MinIO。管理端可切换默认平台；变更默认平台不会改写旧资源的所属平台，删除失败进入可重试状态，清理任务单独运行。

## 数据库

业务表共 25 张，按领域分组：

| 领域 | 表 |
|------|----|
| 用户与会员 | `user`、`vip_redemption_code`、`vip_redemption_record`、`user_preference` |
| 图片 | `picture`、`picture_file`、`picture_stats`、`user_picture_action` |
| 空间与授权 | `space`、`space_user`、`resource_access_grant` |
| Agent | `agent_session`、`agent_message`、`agent_session_attachment`、`agent_run_state`、`agent_action_record` |
| 检索与索引 | `asset_search_snapshot`、`picture_index_task` |
| 治理与反馈 | `resource_isolation`、`user_feedback`、`feedback_attachment` |
| 存储 | `storage_config`、`storage_switch_state`、`storage_cleanup_task` |
| 外部工具 | `mcp_server` |

### 数据规范

- 数据库：PostgreSQL 18；表结构只由 Flyway 建（`src/main/resources/db/migration/V1__baseline.sql`），应用启动不执行 DDL。
- 列命名 `snake_case`（`user_id`、`created_time`）；Java 字段与 JSON 保持 camelCase。
- 逻辑删除：`is_delete`（1 表示删除）。
- 时间列统一 `timestamp(0)` 无时区；SQL 取当前时间用 `statement_timestamp()`，不用事务时间的 `now()`。
- 账号等文本唯一性走 `lower(...)` 大小写不敏感索引；不透明 ID / 哈希保持大小写敏感。
- 资源标识是超出 JavaScript 安全整数范围的雪花值，接口之间按十进制字符串传递。

契约详见 [ADR-0004](docs/adr/0004-postgresql-schema-and-time-contract.md)。

## API 模块

所有接口挂在 context-path `/api` 下，控制器路径不再重复写 `/api`。

| 模块 | 路径 | 核心功能 |
|------|------|----------|
| 健康检查 | `/` | `/health` |
| 用户 | `/user` | 注册、登录、资料、头像、统计、VIP 兑换 |
| 微信登录 | `/user/wx` | 小程序登录 |
| 图片 | `/picture` | 上传、编辑、审核、搜索、扩图 |
| 图片内容 | `/picture/content` | 原图、预览、下载（受资源权限约束） |
| 行为 | `/picture/action` | 曝光 / 点击 / 浏览 / 点赞 / 收藏 / 下载 / 分享 |
| 查看授权 | `/picture/grant` | 限时只读授权与撤销 |
| 协作 | `/picture/collab` | 实时协作监控 |
| 推荐 | `/picture/recommend` | 热门 / 最新 / 质量 / 随机与翻页上下文 |
| 空间 | `/space` | 空间管理 |
| 空间成员 | `/spaceUser` | 成员与角色 |
| 空间分析 | `/space/analyze` | 使用量、分类、标签、大小、用户、排名 |
| 存储 | `/storage/config` | 多平台存储配置与切换 |
| 反馈 | `/feedback` | 反馈与附件 |
| Agent | `/agent` | SSE 对话、会话、附件、历史、恢复 |
| Agent 偏好 | `/agent/preference` | 长期偏好读写与清除 |
| MCP | `/mcp-server` | 个人 MCP 服务配置 |
| 内容审核 | `/moderation` | 违规资源隔离与恢复 |
| 向量索引 | `/vector-store` | 索引同步与重建（RAG 开启时） |
| 管理统计 | `/admin/stats` | 管理后台 Dashboard |

前端客户端由 OpenAPI 契约生成到 `frontend/src/api/`，手写客户端放 `frontend/src/api-custom/`。重新生成：

```bash
cd frontend
OPENAPI_SCHEMA_PATH="$PWD/../target/openapi-contract.json" OPENAPI_SERVERS_PATH="$PWD/src" \
  node scripts/openapi-check.mjs
```

## 质量门禁

```bash
# 后端全量验收（隔离 PostgreSQL 18 / Redis，约 5–15 分钟）
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1

# 单个测试类
python -I scripts/drill-isolated-run.py --test SomeTest

# 部署模板与生产库初始化门禁
python -I scripts/tests/test-deploy-templates.py
python -I scripts/tests/test-production-db-init.py

# 前端
cd frontend && npm run type-check && npm run lint && npm run build
```

CI 入口是 [`.github/workflows/srf.yml`](.github/workflows/srf.yml)，覆盖 acceptance、container、browser、security、evidence 与 merge gate；小程序有独立 workflow。验收口径与故障排查见 [ACCEPTANCE.md](ACCEPTANCE.md)。

## 文档

| 文档 | 内容 |
|------|------|
| [CONTEXT.md](CONTEXT.md) | 领域词汇与边界 |
| [PRODUCT.md](PRODUCT.md) | 产品定位、用户与约束 |
| [DESIGN.md](DESIGN.md) | 视觉与交互规范 |
| [ACCEPTANCE.md](ACCEPTANCE.md) | 验收环境与门禁 |
| [SECRETS.md](SECRETS.md) | 密钥纪律与生产必填项 |
| [deploy/README.md](deploy/README.md) | 原生部署手册 |
| [docs/adr/](docs/adr/) | 架构决策记录 |
| [docs/plans/](docs/plans/) | 迁移、生产就绪与专项计划 |

## License

MIT
