# VisionSpace 阿里云生产部署手册

- 日期：2026-11-24（2026-11-25 改为 PostgreSQL 18 方案；2026-11-26 起部署件改用仓库内 `deploy/` 模板）
- 目标服务器：阿里云 ECS `121.199.77.168`（SSH：`root@121.199.77.168`，密钥 `~/.ssh/aliyun.pem`）
- 域名：`visionspace.space` / `www.visionspace.space`（阿里云 DNS，A 记录 → 121.199.77.168，TTL 10min）
- 版本：1.1.0（首次上线；MySQL → PostgreSQL 18 迁移后）
- **部署模板在仓库里**：`deploy/`（环境变量、systemd、nginx、PG 参数、初始化与自检脚本）。
  本手册讲的是「为什么这么做」，模板讲的是「具体写成什么样」；两者不一致时以 `deploy/` 为准，
  并由 `scripts/tests/test-deploy-templates.py` 在 CI 里盯着。长期契约见
  `docs/adr/0005-production-deployment-contract.md`。

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

2026-11-25 实机只读预检（计划附录 D）实测：这台机器是 Ubuntu **26.04**，自带源已有 `postgresql-18`，但只有 **18.6**；验收与 CI 用的是 `postgres:18.7`。下面仍走 PGDG 官方仓库，把实机补丁版与 CI 对齐到 18.7：

```bash
/usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y
DEBIAN_FRONTEND=noninteractive apt-get install -y postgresql-18
psql --version   # 18.x（应为 18.7；只有 18.6 时见下）

> PGDG 不可达时的退路：`apt-get install -y postgresql-18` 直接用 Ubuntu 26.04 自带源的 **18.6**。同一主版本，本基线用到的特性没有差异，但要在计划附录 D.3 登记「实机 18.6 / CI 18.7」这条补丁版差异，别让后来的人以为两边完全同版。

# 1.7G 内存的低资源参数（对应计划 §5.9）；模板见 deploy/postgresql-production.conf
install -m 644 deploy/postgresql-production.conf /etc/postgresql/18/main/conf.d/vision-space.conf
systemctl enable --now postgresql
```

> `conf.d` 片段与 `postgresql.conf` 追加等价，二选一，别写两遍（后者覆盖前者）。
> 片段里的数值来自实机验收：shared_buffers 128MB / work_mem 2MB / maintenance_work_mem 32MB /
> max_connections 20 / timezone Asia/Shanghai。

角色与库：**迁移角色管 DDL，运行角色只做 DML**。生产库用**独立空库**（默认 `vision_space_prod`），
验收库 `vision_space` 留着验收数据，不直接转正。建库交给仓库里的脚本，它只新建、绝不覆盖既有库：

```bash
# 角色口令用 \password 交互设置（客户端算哈希，不进 shell 历史、不进 argv）
sudo -u postgres psql -c "CREATE ROLE vision_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE"
sudo -u postgres psql -c "CREATE ROLE vision_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT"
sudo -u postgres psql -c '\password vision_migrator'
sudo -u postgres psql -c '\password vision_app'

# 独立空库：角色已存在时脚本不动它们的口令；目标库已存在时脚本直接拒绝
sudo VS_DB_NAME=vision_space_prod ./deploy/init-production-db.sh
```

> 脚本做四件事：建库、`REVOKE CREATE ON SCHEMA public FROM PUBLIC`、`GRANT USAGE ... TO vision_app`、
> 装 `pgcrypto`、设库时区。它**不建业务表**——建表只有 Flyway 一个入口。

建表只走 Flyway（部署包携带 Flyway CLI 13.13.0 与迁移目录）：

```bash
install -d -m 755 /opt/visionspace/flyway /opt/visionspace/migrations
# 解压 flyway-commandline-13.13.0 到 /opt/visionspace/flyway
# 上传 src/main/resources/db/migration/V1__baseline.sql 到 /opt/visionspace/migrations
cat >/opt/visionspace/flyway.conf <<'EOF'
flyway.url=jdbc:postgresql://127.0.0.1:5432/vision_space_prod
flyway.user=vision_migrator
flyway.locations=filesystem:/opt/visionspace/migrations
flyway.validateOnMigrate=true
flyway.cleanDisabled=true
flyway.outOfOrder=false
flyway.encoding=UTF-8
EOF
chmod 600 /opt/visionspace/flyway.conf

# migrate + validate（口令只经环境变量，不进 argv、不进日志）
read -rs -p 'vision_migrator password: ' FLYWAY_PASSWORD; export FLYWAY_PASSWORD
VS_DB_NAME=vision_space_prod ./deploy/flyway-migrate.sh
unset FLYWAY_PASSWORD

# 期望 31 张业务表 + flyway_schema_history
sudo -u postgres psql -d vision_space_prod -tAc \
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

`/opt/visionspace/app.env`（`chmod 600`，只有 root 可读；systemd 以 root 读取后降权运行）。
模板在 `deploy/app.env.example`，**逐项替换 `<...>` 占位**：

```bash
install -m 600 deploy/app.env.example /opt/visionspace/app.env
vi /opt/visionspace/app.env
```

模板里必须改对的几项（2026-11-25 实机踩过的坑）：

| 键 | 值 | 踩坑后果 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | 指向独立空库 `vision_space_prod` | 指到验收库就把验收数据带上线了 |
| `SERVER_ADDRESS` | `127.0.0.1` | 不写就绑 `*:8081`，纵深防御缺口 |
| `SPRING_AI_OPENAI_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `application-prod.yml` 已内建同值默认值；显式写出来是为了「部署意图写在部署文件里」，换供应商时一眼看得出 |
| `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` | `qwen-plus` | 同上，内建默认值已是 `qwen-plus`（Spring AI 的默认 `gpt-4o-mini` 供应商会直接报错） |
| `SPRING_MVC_ASYNC_REQUEST_TIMEOUT` | `300000` | 默认 30 秒会把「首帧还没到」的会话掐断（**止血兜底**，根因见 WP-02） |
| `SPRING_DATA_REDIS_HOST` | `vision-redis` | 写 `127.0.0.1`/`localhost` 会被守卫判为开发回退值，直接拒绝启动 |

小程序登录还需要（可后补，缺失不影响启动）：

```
VISION_SPACE_WX_APPID=<小程序 appid>
VISION_SPACE_WX_SECRET=<小程序 secret>
```

### 3.7 systemd

单元文件在 `deploy/visionspace.service`：

```bash
install -m 644 deploy/visionspace.service /etc/systemd/system/visionspace.service
systemctl daemon-reload
systemctl enable visionspace
```

> 单元里的 JVM 参数按 1.7 GB 实测基线设定（`-Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m`，
> 实测空闲 RSS 431 MB、冷启动 24 秒）；jar 名用不带版本号的软链 `/opt/visionspace/app/vision-space.jar`，
> 避免每次发版都改 unit。

### 3.8 nginx

站点配置在 `deploy/nginx-visionspace.conf`（相对仓库根目录的旧 `visionspace.conf`，
日志路径改成系统默认、静态根目录改成 `/opt/visionspace/dist`，并把 443 段留成注释，
等 certbot 签发后启用）：

```bash
install -m 644 deploy/nginx-visionspace.conf /etc/nginx/conf.d/visionspace.conf
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
read -rs -p "运行角色口令: " PGPASSWORD && echo
PGPASSWORD="$PGPASSWORD" psql -h 127.0.0.1 -U vision_app -d vision_space_prod -tAc 'SELECT 1'
unset PGPASSWORD
```

**一键自检**（监听面 / readiness / 运行角色最小权限 / 业务表数量 / Flyway 状态）：

```bash
read -rs -p "迁移角色口令: " FLYWAY_PASSWORD && echo
export FLYWAY_PASSWORD
VS_DB_NAME=vision_space_prod ./deploy/postflight-check.sh
```

业务表数量的期望值是从 `/opt/visionspace/migrations` 里的迁移脚本数出来的，
加了新的迁移不用改自检脚本。

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
| A：首次真实数据之前 | 库里只有种子/测试数据 | 停应用 → 回退代码 → `DROP DATABASE vision_space_prod` → 按 3.3 重建（Flyway 重放基线）→ 起旧版 |
| B：首次真实数据之后 | 出现第一个真实注册/上传/行为事件 | **禁止 drop 生产库**。回滚 = 从最近 `pg_dump -Fc` 恢复到新库 + 切连接串，或前向修复；先在预发演练恢复 |

- 切换点（首次真实写入）要写进发布记录，注明时间；
- 每次变更前备份，每日备份，保留 7 天：

```bash
install -d -m 700 /opt/visionspace/backup
# 生产库备份：用仓库里的备份脚本（pg_dump -Fc，产物 600，口令走环境变量不进 argv）
read -rs -p "运行角色口令: " PGPASSWORD && echo
export PGPASSWORD
VS_DB_NAME=vision_space_prod ./deploy/backup-db.sh
unset PGPASSWORD
# 保留期只清生产库这一种文件名；验收库备份是 vision_space-<时间>.dump，长期保留，不进这条清理
find /opt/visionspace/backup -name 'vision_space_prod-*.dump' -mtime +7 -delete

# 恢复演练（新库，不动生产）
sudo -u postgres createdb -O vision_migrator vision_space_restore
sudo -u postgres pg_restore -d vision_space_restore /opt/visionspace/backup/<file>.dump
```

> 每日备份与保留策略、异地副本与失败告警属于 1.1.0 生产就绪计划的 WP-03，需要单独授权后配置；
> 上面这段目前只是手工备份的写法。
> 验收库 `vision_space` 的备份**长期保留**，不随保留期清理（它是验收期证据与回退锚点）。

- Flyway 只前向，不写 down migration；`cleanDisabled=true`；
- MySQL 时代遗留的 `sql/migrations/*-rollback.sql` 已归档，不属于 PostgreSQL 回滚路径。

## 七、风险与备注

| 风险 | 处理 |
|---|---|
| 1.7G 内存 | PG 128M shared_buffers + JVM 384M 堆 + Redis + nginx ≈ 1.2G，靠 2G swap 兜底；按计划 §5.9 做低资源实测，不足再升 4G |
| PG 18 装不上 | 2026-11-25 预检：Ubuntu 26.04 自带源已有 `postgresql-18`（18.6），PGDG 给 18.7；PGDG 不可达就退到 18.6 并登记补丁版差异（计划附录 D.3） |
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

> 本节是 **2026-11-25 M4 验收的历史记录**，里面的库名 `vision_space` 指当时的验收库
> （现已由 `vision_space_prod` 独立空库接手生产，见 §3.3 与 §4）。
> 之后的备份恢复演练按 §6.1 在生产库上做，库名写 `vision_space_prod`。

> 2026-11-25 已做过一次**只读**预检（无安装、无写入），机器状态、apt 可得版本、缺失前置条件记在计划附录 D。当时 swap 尚未创建，`.1` 之前先执行 §3.1。安全组与 ICP 备案本机测不出来（代理与 DNS 劫持），仍需在控制台确认。

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
