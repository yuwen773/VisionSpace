# VisionSpace：MySQL 8 → PostgreSQL 18 迁移计划（R1 修订版）

- 修订：2026-11-24 R1，按 P0×4 + P1×9 闭合
- 状态：待 M0 基线回填后进入 M1
- 约束：未经授权不合并 PR、不发布 1.1.0、不部署生产

## Context

首次正式上线尚未发生，生产库为空（`sql/create_vision_space.sql` 只有 31 张表 schema 与 1 行 `storage_switch_state` 种子）。在首次上线前完成 PostgreSQL 迁移，避免上线后迁移真实用户、图片和统计事件。

本轮只迁移业务关系库；Chroma → pgvector 留到下一轮。Redis 保留。迁移完成后以 **1.1.0** 首次上线。

## 已确定决策（含 R1 新增）

| 决策 | 结论 |
|---|---|
| 时机 / 范围 | 先迁移再上线；只换业务库，pgvector 下一轮 |
| 兼容 | 硬切单一 PG；MySQL 只作迁移期对照，不长期双跑 |
| 命名 | 全库 snake_case；Java / JSON / 前端契约保持 camelCase |
| 历史迁移 | 42 个 MySQL 迁移不移植，归档；生成单一 PG 基线 |
| 大小写 | 账号 CI（`lower()` 唯一索引）；openid CS；其余文本唯一键按“不透明 ID/哈希一律 CS”逐个确认（见 §5.2） |
| 表名 | `user` → `app_user` |
| 更新时间 | 复用一个触发器函数，只绑定原本有 `ON UPDATE CURRENT_TIMESTAMP` 的列 |
| 映射 | 开启 `map-underscore-to-camel-case: true` |
| 测试库 | 统一 Docker `postgres:18`；**固定补丁版 `18.7`** |
| PG 数据卷 | **挂载 `/var/lib/postgresql`**；镜像内 `PGDATA=/var/lib/postgresql/18/docker`（18 起路径版本化，旧路径 `/var/lib/postgresql/data` 不再存在） |
| 时间 | `timestamp(0)` 无时区；生产 `Asia/Shanghai`，验收 `UTC`；库、JDBC 会话、JVM 三者同区 |
| tinyint | 全部 `smallint`，实体保持 `Integer`，逻辑删除 0/1 不动 |
| 迁移机制 | Flyway，基线 `V1__baseline.sql`；**唯一建表入口**；部署时显式执行，启动不自动迁移 |
| Flyway 版本 | **13.13.0**（Boot 3.5.16 BOM 管理的 11.7.2 最高只支持 PG17，必须覆盖）；`flyway-database-postgresql` 同版本 |
| 角色 | 迁移角色 `vision_migrator`（DDL/owner）与运行角色 `vision_app`（仅 DML）分离；CI 用真实低权账号 |
| 事务 | 显式 READ COMMITTED；禁止“捕获数据库异常后继续用同一事务”；行锁统一顺序 |
| 切换 | 单一原子 PR；合并后 CI 只跑 PG |
| 回滚 | 首次真实生产数据之前可弃库重建；之后只允许备份恢复/前向修复，禁止 drop 生产库 |

## 0. M0 基线（先于任何代码改动）

在未改代码的 `main@7545610` 主检出手工跑一次现有 MySQL 验收，结果写入 `target/acceptance/summary.json` 与本计划附录。

```bash
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1
```

- 记录：后端测试数/失败数、前端关卡、接口契约关卡、退出码、运行提交。
- 通过标准：退出 0；后端 846 项、0 失败/错误/跳过；前端全部 PASS。
- 该结果是对照基线。后续任何“测试变绿”都必须与它做数量与用例级对照，不能以删除测试取得。

## 1. P0-1：PG 18 镜像与持久化

### 1.1 正确路径

官方 `postgres:18` 起数据目录版本化：

- 镜像声明 `VOLUME /var/lib/postgresql`；
- 默认 `PGDATA=/var/lib/postgresql/18/docker`；
- 旧路径 `/var/lib/postgresql/data` 在 18 镜像中不存在，按旧路径挂载会启动失败。

因此 compose、验收脚本、CI、部署手册统一：

```yaml
volumes:
  - pg-data:/var/lib/postgresql
```

固定镜像 `postgres:18.7`（当前 18.x 最新补丁；验收与 CI 同版）。

### 1.2 容器重建持久化验证（新增 runner test）

`scripts/tests/test-postgres-persistence.ps1`：

1. 建命名卷，起容器，建表并插入一行；
2. `docker rm -f` 销毁容器（保留卷）；
3. 用同一卷、同一镜像重建容器；
4. 用运行角色执行 `SELECT` 校验数据仍在；
5. 断言卷标签与 run-id 清理策略符合 `docker-test-harness.psm1`（命名卷 + `vs.*` 标签），不得使用匿名卷或 prune。

该测试纳入 `srf-matrix.json` 的 runner tests，PR 必跑。

## 2. P0-2：Schema 初始化只走 Flyway

### 2.1 唯一入口

业务表只能由 Flyway 创建。禁止：

- `psql` 导入 `V1__baseline.sql` 或任何业务 DDL；
- Docker init 脚本建业务表；
- 应用启动自动迁移（`spring.flyway.enabled=false`，且运行时不引入 Spring Boot Flyway starter）。

Docker init / 部署初始化脚本**只允许**做四件事：建角色、建库、装扩展、写库级注释（验收标记）。这些不是业务表。

### 2.2 目录与版本

| 项 | 值 |
|---|---|
| 迁移目录 | `src/main/resources/db/migration` |
| 基线 | `V1__baseline.sql` |
| 后续 | `V2__*.sql`，只增不改 |
| 版本 | Flyway **13.13.0**（`flyway-maven-plugin` + `flyway-database-postgresql`） |
| PG18 兼容 | Redgate 支持矩阵已列 PG 12–18；13.x 为当前支持线 |
| 运行时 | 无 Flyway 依赖；`spring.flyway.enabled: false` 仅作显式声明 |
| 配置 | `validateOnMigrate=true`、`cleanDisabled=true`、`outOfOrder=false`、UTF-8 |
| 历史表 | `public.flyway_schema_history`，归 `vision_migrator` 所有 |

`pom.xml` 显式覆盖版本，并注释原因（Boot 3.5.16 管理 11.7.2，不支持 PG18）：

```xml
<flyway.version>13.13.0</flyway.version>
```

插件声明在 `<plugins>`（不绑定生命周期，只供显式调用；`pluginManagement` 同样可行，实际按 `<plugins>` 落地），`flyway-database-postgresql` 作为插件依赖。CI 与本地用：

```bash
mvn -N org.flywaydb:flyway-maven-plugin:13.13.0:migrate \
  -Dflyway.url="$FLYWAY_URL" -Dflyway.user="$FLYWAY_USER" -Dflyway.password="$FLYWAY_PASSWORD"
```

### 2.3 部署时显式执行

生产不装 Maven。部署包携带：

- Flyway CLI `13.13.0`（固定版本，解压到 `/opt/visionspace/flyway`）；
- 迁移目录 `src/main/resources/db/migration` → `/opt/visionspace/migrations`；
- `/opt/visionspace/flyway.conf`（600，放 URL/用户，口令走 `FLYWAY_PASSWORD` 环境变量）。

发布顺序：

1. `pg_dump -Fc` 备份（非首次部署时）；
2. `flyway migrate`；
3. `flyway validate`（失败即中止，不重启应用）；
4. 替换 jar / 重启 systemd；
5. readiness 检查（§7.5）。

### 2.4 验收标记不建表

`IsolatedApplicationInitializer` 现用 `acceptance_instance` 表做隔离标记。PG 下改为：

- runner 建库后执行 `COMMENT ON DATABASE <db> IS 'vs-acceptance-<nonce>'`；
- 初始化器用 `SELECT current_database(), current_user, shobj_description(oid,'pg_database') FROM pg_database WHERE datname=current_database()` 校验 nonce、库名与运行角色。

这样验收不需要在 Flyway 之外建任何表。

## 3. P0-3：事务失败语义、隔离级别、并发与行锁

### 3.1 默认隔离级别

MySQL 默认 REPEATABLE READ，PG 默认 READ COMMITTED。切换后语义变化，必须显式固定：

- Hikari 连接池设置 `transaction-isolation: TRANSACTION_READ_COMMITTED`（启动即生效，不依赖数据库默认值）；
- 验收里断言 `SHOW transaction_isolation` = `read committed`；
- 审计所有“先读后写、依赖可重复读”的逻辑：批量统计、配额扣减、授权/隔离、Agent 状态推进。发现依赖处改为条件更新（`UPDATE ... WHERE 旧值`）或显式加锁。

### 3.2 事务内异常后继续执行（PG 特有）

PG 中任何语句报错都会把当前事务置为 aborted，后续语句一律 `25P02 current transaction is aborted`，直到 ROLLBACK 或回滚到 SAVEPOINT。已审计出的风险点：

| 位置 | 现状 | 处理 |
|---|---|---|
| `PictureActionServiceImpl.registerEvent` | `catch (DuplicateKeyException)` 后 `return false`，事务继续提交 | 改 `INSERT ... ON CONFLICT (event_id) DO NOTHING`，用影响行数（0/1）判定重复；不再捕获异常 |
| `PictureActionServiceImpl.doReportAction` 的 `actionMapper.insert` | 同上，且此前已写入 dedupe 行 | 同上；duplicate 分支不写库、直接返回 false；PG 下事务不再被 abort |
| `ResourceAccessGrantServiceImpl.grant` / `ResourceIsolationServiceImpl.quarantine` | catch 后**抛出**业务异常 | 安全（事务整体回滚），保留；补测试锁定行为 |
| `UserServiceImpl.upgradeStoredPassword` | `catch (Exception)` 吞掉更新失败 | 确认调用链不在事务内；若被事务方法调用，改为 `REQUIRES_NEW` 或去掉 catch |
| `StorageCleanupServiceImpl.enqueue*` | catch 后重新抛出 | 安全，保留 |

规则：**事务方法内不得捕获数据库异常后继续执行 SQL**。需要“冲突即忽略”的语义，一律用 `ON CONFLICT DO NOTHING` + 影响行数；需要“冲突即翻译”的，捕获后必须抛出。确需局部回滚的，用 SAVEPOINT（`@Transactional(propagation=REQUIRES_NEW)` 或 `TransactionTemplate`），并在测试中证明 savepoint 之后仍可写。

新增测试：

- 同一事务内先触发唯一冲突（DO NOTHING），再执行一条正常 INSERT，提交成功；
- 捕获 `DuplicateKeyException` 后不抛出的旧路径在 PG 上会失败（作为反例，确保以后不会再写回）。

### 3.3 并发幂等与行锁

- 所有 `UPDATE ... ORDER BY ... LIMIT` 改 CTE + `FOR UPDATE SKIP LOCKED`（`UserPictureActionMapper.xml`），并保持“先认领、后处理”的顺序。
- 统一加锁顺序：资源行 → 隔离行 → 统计行；替换/删除/隔离共用同一顺序，测试并发无死锁。
- 幂等键唯一约束必须与 `ON CONFLICT` 目标完全一致；冲突目标写成列清单，不写 `ON CONFLICT DO UPDATE`（无目标）以免吞掉非预期约束。
- 统计事件 claim/lease 的 PG 并发验收（P1，见 §5.5）。

## 4. P0-4：迁移角色与运行角色最小权限

### 4.1 角色

| 角色 | 用途 | 权限 |
|---|---|---|
| `vision_migrator` | Flyway / DDL / 表 owner | 数据库 owner；`CREATE` on schema public；建扩展；建表/索引/触发器 |
| `vision_app` | 应用运行 | `CONNECT`；`USAGE` on schema；表 `SELECT/INSERT/UPDATE/DELETE`；序列 `USAGE, SELECT`；**无 CREATE / 无 owner / 无 DDL** |

初始化（Docker init 或部署脚本，psql 以超级用户执行）：

```sql
CREATE ROLE vision_migrator LOGIN PASSWORD :'migrator_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE vision_app      LOGIN PASSWORD :'app_pw'      NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE DATABASE vision_space OWNER vision_migrator;
\connect vision_space
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO vision_app;
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- trusted，DB owner 可装
ALTER DATABASE vision_space SET timezone = 'Asia/Shanghai';

-- Flyway 以 vision_migrator 建完表后执行：
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO vision_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO vision_app;
ALTER DEFAULT PRIVILEGES FOR ROLE vision_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO vision_app;
ALTER DEFAULT PRIVILEGES FOR ROLE vision_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO vision_app;
```

授权语句放进 `V1__baseline.sql` 末尾（Flyway 以 migrator 身份执行），保证新环境一次到位；`ALTER DEFAULT PRIVILEGES` 覆盖后续 `V2+` 新表。

### 4.2 CI / 验收必须用低权账号

- Flyway 用 `vision_migrator`；
- 应用与全部集成测试用 `vision_app`；
- 新增负例：以 `vision_app` 执行 `CREATE TABLE` 必须失败（`42501 insufficient_privilege`）；
- 负例：以 `vision_app` 执行 `TRUNCATE`/`DROP` 必须失败；
- readiness 与业务查询都以 `vision_app` 身份执行（证明真实运行凭据可用）。

## 5. P1 闭合

### 5.1 时间契约与时间函数

**契约（写入 ADR）：**

- 存储：`timestamp(0)` without time zone，存“墙钟时间”；
- 生产：DB `timezone=Asia/Shanghai`，JVM `TZ=Asia/Shanghai`，systemd 显式设置；
- 验收/CI：DB `timezone=UTC`，`JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`（沿用现状）；
- API：继续输出无偏移的 `yyyy-MM-dd HH:mm:ss` / ISO local，契约不变；
- 禁止在 SQL 中把 `timestamp` 与 `timestamptz` 混用；需要“当前时间”时只用下面三个之一。

**`now()` 审计结论：** MySQL `NOW()` 是语句开始时间；PG `now()` 是**事务**开始时间。现有 40+ 处 `NOW()` 多用于租约、认领、重试退避，必须保持语句级语义：

| 场景 | PG 写法 |
|---|---|
| 业务 SQL 中的 `NOW()` | `statement_timestamp()` |
| 列默认值 `DEFAULT CURRENT_TIMESTAMP` | `DEFAULT statement_timestamp()` |
| 触发器刷新 `update_time` | `statement_timestamp()` |
| 需要“同一事务同一时刻” | 显式 `now()`，并在注释说明 |

禁止使用 `clock_timestamp()` 替代业务时间（除诊断场景）。

### 5.2 全部文本唯一索引与大小写语义

MySQL 侧全部为 `utf8mb4_unicode_ci`（不区分大小写）。PG 侧按“不透明 ID/哈希一律区分大小写，只有账号归一化”处理：

| 表.索引 | 列 | MySQL 语义 | PG 决策 |
|---|---|---|---|
| `app_user.uk_user_account` | `user_account varchar(256)` | CI | **CI**：`CREATE UNIQUE INDEX ... ON app_user (lower(user_account))`；查询/登录用 `lower(user_account)=lower(?)` |
| `app_user.uk_user_wx_open_id` | `wx_open_id varchar(128)` | CI | **CS**（微信 openid 规范区分大小写）；查询用 `=` |
| `agent_session.uk_session` | `session_id varchar(64)` | CI | CS（服务端生成的不透明 ID） |
| `space.uk_space_active_key` | `active_key varchar(64)` | CI | CS（生成键） |
| `storage_config.uk_platform` | `platform varchar(32)` | CI | CS（固定小写枚举；迁移前核对常量） |
| `user_picture_action.uk_event_id` | `event_id varchar(128)` | CI | CS（事件 ID） |
| `mcp_server.uk_mcp_server_code_user` | `mcp_server_code, user_id` | CI | CS；若确认是用户可输入展示码，改为 `lower()` 并同步查询 |
| `storage_cleanup_task.uk_pending_object` | `pending_key char(64)` | CI | CS（哈希键） |
| `agent_run_state.uk_thread` | `thread_id varchar(64)` | CI | CS |
| `agent_action_record.uk_thread_action` | `thread_id, action_id` | CI | CS |
| `vip_redemption_code.uk_code_hash` | `code_hash char(64)` | CI | CS（哈希） |
| `agent_session_attachment.uk_session_resource` | `session_id, resource_id` | CI | CS |
| `agent_legacy_attachment.uk_message_hash` | `message_id, url_hash`（MySQL 前缀 32） | CI | CS；PG 建**整列**索引（PG 不支持前缀索引） |
| `feedback_attachment.uk_legacy_url_hash` | `legacy_url_hash char(64)` | CI | CS（哈希） |
| `resource_access_grant.uk_active_key` | `active_key varchar(128)` | CI | CS（生成键） |
| `resource_isolation.uk_active_key` | `active_key varchar(128)` | CI | CS（生成键） |
| `asset_board_proposal.uk_task` | `task_id varchar(36)` | CI | CS（UUID） |
| `asset_board_proposal.uk_request` | `owner_id, proposal_request_id` | CI | CS（客户端幂等键，精确匹配才正确） |
| `asset_board_proposal.uk_operation` | `owner_id, operation_id` | CI | CS（同上） |

其余唯一索引均为 bigint 数值列，不涉及大小写。迁移期用测试逐项锁定：同一索引插入仅大小写不同的两行，CI 索引必须冲突、CS 索引必须共存。

`ILIKE` 只用于模糊搜索（图片名、用户名、空间名、MCP 名），不做唯一性判定。

### 5.3 `ON CONFLICT` 冲突目标与影响行数

| 位置 | 冲突目标 | MySQL 影响行数 | PG 影响行数 | 调用方是否依赖 |
|---|---|---|---|---|
| `AgentActionRecordMapper.claim` | `(thread_id, user_id, action_id)` | 1/2 | 1（插入或更新都为 1） | 否（读回 claim_token 判定） |
| `AgentRunStateMapper` | `(thread_id)` | 1/0 | 1/0 | 需核对 |
| `AgentSessionAttachmentMapper` | `(session_id, resource_id)` | 1/0 | 1/0 | 需核对 |
| `AgentSessionMapper` | `(session_id)` | 1/0 | 1/0 | 需核对 |
| `PictureIndexTaskMapper` | `(picture_id)` | 1/2 | 1 | 需核对 |
| `StorageCleanupTaskMapper` | `(pending_key)` | 1/0 | 1/0 | 需核对 |
| `UserPreferenceMapper` | `(user_id)` | 1/0 | 1/0 | 需核对 |
| `PictureStatsMapper` | `(picture_id)` | 1/2 | 1 | 不依赖（累加在 SQL 内） |
| `StorageSwitchConfirmMapper` | 主键 `(version, instance_id)` | 1/0 | 1/0 | 不依赖（幂等） |
| `StorageSwitchStateMapper` | 主键 `(id=1)` | 1/0 | 1/0 | 不依赖 |
| `PictureActionEventDedupeMapper` | `(event_id)` | 1/0 | 1/0 | 改 DO NOTHING 后依赖 0/1 |
| `PictureActionServiceImpl` 载荷插入 | `(event_id)` 唯一 | 1/0 | 1/0 | 改 DO NOTHING 后依赖 0/1 |
| `FeedbackAttachmentMapper` | `(legacy_url_hash)` | 1/0 | 1/0 | 需核对 |

规则：

- 冲突目标必须写列清单，且与唯一索引完全一致；
- **不得用 `rowCount==2` 区分“插入/更新”**；PG 只有 0/1。需要区分时用 `RETURNING (xmax = 0) AS inserted` 或先 `SELECT ... FOR UPDATE`；
- `ON CONFLICT DO UPDATE` 的 SET 右侧统一用 `EXCLUDED.*`；PG 所有 SET 表达式看到的是旧行（不是 MySQL 的从左到右），迁移时逐条重写并加注释；
- 触发器在“值未变化”的 DO UPDATE 上也会触发，依赖 §5.4 的“内容未变不刷新”逻辑。

### 5.4 更新时间触发器

```sql
CREATE FUNCTION set_updated_time() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW IS DISTINCT FROM OLD
     AND NEW.update_time IS NOT DISTINCT FROM OLD.update_time THEN
    NEW.update_time := statement_timestamp();
  END IF;
  RETURN NEW;
END $$;
```

只绑定原本具有 `ON UPDATE CURRENT_TIMESTAMP` 的列——归档 MySQL dump 实测为 22 处，PG 侧 22 个触发器一一对应（原稿写 37 是估算，实施时按 dump 核准）；不扩大范围。测试矩阵：普通更新刷新；无变化更新不刷新；显式赋不同时间保留；显式赋旧值（已知不可区分，记录为 ADR 差异）；批量更新逐行刷新；事务回滚后不残留；历史 `update_time` 迁移时原值保留。

### 5.5 统计事件 claim/lease 并发验收

在 PG 上新增或迁移：

- 多连接并发认领同一批 `user_picture_action`，断言每条只被一个 worker 认领（`SKIP LOCKED` + claim_token）；
- 认领后进程“死亡”，租约过期后可被重新认领，且只累计一次；
- 重复事件（同 event_id）不重复累计；
- `picture_stats` 累加在并发下最终值 = 事件数，无丢更新；
- 失败注入：事务回滚后事件回到待处理，不丢不重。

### 5.6 JDBC 映射真实测试

用真实 PG（非 mock）验证：

- `bytea` ↔ `byte[]`（图片二进制、摘要）；
- `GENERATED BY DEFAULT AS IDENTITY` 插入后回填主键；
- `numeric` ↔ `BigDecimal`（统计/金额），标度不丢；
- 聚合结果：`count(*)`→`long`、`sum(int)`→`numeric/long`、`avg`→`numeric`，与实体/VO 类型一致；
- `smallint` ↔ `Integer`（tinyint 迁移后的实体字段）；
- `timestamp(0)` ↔ `LocalDateTime`，秒级精度、无时区偏移；
- `boolean` 不新增（继续 smallint 0/1）。

### 5.7 健康检查

- `pg_isready` 只用于“容器已接受连接”，不作为应用健康结论；
- 应用 readiness 保留 `db` + `redis` 组（`application-prod.yml`），且 DataSource 健康检查以**运行角色**执行真实 `SELECT 1`；
- Docker healthcheck 用 `vision_app` + `PGPASSWORD` 执行 `SELECT 1`（口令经环境变量，不进 argv）；
- 部署 postflight 必须同时检查：
  - `curl http://127.0.0.1:9081/actuator/health/readiness`（真实 DB/Redis）；
  - `curl http://127.0.0.1/api/health`（应用存活）；
- 负例：故意用错误口令启动，readiness 必须 DOWN/503，而不是只报端口通。

### 5.8 旧测试 → 新测试覆盖对照

新增 `scripts/pg-migration-test-map.json`：

```json
{
  "baseRef": "7545610",
  "retired": [
    {"test": "com.yuwen.visionspace.acceptance.MaintenanceWindowDrillTest",
     "replacement": "com.yuwen.visionspace.acceptance.PostgresBaselineConformanceTest",
     "reason": "MySQL 迁移演练脚本退役，改为 PG 基线一致性"}
  ],
  "migrated": [
    {"test": "com.yuwen.visionspace.acceptance.PictureStatsEventMigrationTest",
     "replacement": "com.yuwen.visionspace.acceptance.PictureStatsEventPostgresTest"}
  ]
}
```

`scripts/tests/check-test-coverage-map.py`：

- 以 `baseRef` 列出旧测试类；
- 当前缺失的类必须出现在 `retired` 或 `migrated`，否则 FAIL；
- 新增测试类数量必须 ≥ 退役数量（防止净删除）；
- CI 在 backend gate 前执行，结果写入验收 summary。

9 个迁移演练类逐项登记；51 个 JDBC 测试逐项登记为 migrated；不得以“删掉即通过”。

### 5.9 M4 低资源实机验收（2 vCPU / 1.7 GB）

目标机：阿里云 `121.199.77.168`。参数：

| 项 | 值 |
|---|---|
| JVM | `-Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m` |
| PG | `shared_buffers=128MB`、`work_mem=2MB`、`maintenance_work_mem=32MB`、`max_connections=20` |
| Hikari | `maximum-pool-size=5`、`minimum-idle=1` |
| Redis | `maxmemory 64mb`、`maxmemory-policy noeviction` |
| Swap | 2 GB |

验收步骤：

1. 冷启动到 readiness UP，记录启动耗时与 RSS；
2. 20 并发上传/列表/搜索 10 分钟，记录 p95、错误率、PG 连接数；
3. 一条 Agent SSE 会话完整跑通；
4. 1 小时 soak，采样 JVM RSS、PG RSS、swap 使用、OOM 计数；
5. 通过标准：无 OOM/重启；readiness 全程 UP；swap 使用 < 50%；p95 在约定阈值内（记录实测值作为基线）。

### 5.10 回滚边界（必须写进部署手册）

| 阶段 | 定义 | 允许的回滚 |
|---|---|---|
| A：首次真实数据之前 | 生产库只有种子/测试数据 | 停应用 → 回退代码 → `DROP DATABASE vision_space` → 用归档 MySQL schema 重建 → 起旧版。允许弃库 |
| B：首次真实数据之后 | 出现第一个真实注册/上传/行为事件 | **禁止 drop 生产库**。回滚 = 从最近 `pg_dump -Fc` 恢复到新库 + 切连接串，或前向修复；必须先在预发演练恢复 |

- 切换点在发布记录中显式记录时间与首个真实写入；
- 每次迁移前 `pg_dump -Fc`，每日备份，保留 7 天；
- Flyway 只前向，不写 down migration；`cleanDisabled=true`；
- 恢复演练在 M4 完成一次（备份 → 新库恢复 → 应用指向新库 → readiness UP）。

## 6. 实施顺序

### M0 基线（已完成/进行中）
见 §0，回填结果。

### M1 PG 基线与运行时
- `pom.xml`：MySQL 驱动 → `org.postgresql:postgresql`；Flyway 版本覆盖 13.13.0；移除 Chroma 依赖留待下一轮（本轮仅关闭）。
- `application.yml` / `application-prod.yml`：PG 驱动/URL；`map-underscore-to-camel-case: true`；`spring.flyway.enabled: false`；Hikari `transaction-isolation: TRANSACTION_READ_COMMITTED`；`maximum-pool-size` 按环境。
- `MybatisPlusConfig`：`DbType.POSTGRE_SQL`。
- `V1__baseline.sql`：31 表 snake_case、类型映射、IDENTITY、索引（含 `lower(user_account)`）、触发器、种子、授权语句。
- 归档 `sql/create_vision_space.sql` 与 42 个 MySQL 迁移，ADR 记录。

### M2 SQL 与事务移植
- 9 个 XML + 15 个注解 Mapper：snake_case、去反引号、`ON CONFLICT`（按 §5.3 目标）、`CASE`、`statement_timestamp()`、`make_interval`、`gen_random_uuid()`、`digest`、`ILIKE`。
- 修 §3.2 的捕获后继续；`UPDATE ... ORDER BY ... LIMIT` 改 CTE + SKIP LOCKED。
- Service 字面 SQL：`LIMIT size OFFSET offset`、`to_char`、`date_trunc('week')`、`coalesce`、GROUP BY 重复表达式。
- `ProductionSecretsGuard` 的 JDBC URL 回退值改 PG。

### M3 测试迁移
- 三个隔离器改 PG：Docker `postgres:18.7`、挂 `/var/lib/postgresql`、`pg_isready` + 运行角色 `SELECT 1`、Flyway migrate、库注释标记。
- `IsolationSettings`：`postgresPort`、PG URL、`timezone=UTC`。
- 51 个 JDBC 测试、8 个 `information_schema` 查询改 PG。
- 9 个迁移演练类按 §5.8 退役/替换；触发器矩阵、大小写矩阵、冲突语义、并发 lease、JDBC 映射、角色负例全部补齐。
- CI：acceptance / srf / container-verification 改 PG；超时复核；新增测试覆盖对照 gate。

### M4 部署与实机验收
- `docker-compose.yml`：`postgres:18.7`、卷 `/var/lib/postgresql`、双角色、healthcheck 用 `vision_app`；backend URL 改 PG；Chroma 服务本轮从默认编排移除（或 profile 可选）。
- 更新阿里云手册：安装 `postgresql-18`（本机原生，不用 Docker）、初始化双角色/库、Flyway 显式迁移、`app.env` 改 PG、时区 Asia/Shanghai、备份与恢复演练。
- 按 §5.9 做实机低资源验收。
- 全量 PG 验收通过 → 打 **1.1.0** → 首次上线（需授权）。

## 7. 验证门禁

```bash
# 主检出 / CI
pwsh.exe -NoLogo -NoProfile -File ./scripts/acceptance.ps1

# 工作树内
python -I scripts/drill-isolated-run.py --full
```

必须全绿：

1. `flyway migrate` + `flyway validate` 成功，全新库一次建 31 表 + 种子 + 授权；
2. 应用以 `vision_app` 启动并完成 readiness（真实 `SELECT 1`）；
3. `vision_app` DDL 负例失败；
4. 后端全量 0 失败；测试数量 ≥ M0 基线扣除已登记退役项；
5. 大小写矩阵、冲突语义、触发器矩阵、并发 lease、JDBC 映射测试全部通过；
6. 容器重建持久化测试通过；
7. 前端 lint/format/type-check/test/build 与接口契约关卡通过；
8. 实机低资源验收记录归档；
9. SRF 门禁全绿。

## 8. 非目标

- pgvector / Chroma 替换；JSONB、全文检索、pg_trgm、RLS 的引入；
- 去除 Redis；修改 API / JSON / 前端契约；布尔语义重构；
- 保留 MySQL/PG 双兼容或永久双数据库。

## 9. 风险

| 风险 | 处理 |
|---|---|
| PG 事务 aborted 语义破坏“冲突即忽略” | §3.2 已定位并改写；反例测试锁定 |
| 隔离级别从 RR 变 RC | 显式固定 RC；审计先读后写 |
| `rowCount` 语义差异 | §5.3 禁止用 2 判断更新；必要时 RETURNING |
| 文本唯一键大小写语义变化 | §5.2 全量矩阵 + 测试 |
| Flyway 13 与 Boot 3.5 不集成 | 运行时不引 Flyway；只用 Maven 插件/CLI，版本独立 |
| PG18 卷路径写错导致数据不持久 | §1.1 固定 `/var/lib/postgresql`；重建测试 |
| 1.7 GB 内存不足 | §5.9 参数 + soak；必要时升 4 GB |
| 测试被删除换取全绿 | §5.8 对照清单 + CI gate |
| 触发器无法区分“显式写入旧值” | 测试与 ADR 标注为已知差异 |
| 首次真实数据后误用弃库回滚 | §5.10 阶段 A/B 边界 + 备份恢复演练 |

## 10. 外部事实来源

- PG18 镜像：Docker Hub `postgres` 文档与 docker-library/postgres#1370（VOLUME `/var/lib/postgresql`，PGDATA `/var/lib/postgresql/18/docker`）。
- Flyway 支持矩阵：Redgate Flyway “Supported databases and versions”（PG 12–18）；Maven Central `flyway-maven-plugin` 最新 13.13.0（2026-11-19）。
- Spring Boot 3.5.16 BOM：`flyway.version=11.7.2`、`postgresql.version=42.7.11`。

## 附录 A：M0 基线实测（`main@7545610`，MySQL 8，未改任何迁移代码）

两轮完整跑 `scripts/acceptance.ps1`，结果文件 `target/acceptance/summary.json`（第二轮覆盖第一轮）。

| 关卡 | 第 1 轮（runId `e720c2a5…`） | 第 2 轮（runId `c623dde6…`） |
|---|---|---|
| runner-tests / secret-scan / isolation | PASS | PASS |
| backend | PASS：846 项，0 失败 / 0 错误 / 0 跳过（128 个 surefire 报告类 + 3 个 browser IT） | PASS：同上 846 / 0 / 0 / 0 |
| frontend-install / lint / typecheck / tests | PASS | PASS |
| frontend-format | **FAIL**：7 个文件 | PASS（修复后） |
| frontend-build | PASS | **FAIL**：`fonts.googleapis.cn` 返回 Bad Gateway（外网字体 CDN 不可达） |
| asset-color-calibration / asset-search-browser | PASS | BLOCKED_ENV（上一步失败后按设计跳过） |
| frontend-api-contract / cleanup | PASS | PASS |
| 退出码 | 1 | 1 |

两个红门禁都不是代码缺陷，处理与证据如下。

### A.1 frontend-format：主检出的 CRLF 工作区漂移

- 现象：`prettier --check src/` 报 7 个文件（`api/agentImageUploadController.ts`、`api/mainController.ts`、`components/__tests__/GlobalFooter.spec.ts`、`components/BatchEditPicture.vue`、`styles/code-theme.css`、`types/vue-cropper.d.ts`、`utils/copy-code.ts`）。
- 根因：仓库 `.gitattributes` 规定 `* text=auto eol=lf`，但主检出里这 7 个文件的**磁盘内容**是 CRLF（曾被 Windows 工具写过）。git 的 clean 过滤器把 CRLF 归一化成 LF，于是 `git status` 认为「未修改」，CRLF 就静静留在工作区；Prettier 3 默认 `endOfLine: lf`，判为未格式化。
- 证据：同一提交在全新工作树（LF 检出）里 `prettier --check src/` 全绿；7 个文件的 `git hash-object` 与索引完全一致。
- 修复：`rm` + `git checkout --` 重新检出这 7 个文件（`git checkout-index -f` 不会重写已被判定为未修改的文件），`npm run format:check` 立即全绿；文件内容与提交内容零差异。

### A.2 frontend-build：外网字体 CDN 不可达

- 现象：`vite:css [less] resource 'https://fonts.googleapis.cn/css2?family=Noto+Serif+SC…' gave this Error: Bad Gateway`，构建失败。
- 证据：`curl` 对 `fonts.googleapis.cn` 与 `fonts.googleapis.com` 均返回 000（TLS 失败），同时 npm registry、Maven 仓库正常；第 1 轮同一提交构建 PASS。属外网依赖抖动，与代码无关。
- 处理：等 CDN 恢复后重跑该关卡；迁移分支的最终验收必须包含一次 `frontend-build` PASS。

### A.3 结论

- 后端基线固定为 **846 项、0 失败 / 0 错误 / 0 跳过**；迁移后任何「变绿」都必须与这个数字对照，扣除已登记的退役项（§5.8）。
- 除上述两条环境因素外，13 个关卡全部 PASS，退出码 1 仅由它们造成；两条都在本节留下了可复核的证据与修复动作。

## 附录 B：M1–M3 实施与验证结果（PG 18.7，工作树 `feat/postgresql-migration`）

### B.1 后端全量套件

工作树内用隔离 PG/Redis 跑后端全量（`python -I scripts/drill-isolated-run.py --full`）：

| 轮次 | 测试数 | 失败 | 错误 | 跳过 | 退出码 |
|---|---|---|---|---|---|
| 第 1 轮（`pg-full-suite.log`） | 879 | 52 | 65 | 0 | 1 |
| 第 2 轮（`pg-full-suite2.log`） | 879 | 2 | 0 | 0 | 1 |
| 第 3 轮（`pg-full-suite3.log`） | 879 | 0 | 0 | 0 | 0 |

第 3 轮之后只跑了受影响的子集（`pg-fix-run1/2.log`、`pg-browser-run.log`），全量确认在验收关卡内完成（见 B.4）。

879 相对 M0 基线 846 净增 33：退役 1 个类（`MaintenanceWindowDrillTest`）、迁移登记 51 个类、新增 8 个类（§5.8 对照清单 `OK: 132 个基线测试类全部有下落`）。

### B.2 迁移期发现的真实缺陷（按类别）

**PG 方言与类型严格性**（都是 MySQL 下被静默容忍、PG 直接报错的写法）：

| 位置 | 症状 | 根因与修复 |
|---|---|---|
| `ResourceIsolationServiceImpl.excludeQuarantined` | `column ri.resourceid does not exist`（84 次） | 手写 SQL 里用了 camelCase 列名；改 `ri.resource_id` / `ri.resource_type` |
| `WsHandshakeInterceptor` | 握手 500 | 主键 bigint 却绑定 String → `operator does not exist: bigint = character varying`；先 `Long.parseLong` 再查库，非法 id 拒绝握手 |
| `AssetSearchService.readTask/readBoard/readOperation` | `SELECT FOR SHARE in a read-only transaction`（99 次） | 读路径标了 `readOnly = true` 却做锁定读；PG 不允许，去掉注解并注明原因 |
| `AgentSessionMapper.selectByUserIdCursor` | `could not determine data type of parameter` | `#{beforeId} IS NULL` 为未定型 null；补 `jdbcType=BIGINT` |
| `AgentRunStateMapper.updateIfNewer` | 同上，导致 `saveFullState` FAILED 并连锁 22 个失败 | 同上，`runEpoch` 补 `jdbcType=BIGINT` |
| `SpaceUserServiceImpl.getQueryWrapper` | 浏览器用例 `column "userid" does not exist` | `QueryWrapper` 列名是裸字符串、MP 不做 camel→snake 折叠；改 `space_id`/`user_id`/`space_role` |

最后一条不是单点：它代表一整类「Wrapper 列名字面量」缺陷，已用 `scripts/tests/check-repo-hygiene.py` 的全仓静态扫描永久守住（扫描器先在修复前的行上验证过会报错）。

**Schema 基线**：

- 11 张表漏了 `GENERATED BY DEFAULT AS IDENTITY`（`app_user`、`picture`、`space`、`storage_config` 等），插入报 `null value in column "id"`（13 次）。补齐后 24 张表与 MySQL `AUTO_INCREMENT` 一一对应，`PostgresSchemaConformanceTest` 的 `IDENTITY_TABLES` 同步为 24 项。

**测试自身的 PG 化**（迁移登记，不是业务缺陷）：

- `ChatHistoryCursorIntegrationTest`、`HttpStorageSwitchTest`（2 处）、`HttpResourceQuarantineTest`：INSERT 列表加了 `id` 后占位符差一个，pgjdbc 报「参数索引超过许可范围」。
- `PrivateResourceMigrationTest`：id 区间实参顺序错（`FEEDBACK_ID_BASE_USER`/`+100`）+ 关联子查询里 `url` 被内层同名列遮蔽导致条件恒假、引用重复登记；改为 `numbered.url`。
- `ProductionSecretsGuardTest`：dev JDBC URL 还是 MySQL 的。
- `HttpPublicGalleryTest`：共享库无 `ORDER BY` 时首页覆盖不到本用例的三张图；列表请求按 `authorId` 过滤。

**基础设施与门禁脚本**：

- 测试 PG 默认 `max_connections=100`（MySQL 151），多个 Spring 上下文并发时 `remaining connection slots`（6 次）；drill、`acceptance-environment.psm1`、`acceptance.ps1` 统一起 `-c max_connections=200`。
- `Invoke-AcceptancePostgres` 缺 `-tA`，psql 尾部的 `(1 row)` 混进探活输出，`runner-tests` 误判；补 `-tA` 并让测试取 `$probe[0]`。
- `secret-scan.ps1` 把 psql 的 `PASSWORD :'migrator_pw'` 当硬编码口令（误报）；规则改为 `[:=](?!['"])`，同时在 `verify-secret-scan.py` 里补 `:'var'` 与 `"$VAR"` 两条回归用例。

### B.3 PG 基础设施门禁（本地实测）

| 脚本 | 结果 | 覆盖 |
|---|---|---|
| `scripts/tests/test-postgres-persistence.ps1` | PASS | 命名卷 + `docker rm -f` + 同卷重建后数据仍在；卷带 `vs.*` 标签 |
| `scripts/tests/test-postgres-backup-restore.ps1` | PASS | `pg_dump -Fc` → 新库恢复 → 应用可读 |
| `scripts/tests/test-postgres-init.ps1` | PASS | init 脚本收敛性：只建双角色/库/扩展/库注释，出现 `CREATE TABLE` 即失败；口令经 `psql -v` 传入。**首轮验收时它只被手工执行、没进 `srf-matrix.json`，现已登记到 runner-tests** |

运行角色无 DDL 权限的负例在后端套件里：`PostgresLeastPrivilegeTest` 断言 `vision_app` 执行 `CREATE TABLE` / `TRUNCATE` 得到 `42501 insufficient_privilege`。

### B.4 迁移分支全量验收

`scripts/acceptance.ps1`，两次都是退出码 **0**、14 个关卡全 PASS：

| 轮次 | runId | 后端 |
|---|---|---|
| 迁移完成时 | `46ceb80f0bb34037b6e8d48e21745715` | 879 项，0 失败 / 0 错误 / 0 跳过 |
| 评审修复后 | `5f1b37b3308c450f83159adc2586de5e` | 881 项，0 失败 / 0 错误 / 0 跳过（新增两个累计/回滚用例） |

| 关卡 | 结果 |
|---|---|
| runner-tests / secret-scan / isolation | PASS（runner-tests 含本轮新登记的 `test-postgres-init.ps1`） |
| backend | PASS（含 3 个浏览器 IT） |
| frontend-install / lint / format / typecheck / tests / build | PASS |
| asset-color-calibration / asset-search-browser | PASS |
| frontend-api-contract / cleanup | PASS |

与 M0 基线（846 / 0 / 0 / 0）对照：+35 项，退役与新增逐项登记在 `scripts/pg-migration-test-map.json`。把关方式是 `acceptance.ps1` 的 runner-tests 回合：它除按 `srf-matrix.json` 跑 `.ps1` 外，还会遍历 `scripts/tests/test-*.py` 全部执行，覆盖率对照脚本（输出 `OK: 132 个基线测试类全部有下落…`）就在这一步跑。

### B.5 两轴代码评审（Standards + Spec）与处置

评审在提交 `6a165e2` 上以 `main` 为基点跑了两轴。**真缺陷 3 个，已修**：

| 发现 | 处置 |
|---|---|
| `AdminStatsServiceImpl` / `SpaceAnalyzeServiceImpl` 的 SQL 别名改成 `total_size`，Java 侧仍 `get("totalSize")` → 拆箱 NPE | 两处改读 `total_size`；同类风险用 `check-repo-hygiene.py` 新增规则守住（selectMaps 的键不会 camel 化，按同文件 `as` 别名对齐；先在坏行上验证会报错） |
| 空间分类分析的 `sum(pic_size)` 在整组为空时返回 NULL | 包 `coalesce(..., 0)` |
| `AgentActionLedger` 注释写 `ON CONFLICT (thread_id, user_id, action_id)`，与真实唯一键不符 | 注释改为 `(thread_id, action_id)` |

**证据强度问题，已修**：`test-postgres-init.ps1` 之前只手工跑过、没进 `srf-matrix.json`（现已登记）；`PostgresUniqueCaseSemanticsTest` 的账号用例原本用自写 SELECT 自证大小写不敏感，改为走真实 `userRegister` + `authenticateForPassword`；`PostgresSchemaConformanceTest` 的账号索引断言补上 `CREATE UNIQUE INDEX`（否则非唯一索引也能过）、并删掉一段自相矛盾的过期 javadoc；`HttpResourceQuarantineTest` 里「picture_file 没有自增列」的注释与基线冲突，已改。

**补齐的计划项**：§5.5 的「最终值 = 事件数」与失败注入（`PostgresStatsClaimConcurrencyTest` 新增两个用例：4 线程并发聚合后 `impression_count` 必须恰好等于事件数；外层事务回滚后统计无增量、事件全部退回待处理）；§3.3 的加锁顺序写进 ADR §4 并指明唯一入口 `ResourceLockManager.lockResourceRow`（隔离/删除/替换/编辑租约都先取它）；§5.10 的备份口径（每日一次、保留 7 天、恢复演练）写进 ADR §6；§5.4 的触发器数量按归档 dump 核准为 22（原稿 37 是估算），计划已更正；`sql/README.md` 标明该目录是归档件、任何流程都不执行。

### B.6 仍未完成（不阻塞合并，但上线前要闭）

- M4 低资源排练（§5.9）：本机按 2 vCPU / 1700 MB 参数做一次冷启动 + 备份恢复演练；阿里云实机（`121.199.77.168`）需授权后执行。§7 门禁第 8 条因此未满足。
- §5.7 的「错误口令启动 → readiness DOWN/503」负例没有单独用例：验收上下文里 `IsolatedApplicationInitializer` 会先因库级 nonce 校验失败而中止启动，readiness 根本不可达；现有证据是 `test-container-verification.ps1` 的依赖后到/停库场景断言 readiness 不 UP。
- §5.2 的「同一索引插一对只差大小写的值」行为矩阵只覆盖账号、openid、兑换码哈希三类；其余文本唯一键靠 `PostgresUniqueCaseSemanticsTest` 的索引清单（名称 + 覆盖表达式逐条钉住）结构性保证。
- 合并、打 1.1.0、部署生产：均需显式授权，未执行。
