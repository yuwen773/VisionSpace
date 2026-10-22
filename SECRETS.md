# #28 生产配置与存储密钥止血

工单：[28](https://github.com/yuwen773/VisionSpace/issues/28)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。

本记录只覆盖源码与隔离环境验证。历史凭据轮换、真实存储凭据替换与生产部署属人工关卡，见文末。

## 一、生产秘密外置（02.1 / 02.2）

| 位置 | 处理 |
| --- | --- |
| `application-prod.yml` | 原先硬编码的数据库口令、内网 IP、Redis 口令全部改为 `${ENV}` 占位；文件本身不再含任何可用的连接信息或密钥。 |
| `application.yml` | 开发默认值改为环境变量回退（`${MYSQL_PASSWORD:123456}`、`${REDIS_PASSWORD:}`、`${VISION_SPACE_DEFAULT_PASSWORD:}`）。 |
| `.env.example` | 仅含占位说明与生产必需项清单，不含真实值。 |
| 镜像 | `pom.xml` 的资源过滤排除 `application-local.yml` / `application-server.yml`，`.dockerignore` 同步排除；这两个未跟踪文件含真实密钥，此前会被打进 jar 的 `BOOT-INF/classes/`。`docker-compose.yml` 已补齐全部必需项的变量透传。 |
| 日志 | `application-prod.yml` 设 `mybatis-plus.configuration.log-impl: NoLoggingImpl`。默认的 `StdOutImpl` 会把 `accessKey` / `secretKey` 作为 SQL 参数原样打印到生产 stdout。 |

启动守卫 `ProductionSecretsGuard` 作为 `ApplicationContextInitializer` 挂在 `SpringApplication` 上，在创建任何业务 Bean 之前生效：

- 只有 active profile 含 `prod` / `production` 时才检查；`local`、`acceptance` 不受影响。
- 11 个必需项逐一校验：缺失、空白、**开发期回退值**、**占位文本**都会失败。新增覆盖 `spring.ai.openai.api-key` 与 `aliYunAi.apiKey`——两者都有 `@Value` 或自动配置依赖，原先未纳入守卫，缺项会以无关错误崩溃而非 `BLOCKED_ENV`。
- 错误信息只含属性名与原因，不含属性值。

必需项：`spring.datasource.url`、`spring.datasource.username`、`spring.datasource.password`、`spring.data.redis.host`、`spring.data.redis.password`、`spring.ai.dashscope.api-key`、`spring.ai.openai.api-key`、`aliYunAi.apiKey`、`vision-space.pexels.api-key`、`vision-space.vision-recognition.api-key`、`vision-space.security.default-password`。

占位判定同时覆盖 `.env.example` 实际使用的值（`your_db_password`、`your-redis-host`、`change_me_before_production` 等），以 `your_` / `your-` 开头或含 `change_me` 的值一律拒绝——否则照抄示例文件并设 `prod` 就能蒙混过关。

失败示例（不含任何值）：

```text
BLOCKED_ENV: production secrets are not configured: spring.datasource.url (missing),
spring.datasource.username (missing), ... vision-space.security.default-password (missing)
```

## 二、存储配置零密钥响应（03.1 / 03.2 / 03.3）

**修复的既有缺陷：** `/storage/config/list` 原先没有任何鉴权注解，匿名请求即可列出全部配置，且 `StorageConfigVO` 直接携带 `accessKey` / `secretKey` 原值。本次改动前，未登录用户可读到真实存储密钥。

| 项 | 改动 |
| --- | --- |
| 读取授权 | `/storage/config/list` 增加 `@AuthCheck(mustRole = "admin")`；其余写接口原有校验保持不变。 |
| 返回模型 | `StorageConfigVO` **删除** `accessKey` / `secretKey` 字段，改为 `accessKeyMasked`、`secretKeyMasked`、`hasAccessKey`、`hasSecretKey`。字段不存在，误用全量拷贝也无法序列化真实密钥。 |
| 映射方式 | `listStorageConfigVO` 改为逐字段赋值，不再整表 `BeanUtils.copyProperties`。 |
| 未修改语义 | `StorageCredentialMasker.isReplacement`：`null`、空白、`******` 一律视为"不修改"，保留库中原值；其他值才替换。新建配置时把 `******` 当密钥提交会被拒绝（`40000`），不会把遮蔽值写成真实密钥。 |
| UI | 编辑弹窗不再把密钥填回表单；已配置时提示"已配置，留空表示不修改"，并说明只有新值才会替换。留空字段在提交前被移除，不会以空值覆盖。 |

日志边界：`StorageCredentialMasker.describe` 只输出长度与首尾各 2 字符，供排查使用；`FileStorageInit` 现有日志只打印平台名，不含凭据。

## 三、脱敏秘密扫描（02.3）

`scripts/secret-scan.ps1` 已接入 `scripts/acceptance.ps1` 与 CI：

- 候选集 = `git ls-files` + `git ls-files --others --exclude-standard`，被 `.gitignore` 排除的本地配置不在判定范围。
- 规则：私钥块、阿里云 AccessKeyId、AWS AccessKeyId、`sk-` 长密钥、硬编码 password / api-key、Pexels 形态裸令牌。占位文本与测试假值被排除。
- 候选类型扩展至 `*.java`、`*.ts`、`*.sql`；password 规则不再要求词边界，因此 `MYSQL_ROOT_PASSWORD=…` 这类键名也能命中。
- 输出只有 `文件:行号:规则名`，**不打印命中内容**，避免扫描结果本身成为泄漏源。

验证：对修复前的 `HEAD` 导出运行扫描命中 2 处（`application-prod.yml`、`application.yml`）；对当前工作树为 0 处。

## 四、历史暴露（不虚报已解决）

`application-prod.yml` 中的数据库口令与 Redis 口令存在于提交 `4aee335`（PR #15）及之后的历史中，远程仓库同样可见。本次**没有**重写历史、推送清理或轮换线上密码——清洗历史不能替代轮换，且重写需要单独授权。

需要人工处理：

1. 轮换该 MySQL 账号口令与 Redis 口令。
2. 轮换曾出现在 `application-local.yml` 的 DashScope、Pexels、视觉识别密钥（该文件从未被跟踪，但曾在本机存在）。
3. 复核对象存储平台中的既有密钥是否仍有效。
4. 轮换完成后才能认为"历史暴露"闭环；在此之前不得表述为本票已解决。

## 五、运维兼容与安全回退

- **兼容：** 生产仍使用 `SPRING_PROFILES_ACTIVE=prod` + 环境变量，与 `docker-compose.yml` 现有注入方式一致；键名沿用 Spring 标准名，无需改部署脚本。新增项见 `.env.example`。
- **回退：** 若某次发布必须临时回退，只能回退应用镜像，**不能**回退到把这些值写回 `application-prod.yml` 的旧提交——那会重新引入明文密钥。正确的回退是恢复上一版本镜像并保持秘密注入。
- **启动失败的含义：** 看到 `BLOCKED_ENV: production secrets ...` 表示环境注入缺失或仍在使用开发回退值，属于预期保护，不应通过补默认值绕过。

## 六、本次验证记录

2026-10-22，Windows + JDK 23 运行 + Maven 3.9.9 + Node 22.21.0，原生隔离 MySQL 8.0.26 / Redis 3.2.100（Docker engine 本机不可用，容器模式由 CI 覆盖）。

| 验证项 | 结果 |
| --- | --- |
| 后端全量测试 | 102 条，0 失败/错误/跳过，`BUILD SUCCESS`。新增 16 条：`ProductionSecretsGuardTest`(6)、`StorageCredentialMaskerTest`(3)、`HttpStorageConfigSecurityTest`(7)。 |
| 前端 | 类型检查通过，12 条测试通过，构建通过。 |
| 完整验收脚本 | `runner-tests`、`secret-scan`、`isolation`、`backend`、前端四项、`cleanup` 全部 PASS。 |
| 匿名读取存储配置 | 改动前返回 `code:0` 并含真实密钥；改动后返回 `40100`。 |
| 全部写接口拒绝非管理员 | `add` / `delete` / `set_active` / `set_default` / `refresh` 均返回 `40101`，配置行数与密钥不变。 |
| 新建时提交遮蔽值 | 返回 `40000`，库中不存在 `accessKey = '******'` 的行。 |
| 非管理员读取/修改 | 均返回 `40101`，且数据库中的密钥未被改写。 |
| 管理员列表 | 响应不含 `accessKey` / `secretKey` 键，仅 `******` 与 `has*` 布尔；全响应体不含真实值。 |
| 遮蔽值/空值回传 | 数据库密钥保持不变。 |
| 显式替换单个凭据 | 仅提交的字段被替换，另一项保持原值，响应仍为遮蔽。 |
| prod 缺秘密启动 | 退出 1，抛出 `BLOCKED_ENV` 并列出全部缺失项，未创建业务 Bean。 |
| prod 使用开发回退值 | 退出 1，`spring.data.redis.host (development fallback)`。 |
| prod 正确注入 | 独立临时 MySQL/Redis + 全套注入下 `Started VisionSpaceApplication in 26.9s`。 |
| 秘密扫描器有效性 | 对修复前 HEAD 命中 2 处，对当前工作树 0 处（358 个候选文件）。 |
| jar 内容 | 重建后 `BOOT-INF/classes/` 不再包含 `application-local.yml` / `application-server.yml`。 |

未验证：Docker 容器模式下的 prod 启动、真实云存储与真实模型调用、生产部署。

## 七、遗留与边界

- 本票**未**重写 git 历史，**未**轮换任何线上凭据，**未**推送或部署。
- 存储配置的"候选验证后再切换"（双实例生效确认、失败保留旧快照）属 D20 / 工单 #47 范围，本票只处理密钥外置与响应脱敏。
- 管理表单的浏览器行为（空白 vs 显式替换的交互）没有组件级测试；服务端契约由完整 HTTP 覆盖，UI 逻辑靠类型检查与人工走查。这是 03.4 的已知缺口。
- 守卫只识别 `prod` / `production`。写成 `prd` 之类的拼写错误会绕过检查并回退到开发默认值；这一点依赖部署清单，未在代码层强制。
- 分支保护未配置，门禁会跑但不强制拦截合并。
