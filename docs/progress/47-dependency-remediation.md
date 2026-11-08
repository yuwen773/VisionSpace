# #47 依赖安全整改记录

2026-11-08 状态：同主版本线整改与下文的时限豁免已通过 [PR #83](https://github.com/yuwen773/VisionSpace/pull/83) 合入 main；[main SRF run 42378255886](https://github.com/yuwen773/VisionSpace/actions/runs/42378255886) 已成功。原始扫描的 4 条 CRITICAL 包-公告记录对应 3 个 CVE，属于已批准的已知接受风险，豁免于 `2027-02-07` 到期，不宣称已修复。#47 的完整工程验收和最终关单见 [门禁记录](47-acceptance-gates.md) 及工单收尾评论。

以下“未提交”“未推送”及 FAIL/BLOCKED 均为豁免前的历史过程，保留其原判定；后续授权以文末时限豁免决定为准。

## 决策基线：冻结现有技术框架（2026-11-07）

用户已明确决定：保持现有 Spring Boot 3 / Spring AI 1 / Spring AI Alibaba 1.1.x 主版本组合，
不引入 Spring 7、Boot 4 或 Spring AI 2，不改写 Agent 核心、graph-core、DashScope provider
与自动配置。本轮及后续整改都不得以此扩大范围。

「提升安全版本」仅指同一主版本线内的补丁升级（如 Boot 3.5.8 → 3.5.16），不属于框架变更；
Boot 4 试验产物只作否决记录，不应用到候选。当前候选中 Boot 3.5.16、AI 1.1.8、
Alibaba 1.1.2.4-security-fix 均在该框架线内。

豁免前，剩余 4 条 CRITICAL 属于已知、且本轮不再修复的问题，安全门禁保持 FAIL、
#47 保持 BLOCKED。此后用户另行授权了文末的时限豁免，改变的是风险接受与门禁状态，不是漏洞修复结论。

## 候选与边界

本轮从 `feat/47-acceptance-gates` 的 `6bcd4e525892af753346b45aa3f133ed970f4b56`
创建 `fix/47-dependency-security`。修改尚未提交；日志中的 HEAD 是起点，不能将其当作包含本轮修改的提交。
原工作区、#47 和 #82 的原分支均未改动。

`security-verification.ps1`、SRF workflow、扫描阈值、忽略规则与 Ruleset 均未修改。
依赖发现仍使安全入口返回 1；没有把未修复问题视为通过。

## 已实施

- 前端升级实际命中的直接和传递依赖，保留 DOMPurify 精确版本固定。
- `@umijs/openapi` 的 `mockjs` 限定替换为 `better-mock@0.3.7`。
  通过生成器实际解析的依赖调用 `Random.extend`，旧包造成原型污染的回归先红后绿；
  同时检查自定义随机值和真实客户端生成，未仅通过更换包名消除扫描记录。
- 后端保留 Spring Boot 3 / Spring AI 1 的兼容主版本，提升 Boot、AI、Alibaba 安全版本、
  Tomcat、Netty、Jackson、Bouncy Castle 及命中的存储和解析依赖。
- 升级阿里云 SDK，实际移除无修复版本的 ini4j；fastjson 使用同坐标 2.x 兼容实现。
- MCP 使用对齐的原生 SDK 与 Jackson 2 codec；应用自行管理出站和工具授权，
  移除未使用的 Spring MCP 自动配置及默认聚合包，避免引入不需要的 Jackson 3 codec。
- 原生隔离运行器显式以 UTC 启动 MySQL，与 #47 的验收 JVM 时区约定一致。
  旧入口实测为 `SYSTEM`、数据库 `NOW()` 比 UTC 快 8 小时，导致租约过期回归失败；
  保留该轮失败及中断记录，未把进程退出码当作完整 JUnit 通过证据。

## 实际验证

| 检查 | 结果 |
| --- | --- |
| 原依赖扫描复现 | 125 条包-公告记录，18 CRITICAL / 107 HIGH；不是独立 CVE 数 |
| 最终兼容候选依赖扫描 | 4 条 CRITICAL，0 HIGH；前端 0，安全入口保持 FAIL |
| 秘密扫描 | PASS |
| 前端回归 | 23 文件、189/189 PASS，含新增 3 条依赖行为测试 |
| 前端类型检查、lint、单文件格式检查 | PASS |
| 前端生产构建 | PASS；受限 1 GB 堆的初次尝试失败，2 GB 堆串行构建成功，未改构建逻辑 |
| 客户端生成兼容夹具 | PASS；不等同于完整后端契约比对 |
| 定向 HTTP / 恢复 / MCP / OpenAPI | 中间兼容候选 43/43 PASS，0 跳过；最终候选另跑完整后端 |
| 最终候选完整后端与双实例 | 98 个测试报告、672/672 PASS，0 失败/错误/跳过；真实第二 JVM，`verify` 打包 PASS |
| 完整生成契约比对 | 最终 UTC 批次导出契约，逐字节比对 PASS |
| 打包依赖读回 | 确认新版本入包，ini4j、旧 fastjson 和 Jackson 3 MCP codec 不在产物中 |
| 隔离运行清理 | 本轮六批次相关进程均退出，六个 MySQL 数据目录均已删除；诊断日志保留 |
| 最终候选容器、完整浏览器与远程 CI | NOT_RUN |
| 真实环境、模型服务、迁移、发布 | NOT_RUN |

原始诊断保留在本工作区 `.srf-runs/`；安全投影与候选差异指纹保留于
`.srf-runs/dependency-evidence/`，不把中间版本或中断结果混入最终结果。

## 尚未解决的 4 条记录

| 包 | 当前版本 | 公告 | 扫描修复版本 |
| --- | --- | --- | --- |
| spring-webflux | 6.2.19 | CVE-2026-47890 | 7.0.9 |
| spring-webflux | 6.2.19 | CVE-2026-47892 | 7.0.9 |
| spring-webmvc | 6.2.19 | CVE-2026-47884 | 7.0.9 |
| spring-webmvc | 6.2.19 | CVE-2026-47890 | 7.0.9 |

以上为 4 条包-公告记录、3 个不同 CVE。当前 OSS Spring 6.2 无对应发布补丁；
官方公告列出的 6.2.20 属企业支持范围。不能用供应商较低严重性评级覆盖当前扫描规则。
来源：[Spring 官方公告](https://spring.io/security/cve-2026-47890/)。

## Spring 7 迁移试验：已否决，不再作为候选

试验中 Boot 4.0.9 / Spring 7 的依赖扫描曾达到零命中，但该中间候选不可运行：
Spring AI 1.1.8 在实际 HTTP 验收启动时调用已删除的 `HttpHeaders.addAll`，触发 `NoSuchMethodError`。
改用 AI 2.0.2 后，应用引用的 `OpenAiApi` 已删除，编译失败。

进一步核对 Alibaba 已发布源码发现，稳定安全版本及 2.0 里程碑均仍调用 AI 2 GA
删除的选项、ChatClient 和序列化接口。上游 `2.x` 的
`ddb807be824813a226474a6b0b6815f28c9437ad` 已有适配实现，但尚无对应稳定发布构件。
因此没有将这些不可运行的版本组合留在本候选中，也不把试验的零命中视为整改完成。
按上述「冻结现有技术框架」决策，这条迁移路线已被否决，试验产物仅作证据保留，不构成后续待办。

该路线若要走通，必须改写 graph-core、agent-framework，连同 DashScope provider 与自动配置；
还需重新证明工具确认、取消、恢复和零未授权副作用，尤其要防 AI 2 自动工具 Advisor
绕过现有图内确认流程。这正是「冻结现有技术框架」所排除的范围，本轮不实施。
迁移版本与错误证据留在 `.srf-runs/boot4-experiment/` 及对应日志中，不得应用到候选。

剩余 4 条包-公告记录在本轮范围内无兼容修复版本：OSS Spring 6.2 线无补丁，
免费可得的 7.0.9 与现有框架线不兼容。不以供应商评级覆盖扫描规则，也不因试验零命中宣称整改完成。

## 时限豁免决定（用户 2026-11-07 授权）

为解开「Ruleset 要求 SRF merge gate，而 srf.yml 尚未合入 main，门禁自身又被
security 挡住」的死锁，用户明确授权引入带到期日的 Trivy 豁免：

- `.trivyignore` 位于仓库根（`security-verification.ps1` 以仓库根为 cwd 调用
  Trivy，默认在该处解析），对 47884 / 47890 / 47892 三条 CVE 设置
  `exp:2027-02-07`，到期自动失效、告警重现。
- 豁免不改变 HIGH/CRITICAL 阈值、扫描器错误仍为 BLOCKED_ENV、其余命中不变。
- 到期或框架决策变化时须重新评估：升级框架、购买 Spring 企业支持（6.2.20）
  或论证漏洞不可达。安全门禁因此可转绿，但本记录不宣称这 4 条记录对应的 3 个 CVE 已修复，
  只记录已知接受风险及到期日。

后续若改变框架决策，须由用户重新明确授权并另立范围；本记录不代表该授权。
