# #39 VIP 权益独立与原子兑换 — 实施与验收记录

工单：[39](https://github.com/yuwen773/VisionSpace/issues/39)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26) D13/T15/A25。
分支：`codex/39-vip-atomic-redemption`，基线：`513fb4f0af5434aab450467b866a10c37d4f0013`。

## 实现

### 后端

- `vip_redemption_code`（sha-256 摘要 + status 0可用/1已用/2作废，`uk_codeHash`）与
  `vip_redemption_record`（流水，`uk_codeId`）两张新表（`sql/create_vision_space.sql`、
  `sql/migrations/2026-10-24-vip-redemption.sql`）。
- `VipRedemptionServiceImpl.redeem`：
  - 核销 = 单条 `UPDATE vip_redemption_code SET status=1 WHERE codeHash=? AND status=0`
    （`VipRedemptionMapper.xml markUsed`）。行锁 + 条件更新 ⇒ 双实例同码恰好一次成功。
  - 核销、用户行 `SELECT ... FOR UPDATE`、权益更新、流水写入同一 `@Transactional`；
    任一步失败整体回滚 ⇒ 失败不吞码。
  - 到期时间 = max(now, 原到期) + 365 天；同人并发多码串行加时不丢。
  - 不写 `userRole`；VIP 独立于管理角色。
- 响应 `VipExchangeResult{status: success|already_used|invalid, vipExpireTime}`；
  `LoginUserVO` 增加 `vipExpireTime`；日志只带摘要前 8 位，禁止输出明文码。
- 删除 `VipCode.java` DTO 与 `biz/vipCode.json`（JAR 内不可写、依赖修改 Classpath 的旧实现）。
- `VipCodeImportTool`（独立 main，不连库）：历史 JSON → 摘要 INSERT；已用→1、
  未用→2（作废）+ 脱敏补发清单（无码值）；`--issue N --issue-out` 生成补发新码
  （明文仅写指定文件）。

### 前端

- `VipSection.vue`：真实登录态（`vipExpireTime > now` 判定 VIP，不再用 userRole）、
  兑换表单、success/already_used/invalid 三态反馈；成功后刷新登录态。
- 生成客户端手工同步（`typings.d.ts` / `userController.ts`）：合并前应跑 `npm run openapi2ts`
  校验（需活后端，见「未验证范围」）。

### 迁移与回滚

- 前向 `2026-10-24-vip-redemption.sql`：仅建两表；码值/摘要不回填，存量码由导入工具处理。
- 回滚 `...-rollback.sql`：幂等补回 `user.vipCode` 兼容列（恒 NULL），**不删表、不改状态行**；
  切换流程要求运行时 `vipCode.json` 置 `[]` ⇒ 旧版本回滚后无可用码，不重新开放已作废码。
- 回滚不推定管理员身份：兑换实现已不再写 `userRole`；旧实现写入的 `vip` 角色行不自动改动，
  `userRole` 的修复属 #63 范围，本票仅保证不再制造。

## 共享文件最小 diff 说明

- `sql/create_vision_space.sql`：追加两张表（+37 行），无既有行改动。
- `scripts/acceptance.ps1`：表计数 15→17（1 行）；新增双实例启动/握手/清理块
  （backend gate 由 `verify` 改为 `test-compile + build-classpath + 启动第二 JVM + verify`）。
- `frontend/src/api/typings.d.ts`、`userController.ts`：生成文件的手工同步，建议合并前重生成。

## 验收证据

### run6（评审修复前，基线证据）
- 基础设施：native-explicit（MySQL 8.0.26 + Redis 3.2.100 本机二进制，
  `-MySqlBin` / `-RedisBin` 模式；本机无 Docker）。
- 门禁：runner-tests / secret-scan / isolation / backend / frontend-install /
  frontend-typecheck / frontend-tests / frontend-build / cleanup 全 PASS
  （backend = 464 tests, 0 errors, 0 failures；VIP 专项 13 个全过）。
- 双实例：VipSecondInstanceMain 以真实第二 JVM 启动，端口经就绪文件握手；
  并发同码恰好一次成功、同人双码并发完整加时（HttpVipRedemptionDualInstanceTest 2 项）。
- JAR 证据：`mvn package` 后 `vision-space-0.0.1-SNAPSHOT.jar` 内无 `biz/`、
  无任何 vipCode 明文条目；VipRedemptionServiceImpl 打入 JAR。
- 日志目录：`C:/Users/yuwen/AppData/Local/Temp/visionspace-acceptance-ded601c2e3c3435fa48e0e5090811231`。

### 评审返工（第二轮审查 513fb4f → 6105bd0，4 项）

1. **[P1] 导入器已用状态判定**：原文本匹配依赖属性顺序与固定空白，
   `hasUsed` 在 `code` 之前会把已用码误判为未用并计入补发。
   改为手写最小 JSON 解析器（字段校验、顺序/空白无关、缺失即失败），
   新增回归 `importToolKeepsUsedStatusRegardlessOfJsonPropertyOrderAndWhitespace`。
2. **[P2] 第二实例清理失效**：`Run-Gate` scriptblock 是子作用域，
   `$secondProcess`/`$ownedSecondInstance` 赋值传不回外层 finally，
   run7 遗留 6 个 JVM（已手工清理）。改为 `$script:` 作用域变量 +
   兜底按命令行扫描残留进程（命中即 cleanup FAIL）。run8 验证优雅退出零残留。
3. **[P2] backend gate 丢失 clean**：恢复 `clean test-compile`，防旧 target
   残留（已删除的 biz/vipCode.json、旧测试类）进构件或测试扫描。
4. **[P2] API 客户端手工同步**：以专用隔离 MySQL/Redis（18306/18379，
   一次性临时目录）启动活后端 18081，`OPENAPI_SCHEMA_PATH` 指向它重新生成；
   `postgen-typings.mjs` 别名表补 `BaseResponseVipExchangeResult`。
   净 diff 仅 typings/userController 两个文件，幂等重跑验证一致。
   **修正**：临时后端用本机原生二进制，未使用 Docker。

### run7（评审修复后回归）
- 修复提交 `2bdc2cb` 后全量重跑：runner-tests / secret-scan / isolation / backend /
  frontend-install / frontend-typecheck / frontend-tests / frontend-build / cleanup 全 PASS。
- backend 464 tests / 0 errors / 0 failures（VIP 专项 13 个全过，含双实例真进程并发）。
- 日志目录：`C:/Users/yuwen/AppData/Local/Temp/visionspace-acceptance-5da06a15dff74b19b52c8c1dd4b93059`。
- 最终提交链：ed31b6e（feat）→ 584fcb7 → 473f943 → 2bdc2cb → 2d83df9（返工）。

### 第三轮评审返工（4 项，572d0e2）

1. **[P1 规范] Ubuntu CI 平台兼容**：CI 工作流跑 ubuntu-24.04（docker 模式）。
   清理兜底的 `Get-CimInstance Win32_Process` 与 `-WindowStyle Hidden` 都是
   Windows 专用——分别按 `$IsWindows` 分流（Linux 扫 `/proc/*/cmdline`、
   不传窗口参数），java 二进制兼容 `JAVA_HOME/bin/java`。静态确认，未跑远程 CI。
2. **[P2 规范] 兜底扫描归属限定**：匹配条件收紧为命令行同时含
   `VipSecondInstanceMain` 与本轮就绪文件路径（runDir 含逐次 nonce），
   不会误杀其他验收运行。
3. **[P1 规格] 重复关键字段**：`code`/`hasUsed` 出现两次即整体失败
   （此前取第一个值，已用码会误判未用并计入补发）。
4. **[P2 规格] JSON 转义解码**：字符串按 RFC 8259 解码（短转义与 uXXXX）
   后再参与摘要；实现与测试都用运行时拼接构造转义形态——javac 的 Unicode
   预处理先于字符串转义处理，源码字面反斜杠u 序列会被提前解码（探针实测确认）。

### run8（返工后回归）
- 全门禁 PASS；backend 465 tests / 0 errors / 0 failures（新增解析回归 1 项）。
- 第二实例优雅退出，无残留进程；stop 文件与端口文件正常生成。
- 日志目录：`C:/Users/yuwen/AppData/Local/Temp/visionspace-acceptance-76382e7ff3e04c868765e7d37c019499`。

### run9（第三轮返工后回归）
- 全门禁 PASS；backend 466 tests / 0 errors / 0 failures（迁移测试 5 项，
  含重复字段失败与转义解码两条新回归）。
- 第二实例优雅退出（stop 文件生成），套件结束后残留进程计数 0。
- 日志目录：`C:/Users/yuwen/AppData/Local/Temp/visionspace-acceptance-66434cd3fa22427097e2c8eb9919188c`。
- 提交链追加：2d83df9（第二轮返工）→ 572d0e2（第三轮返工）。

### 第四轮评审返工（1 项 P2）

1. **[P2 规格] 跨记录重复码**：`[{"code":"SameCode01","hasUsed":false},
   {"code":"SameCode01","hasUsed":true}]` 原实现退出码 0，输出同一摘要的
   作废+已用两条 INSERT（执行时第二条撞 `uk_codeHash`，且默认自动提交下
   先落库的一条状态随机，已用事实可能丢失），并把重复计入待补发数量。
   修复：在输出任何 SQL / 生成补发码之前，对解码后的码值按摘要跨记录查重，
   重复即整体失败（退出码 3、stderr 报告脱敏线索、stdout 零输出、不写补发文件），
   交人工核查源文件。新增回归
   `importToolFailsEntirelyOnCrossRecordDuplicateCodes`
   （断言非零退出、含"重复码"提示、无任何 `INSERT INTO`、补发文件未写入）。

### run10（第四轮返工后回归）
- 全门禁 PASS：runner-tests / secret-scan / isolation / backend /
  frontend-install / frontend-typecheck / frontend-tests / frontend-build / cleanup。
- backend 467 tests / 0 errors / 0 failures（迁移测试 6 项，含跨记录重复码回归）。
- 第二实例优雅退出，残留进程计数 0（cleanup PASS）。
- 日志目录：`C:/Users/yuwen/AppData/Local/Temp/visionspace-acceptance-549a3912aebd4ec7a277d5f47c6d638f`。
- 提交链追加：9927733（run9 文档）→ 第四轮返工 fix（本文档同批提交）。

## 未验证范围（持续 NOT_RUN）

- **远程 Ubuntu CI 实跑**：平台分流（/proc 扫描、条件 WindowStyle）仅静态确认，
  CI 用 docker 模式，本地无 Docker 无法实跑。
- **旧 JAR 回滚端到端演练**：回滚脚本幂等性已由测试覆盖（runOnSingleConnection
  连续两次执行）；「跑导入工具 → 删除运行时 vipCode.json → 部署旧 JAR → 验证
  旧版本不可兑换」的人工迁移关卡属生产操作，不在此环境执行。
- **productionAcceptance**：生产迁移与真实兑换码操作不在本票授权范围内。

## 已发现并修复的实施问题

1. **Oracle javapath 桩**：PATH 首位的 `java.exe` 是无法独立运行的桩（退出码 127），
   第二实例启动静默失败。修复：`acceptance.ps1` 优先用 `JAVA_HOME/bin/java.exe`。
2. **VipSecondInstanceApp 污染测试上下文**：`@SpringBootApplication` 测试入口类位于
   acceptance 包内，被 `@SpringBootTest` 当作配置类发现，导致全部隔离测试
   `ApplicationContext failure threshold exceeded`（178 errors）。修复：删除该类，
   第二实例 main 直接以 `VisionSpaceApplication.class` 为 source，
   web 类型与随机端口经 default properties 注入。


- `UserService.exchangeVip` 签名由 `boolean` 改为 `VipExchangeResult`；`UserServiceImpl`
  的 VIP 区段已重写。#63 改角色逻辑时请勿恢复 `userRole='vip'` 写入——VIP 由
  `vipExpireTime` 表达；`UserRoleEnum.VIP`/`UserConstant.VIP_ROLE` 已无业务含义。
- `UserMapper` 新增两个方法（`selectVipExpireTimeForUpdate`/`updateVipExpireTime`），
  XML 在 `mapper/UserVipMapper.xml`——#63 若迁移 UserMapper XML 请保留。
- `HttpAccountSecurityTest` 若引用 exchangeVip 旧签名需在合并时对齐。
- 第二实例入口 `VipSecondInstanceApp` 与主应用同包扫描层级，测试源码不进生产构件。
