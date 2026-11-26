# deploy/：原生部署模板

阿里云 ECS（2 vCPU / 1.7 GB）上的**原生部署**模板：systemd + nginx + 本机 PostgreSQL 18 + 本机 Redis 8，不使用 Docker（机器内存放不下 Docker 的额外开销）。

仓库根目录的 `.env.example` 是 **compose 口径**（`POSTGRES_*` / `REDIS_PASSWORD` / `BACKEND_PORT`），本目录的 `app.env.example` 是 **原生口径**（`SPRING_DATASOURCE_*` / `SPRING_DATA_REDIS_*`）。两套键名**不能混用**：照抄 compose 的 `.env` 去写 `app.env` 会缺键，应用启动即被守卫拦下。

## 1. 文件

| 文件 | 装到哪 | 说明 |
|---|---|---|
| `app.env.example` | `/opt/visionspace/app.env`（600 root） | 环境变量模板。`<...>` 占位必须全部替换，否则启动被拒 |
| `visionspace.service` | `/etc/systemd/system/visionspace.service` | systemd 单元 |
| `nginx-visionspace.conf` | `/etc/nginx/conf.d/visionspace.conf` | 站点配置；443 段由 certbot 签发后就地启用 |
| `postgresql-production.conf` | `/etc/postgresql/18/main/conf.d/vision-space.conf` | PG 18 低资源参数（数值来自实机验收） |
| `init-production-db.sh` | 任意（建议 `/opt/visionspace/`） | **独立空库**初始化：角色、库、扩展、授权 |
| `flyway-migrate.sh` | 任意 | 对目标库执行 Flyway `migrate` + `validate` |
| `postflight-check.sh` | 任意 | 部署后自检：监听面 / readiness / 最小权限 / Flyway 状态 |

## 2. 键名对照表

| 用途 | compose 口径（`.env.example`） | 原生口径（`deploy/app.env.example`） | Spring 属性 | 生产必填 |
|---|---|---|---|---|
| 数据库口令 | `POSTGRES_APP_PASSWORD` / `SPRING_DATASOURCE_PASSWORD` | `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | ✅ |
| 数据库地址 | `SPRING_DATASOURCE_URL`（compose 插值） | `SPRING_DATASOURCE_URL` | `spring.datasource.url` | ✅ |
| 数据库用户 | 固定 `vision_app` | `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | ✅ |
| 迁移角色口令 | `POSTGRES_MIGRATOR_PASSWORD` | 不进 app.env（只在 `FLYWAY_PASSWORD` 里用一次） | — | 迁移时必填 |
| Redis 口令 | `REDIS_PASSWORD` | `SPRING_DATA_REDIS_PASSWORD` | `spring.data.redis.password` | ✅ |
| Redis 地址 | compose 里固定 `redis` | `SPRING_DATA_REDIS_HOST`（用别名 `vision-redis`） | `spring.data.redis.host` | ✅ |
| 后端端口 | `BACKEND_PORT` | 不需要（固定 8081，由 nginx 反代） | `server.port` | — |
| 业务监听面 | 不需要 | `SERVER_ADDRESS` | `server.address` | — |
| 模型 Key | `SPRING_AI_DASHSCOPE_API_KEY` / `SPRING_AI_OPENAI_API_KEY` / `ALI_YUN_AI_API_KEY` | 同名 | `spring.ai.dashscope.api-key` 等 | ✅ |
| 模型端点 | `SPRING_AI_OPENAI_BASE_URL` | 同名 | `spring.ai.openai.base-url` | ✅（不能为空） |
| 模型名 | `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` | 同名 | `spring.ai.openai.chat.options.model` | ✅（不能为空） |
| SSE 超时 | `SPRING_MVC_ASYNC_REQUEST_TIMEOUT` | 同名 | `spring.mvc.async.request-timeout` | 建议显式写 |
| 默认用户口令 | `VISION_SPACE_DEFAULT_PASSWORD` | 同名 | `vision-space.security.default-password` | ✅ |
| 小程序 | `VISION_SPACE_WX_APPID` / `VISION_SPACE_WX_SECRET` | 同名 | — | 可选 |

这张表由 `scripts/tests/test-deploy-templates.py` 机器校验：守卫新增必填项而模板没跟上（或反之），CI 直接红。

## 3. 从零到一台机器

```bash
# 0) 系统与 swap（1.7 GB 内存必须，见部署手册 §3.1）
# 1) PostgreSQL 18 + Redis 8（部署手册 §3.3 / §3.4）
install -m 644 deploy/postgresql-production.conf /etc/postgresql/18/main/conf.d/vision-space.conf
systemctl restart postgresql

# 2) 角色、别名、目录
useradd --system --home /opt/visionspace --shell /usr/sbin/nologin visionspace
mkdir -p /opt/visionspace/{app,dist,logs,backup}
chown -R visionspace:visionspace /opt/visionspace
# 生产守卫不接受 127.0.0.1/localhost 作为 Redis 主机名，用回环别名
grep -q 'vision-redis' /etc/hosts || echo '127.0.0.1 vision-redis' >> /etc/hosts

# 3) 独立空库 + 迁移
sudo VS_DB_NAME=vision_space_prod ./deploy/init-production-db.sh
VS_DB_NAME=vision_space_prod FLYWAY_PASSWORD=<迁移角色口令> ./deploy/flyway-migrate.sh

# 4) 应用
install -m 600 deploy/app.env.example /opt/visionspace/app.env   # 然后逐项替换占位
vi /opt/visionspace/app.env
install -m 644 deploy/visionspace.service /etc/systemd/system/visionspace.service
systemctl daemon-reload && systemctl enable --now visionspace

# 5) nginx + 证书
install -m 644 deploy/nginx-visionspace.conf /etc/nginx/conf.d/visionspace.conf
nginx -t && systemctl reload nginx
certbot --nginx -d visionspace.space -d www.visionspace.space --redirect -m <邮箱> --agree-tos -n

# 6) 自检
FLYWAY_PASSWORD=<迁移角色口令> VS_DB_NAME=vision_space_prod ./deploy/postflight-check.sh
```

## 4. 生产数据库：独立空库

验收期用的是 `vision_space`（里面留着 4 个账号、1 个空间、38 张图、10 条 agent session）。**它不是生产库**，也不应该在清理之前直接拿来上线。正确顺序：

1. **备份**：`pg_dump -Fc` 备份 `vision_space` 到 `/opt/visionspace/backup/`（600）。这份备份**长期保留**——它既是验收期证据，也是回退锚点。
2. **独立空库**：`init-production-db.sh` 建 `vision_space_prod`。脚本只新建库，**不 drop、不 truncate、不改既有库**；目标库已存在时直接拒绝。
3. **建表与切换**：`flyway-migrate.sh` 对新库跑 `migrate` + `validate`，然后把 `app.env` 的 `SPRING_DATASOURCE_URL` 切到新库并重启，readiness UP 后再谈清理验收库。

清理验收库属于单独授权的操作，不在本目录脚本的范围内。

## 5. 口令纪律

- 口令只出现在：环境变量、`app.env`（600）、`flyway.conf`（600）、`/root/.vs-secrets/*.pw`（600）。
- **不进** argv（psql 的口令参数、redis-cli 的 `-a` 参数都不许用；`PGPASSWORD` 走环境变量）、**不进**日志、**不进**仓库。
- 生产守卫（`ProductionSecretsGuard`）在创建任何业务 Bean 之前校验：缺失、空白、开发回退值、占位符（含未替换的 `<...>` 模板）一律拒绝启动，错误只给键名。

## 6. 改完这些文件要跑什么

```bash
python -I scripts/tests/test-deploy-templates.py     # 模板与代码的一致性（秒级）
python -I scripts/tests/test-production-db-init.py   # 独立空库初始化（Docker PG 18.7 隔离环境）
```
