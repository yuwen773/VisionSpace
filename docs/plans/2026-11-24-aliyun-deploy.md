# VisionSpace 阿里云生产部署手册

- 日期：2026-11-24（2026-11-25 改为 PostgreSQL 18 方案）
- 目标服务器：阿里云 ECS `121.199.77.168`（SSH：`root@121.199.77.168`，密钥 `~/.ssh/aliyun.pem`）
- 域名：`visionspace.space` / `www.visionspace.space`（阿里云 DNS，A 记录 → 121.199.77.168，TTL 10min）
- 版本：1.1.0（首次上线；MySQL → PostgreSQL 18 迁移后）

## 一、目标拓扑

```
浏览器 / 小程序
      │ https://visionspace.space
      ▼
    nginx :443 ──静态──> /opt/visionspace/dist
      │ /api, SSE, WSS
      ▼
  Spring Boot :8081 (systemd visionspace)
      ├── PostgreSQL 18  127.0.0.1:5432（vision_migrator / vision_app 双角色）
      └── Redis 8    127.0.0.1:6379
```

- 单机原生部署，不使用 Docker（机器只有 1.7G 内存）
- Chroma / 向量检索关闭：`SPRING_AI_VECTORSTORE_TYPE=none`、`SPRING_AI_MODEL_EMBEDDING_TEXT=none`；pgvector 留到下一轮
- 用 `systemd` 托管 jar，nginx 终止 TLS，certbot 签证书
- 业务表只由 Flyway 创建；应用启动不做任何 DDL（`spring.flyway.enabled=false`）

## 二、前置条件

| 项 | 状态 |
|---|---|
| DNS `@` / `www` A → 121.199.77.168 | 已配置（2026-11-24 21:58 更新，TTL 10min） |
| ICP 备案（大陆 ECS 80/443） | **需确认**；未备案可能被运营商拦截 |
| 安全组放行 22 / 80 / 443 | **需确认** |
| 安全组禁止 5432 / 6379 / 8081 对公网 | **需确认** |
| 本地 `.env` 提供 11 项必填密钥 | 已有，但缺 `VISION_SPACE_WX_APPID` / `VISION_SPACE_WX_SECRET` |

`ProductionSecretsGuard` 在生产 profile 下强制要求：

```
spring.datasource.url / username / password
spring.data.redis.host / password
spring.ai.dashscope.api-key
spring.ai.openai.api-key
aliYunAi.apiKey
vision-space.pexels.api-key
vision-space.vision-recognition.api-key
vision-space.security.default-password
```

> 关键：生产 profile 下 `spring.data.redis.host` **不能是 `localhost` / `127.0.0.1` / `::1`**，否则启动直接 `BLOCKED_ENV`。本机部署用 `/etc/hosts` 别名 `vision-redis` 指向 127.0.0.1（见 3.5）。

## 三、安装与配置（root）

### 3.1 swap（1.7G 内存必须）

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile
mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
free -h
```

### 3.2 软件包

```bash
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  openjdk-21-jre-headless nginx redis-server certbot python3-certbot-nginx postgresql-common
systemctl enable --now nginx redis-server
java -version   # 21.x
```

### 3.3 PostgreSQL 18

Ubuntu 自带源通常不是 18，用 PGDG 官方仓库（与验收/CI 的 `postgres:18.7` 对齐到同一主版本）：

```bash
/usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y
DEBIAN_FRONTEND=noninteractive apt-get install -y postgresql-18
psql --version   # 18.x

# 1.7G 内存的低资源参数（对应计划 §5.9）
cat >>/etc/postgresql/18/main/postgresql.conf <<'EOF'
listen_addresses = '127.0.0.1'
shared_buffers = 128MB
work_mem = 2MB
maintenance_work_mem = 32MB
max_connections = 20
timezone = 'Asia/Shanghai'
log_timezone = 'Asia/Shanghai'
EOF
systemctl enable --now postgresql
```

角色与库：**迁移角色管 DDL，运行角色只做 DML**。口令用 `\password` 交互设置（客户端算哈希，不进 shell 历史、不进 argv）：

```bash
sudo -u postgres psql -c "CREATE ROLE vision_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE"
sudo -u postgres psql -c "CREATE ROLE vision_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT"
sudo -u postgres psql -c "CREATE DATABASE vision_space OWNER vision_migrator"
sudo -u postgres psql -d vision_space \
  -c "REVOKE CREATE ON SCHEMA public FROM PUBLIC" \
  -c "GRANT USAGE ON SCHEMA public TO vision_app" \
  -c "CREATE EXTENSION IF NOT EXISTS pgcrypto" \
  -c "ALTER DATABASE vision_space SET timezone = 'Asia/Shanghai'"
sudo -u postgres psql -c '\password vision_migrator'
sudo -u postgres psql -c '\password vision_app'
```

建表只走 Flyway（部署包携带 Flyway CLI 13.13.0 与迁移目录）：

```bash
install -d -m 755 /opt/visionspace/flyway /opt/visionspace/migrations
# 解压 flyway-commandline-13.13.0 到 /opt/visionspace/flyway
# 上传 src/main/resources/db/migration/V1__baseline.sql 到 /opt/visionspace/migrations
cat >/opt/visionspace/flyway.conf <<'EOF'
flyway.url=jdbc:postgresql://127.0.0.1:5432/vision_space
flyway.user=vision_migrator
flyway.locations=filesystem:/opt/visionspace/migrations
flyway.validateOnMigrate=true
flyway.cleanDisabled=true
flyway.outOfOrder=false
flyway.encoding=UTF-8
EOF
chmod 600 /opt/visionspace/flyway.conf
read -rs -p 'vision_migrator password: ' FLYWAY_PASSWORD; export FLYWAY_PASSWORD
/opt/visionspace/flyway/flyway -configFiles=/opt/visionspace/flyway.conf migrate
/opt/visionspace/flyway/flyway -configFiles=/opt/visionspace/flyway.conf validate
unset FLYWAY_PASSWORD

# 期望 31 张业务表 + flyway_schema_history
sudo -u postgres psql -d vision_space -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'"
```

### 3.4 Redis 8

```bash
sed -i 's/^# *requirepass .*/requirepass <REDIS_PASSWORD>/' /etc/redis/redis.conf
grep -q '^requirepass ' /etc/redis/redis.conf || echo 'requirepass <REDIS_PASSWORD>' >> /etc/redis/redis.conf
# 只监听回环
sed -i 's/^bind .*/bind 127.0.0.1 ::1/' /etc/redis/redis.conf
systemctl restart redis-server
redis-cli -a '<REDIS_PASSWORD>' ping   # PONG
```

### 3.5 用户、目录与 Redis 主机别名

```bash
useradd --system --home /opt/visionspace --shell /usr/sbin/nologin visionspace || true
mkdir -p /opt/visionspace/{app,dist,logs}
chown -R visionspace:visionspace /opt/visionspace
chown -R www-data:www-data /opt/visionspace/dist

# ProductionSecretsGuard 拒绝 127.0.0.1；用回环别名，不改变实际网络路径
grep -q 'vision-redis' /etc/hosts || echo '127.0.0.1 vision-redis' >> /etc/hosts
getent hosts vision-redis
```

### 3.6 应用环境变量

`/opt/visionspace/app.env`（`chmod 600`，只有 root 可读；systemd 以 root 读取后降权运行）：

```bash
cat >/opt/visionspace/app.env <<'EOF'
SPRING_PROFILES_ACTIVE=prod
# 运行角色只做 DML；迁移角色只在 3.3 的 Flyway 步骤里使用
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/vision_space?ApplicationName=vision-space&sslmode=disable
SPRING_DATASOURCE_USERNAME=vision_app
SPRING_DATASOURCE_PASSWORD=<APP_PASSWORD>
SPRING_DATA_REDIS_HOST=vision-redis
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=<REDIS_PASSWORD>
SPRING_AI_DASHSCOPE_API_KEY=<...>
SPRING_AI_OPENAI_API_KEY=<...>
ALI_YUN_AI_API_KEY=<...>
VISION_SPACE_PEXELS_API_KEY=<...>
VISION_SPACE_VISION_RECOGNITION_API_KEY=<...>
VISION_SPACE_VISION_RECOGNITION_BASE_URL=<...>
VISION_SPACE_VISION_RECOGNITION_MODEL=<...>
VISION_SPACE_DEFAULT_PASSWORD=<至少 8 位，不要用 12345678>
SPRING_AI_VECTORSTORE_TYPE=none
SPRING_AI_MODEL_EMBEDDING_TEXT=none
VISION_SPACE_TRUSTED_ORIGINS=
LOGGING_FILE_NAME=/opt/visionspace/logs/app.log
# 库、JDBC 会话、JVM 三者同区：timestamp(0) 存墙钟，API 输出不带偏移
TZ=Asia/Shanghai
PGTZ=Asia/Shanghai
EOF
chmod 600 /opt/visionspace/app.env
```

小程序登录还需要（可后补，缺失不影响启动）：

```
VISION_SPACE_WX_APPID=<小程序 appid>
VISION_SPACE_WX_SECRET=<小程序 secret>
```

### 3.7 systemd

`/etc/systemd/system/visionspace.service`：

```ini
[Unit]
Description=VisionSpace backend
After=network-online.target postgresql.service redis-server.service
Wants=network-online.target

[Service]
Type=simple
User=visionspace
Group=visionspace
WorkingDirectory=/opt/visionspace/app
EnvironmentFile=/opt/visionspace/app.env
ExecStart=/usr/bin/java -Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m -Duser.timezone=Asia/Shanghai -jar /opt/visionspace/app/vision-space-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable visionspace
```

> jar 名以 `mvn package` 实际产物为准（`target/vision-space-*.jar`）。首次部署后可把它固定成不带版本号的软链，避免每次改 unit。

### 3.8 nginx

`/etc/nginx/conf.d/visionspace.conf`（基于仓库 `visionspace.conf`，日志路径改为系统默认）：

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

map $args $vs_masked_args {
    "~*^(?<vs_pre>.*&|)(?<vs_key>[^&=]*token[^&=]*=)[^&]*(?<vs_post>&.*|)$"  "${vs_pre}${vs_key}***${vs_post}";
    default $args;
}

log_format vs_main '$remote_addr - $remote_user [$time_local] '
                    '"$request_method $uri$is_args$vs_masked_args $server_protocol" '
                    '$status $body_bytes_sent "$http_referer" "$http_user_agent"';

server {
    listen 80;
    server_name visionspace.space www.visionspace.space;

    root /opt/visionspace/dist;
    index index.html;

    access_log /var/log/nginx/visionspace.access.log vs_main;
    error_log  /var/log/nginx/visionspace.error.log;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8081/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

```bash
nginx -t && systemctl reload nginx
```

> 生产配置强制 `sa-token.cookie.secure=true`，**没有 HTTPS 时登录 Cookie 不会回传**。所以必须完成 3.9 再用。

### 3.9 TLS

```bash
certbot --nginx -d visionspace.space -d www.visionspace.space \
  --redirect -m <你的邮箱> --agree-tos -n
systemctl list-timers | grep certbot
```

## 四、构建与发布

部署技能条目已加：`visionspace-aliyun`（`~/.claude/skills/project-deploy/config/servers.json`）。

```bash
# 一次性：主检出装前端依赖
cd D:/Work/code/VisionSpace/frontend && npm ci

# 计划（不连服务器）
cd ~/.claude/skills/project-deploy
bash scripts/deploy.sh visionspace-aliyun --dry-run

# 实发：先 npm run build，再 mvn package -DskipTests，推 dist + jar，重启并探活
bash scripts/deploy.sh visionspace-aliyun --mode full
```

发布脚本会：

- 前端整体交换 `dist/`，旧目录保留为 `dist.bak.<stamp>`
- 后端把旧 jar 备份为 `*.jar.bak.<stamp>`，`systemctl restart visionspace`
- 轮询 `http://127.0.0.1/api/health`（经 nginx）最多 20 秒；失败则报错退出

## 五、上线验证

```bash
curl -I https://visionspace.space
curl -s https://visionspace.space/api/health
# readiness 会以运行角色做真实 SELECT 1 并探 Redis；端口通不等于就绪
curl -s http://127.0.0.1:9081/actuator/health/readiness
systemctl status visionspace --no-pager
journalctl -u visionspace -n 50 --no-pager

# 反向证明运行凭据真的可用（若这里失败，readiness 一定也是 DOWN）
PGPASSWORD=<APP_PASSWORD> psql -h 127.0.0.1 -U vision_app -d vision_space -tAc 'SELECT 1'
```

首次上线做一次负例（做完立即改回）：把 `app.env` 里的 `SPRING_DATASOURCE_PASSWORD` 改成错误值并重启，
`/actuator/health/readiness` 必须是 `DOWN` / 503，而 `systemctl is-active` 仍可能是 active——
端口通、进程在，都不等于数据库可用。改回后重新走一遍本节。

人工检查：

- 注册 / 登录（HTTPS 下 Cookie 为 Secure，登录态可保持）
- 上传图片、颜色检索、空间浏览
- Agent 对话（需要 DashScope key 有效）
- 日志 30 秒内增速正常（`/opt/visionspace/logs/app.log`）
- 小程序：微信后台把 `https://visionspace.space` 加入 request / upload / download 合法域名（必须 HTTPS 且已备案）

## 六、回滚

```bash
# 先看会退到哪一版（会读远端）
bash scripts/rollback.sh visionspace-aliyun "<文件名>" --dry-run
bash scripts/rollback.sh visionspace-aliyun "<文件名>"
```

- 前端：`dist.bak.<stamp>` 目录
- 后端：`vision-space-*.jar.bak.<stamp>`，回滚后自动重启并健康检查

### 6.1 数据库回滚边界（与计划 §5.10 一致）

| 阶段 | 定义 | 允许的回滚 |
|---|---|---|
| A：首次真实数据之前 | 库里只有种子/测试数据 | 停应用 → 回退代码 → `DROP DATABASE vision_space` → 按 3.3 重建（Flyway 重放基线）→ 起旧版 |
| B：首次真实数据之后 | 出现第一个真实注册/上传/行为事件 | **禁止 drop 生产库**。回滚 = 从最近 `pg_dump -Fc` 恢复到新库 + 切连接串，或前向修复；先在预发演练恢复 |

- 切换点（首次真实写入）要写进发布记录，注明时间；
- 每次变更前备份，每日备份，保留 7 天：

```bash
install -d -m 700 /opt/visionspace/backup
sudo -u postgres pg_dump -Fc vision_space > /opt/visionspace/backup/vision_space-$(date +%F-%H%M).dump
find /opt/visionspace/backup -name 'vision_space-*.dump' -mtime +7 -delete

# 恢复演练（新库，不动生产）
sudo -u postgres createdb -O vision_migrator vision_space_restore
sudo -u postgres pg_restore -d vision_space_restore /opt/visionspace/backup/<file>.dump
```

- Flyway 只前向，不写 down migration；`cleanDisabled=true`；
- MySQL 时代遗留的 `sql/migrations/*-rollback.sql` 已归档，不属于 PostgreSQL 回滚路径。

## 七、风险与备注

| 风险 | 处理 |
|---|---|
| 1.7G 内存 | PG 128M shared_buffers + JVM 384M 堆 + Redis + nginx ≈ 1.2G，靠 2G swap 兜底；按计划 §5.9 做低资源实测，不足再升 4G |
| PG 18 装不上 | Ubuntu 默认源版本偏旧，必须走 PGDG 仓库（3.3）；与验收/CI 的 `postgres:18.7` 对齐主版本 |
| 角色配错 | 应用必须连 `vision_app`；`vision_app` 执行 DDL 会被 42501 拒绝（验收有负例）。部署后用 `SELECT current_user` 复核 |
| 时区错位 | 库、JVM（`TZ` / `-Duser.timezone`）、JDBC 会话统一 `Asia/Shanghai`；`timestamp(0)` 不带时区，跨区会整体偏移 |
| 大陆未备案 | 80/443 可能被阻断；上线前确认备案 |
| 缺 `WX_APPID/SECRET` | 小程序登录不可用，主站不受影响 |
| 部署脚本不自动回滚前端 | 失败时按输出用 `rollback.sh` |
| Hermes | 已停用并禁止自启；恢复：`systemctl --user -M hermes@ enable --now hermes-gateway` |
| 向量检索 | 本版关闭（cf. `SPRING_AI_*` = none）；下一轮 Chroma → pgvector，先升内存 |

## 八、M4 低资源实机验收清单（计划 §5.9 / §5.10）

在 2 vCPU / 1.7 GB 的阿里云 ECS 上，按 §3.3 的原生方式装好后，逐条执行并记录数字。
**不要占用 8081 与 nginx**：验收用临时端口，测完停掉测试实例并删测试库。

### 8.1 前置记录

```bash
free -m; nproc; df -h /; swapon --show
systemctl list-units --type=service | grep -E 'postgres|redis|visionspace'
```

### 8.2 数据库与角色

- 按 §3.3 建 `vision_migrator` / `vision_app` 与 `vision_space`；
- Flyway `migrate` + `validate` 均成功，31 张业务表；
- 负例（必须失败）：`sudo -u postgres psql -d vision_space -tAc "SET ROLE vision_app; CREATE TABLE probe_x(id int)"`
  报 `permission denied for schema public`；
- `SHOW shared_buffers; SHOW work_mem; SHOW maintenance_work_mem; SHOW max_connections; SHOW timezone;`
  必须是 128MB / 2MB / 32MB / 20 / Asia/Shanghai。

### 8.3 应用冷启动

- 用 §3.6 的环境变量与 §3.7 的 systemd 参数，但**先临时端口**：
  `java -Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m -Duser.timezone=Asia/Shanghai -jar app.jar --server.port=18081 --management.server.port=19081 --management.endpoints.web.exposure.include=health --management.endpoint.health.probes.enabled=true --management.endpoint.health.group.readiness.include=db,redis`
- 记录：从进程启动到 `curl -s http://127.0.0.1:19081/actuator/health/readiness` 返回 `{"status":"UP"}` 的秒数；
- 记录：`ps -o rss= -p <pid>`（KB）→ 换算 MB；`systemctl status postgresql` / `redis-server` 的 RSS；
- 记录：`SELECT count(*) FROM pg_stat_activity WHERE datname='vision_space'`（应 ≤ 6：池 5 + 管理连接）。

### 8.4 负载

- 20 并发持续 10 分钟打 `POST /api/picture/list/page/vo`，记录请求数、错误率、p50/p95/p99；
- 期间每分钟采样一次 JVM RSS、PG RSS、`pg_stat_activity` 数、`free -m`（看 swap 是否上涨）；
- 通过标准：错误率 0、readiness 全程 UP、swap 使用 < 50%、无 OOM/重启。

### 8.5 备份恢复（§5.10 阶段 B）

```bash
sudo -u postgres pg_dump -Fc -d vision_space -f /tmp/vision_space.dump
sudo -u postgres psql -c "CREATE DATABASE vision_space_restore OWNER vision_migrator"
sudo -u postgres pg_restore -d vision_space_restore /tmp/vision_space.dump
sudo -u postgres psql -d vision_space_restore -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"
```
- 把应用指向 `vision_space_restore`（只改 URL，不改端口），readiness 必须 UP，业务查询要能返回；
- 记录恢复耗时与库体积。

### 8.6 清理

```bash
# 停掉临时实例、删测试库、删 dump
sudo -u postgres psql -c "DROP DATABASE vision_space_restore"
rm -f /tmp/vision_space.dump
```
- 恢复正式库仍指向 `vision_space`；
- 若正式部署尚未进行，本轮到此结束，机器上只留 §3 的 PG/Redis 与角色。

### 8.7 记录归档

把 8.1–8.6 的数字补进迁移计划附录 C（替换「本机模拟」那节），并写明**实机**跑过：
`free -m`、启动秒数、RSS、负载分位、swap 使用、恢复耗时。
