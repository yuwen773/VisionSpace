# #47 全量自动验收与强制合并门禁

工单：[47](https://github.com/yuwen773/VisionSpace/issues/47)，父规格：[26](https://github.com/yuwen773/VisionSpace/issues/26)。起点：`003e487cd8105b2c0bc3908c09faeda87a3b3a7c`。

## 状态与边界

工程实现已接通，但工单保持 **BLOCKED**：依赖扫描发现既有 HIGH / CRITICAL 风险，远程完整候选 CI 尚未执行。没有删除、跳过、忽略这些失败，也没有为获得绿灯进行业务依赖主版本迁移。

本票只运行随机端口、随机数据库与凭据的隔离环境，模型/MCP/存储使用既有受控替身或回环失败端点。不执行生产部署、真实迁移、凭据轮换、真实桶 ACL/CDN 验证、付费模型调用、历史重写或主分支合并。#29/#48 及真实环境批准点继续 BLOCKED / NOT_RUN。

## 单一执行约定

保留 Maven Surefire 的正常测试发现与现有 Vitest/HTTP/浏览器驱动；没有另建业务测试框架。新增回归仍在现有 `HttpPictureContentAuthTest` 中发现，新增脚本回归由现有 `acceptance.ps1` 执行 `scripts/tests/test-*.py`。有跳过用例、损坏/缺失报告或零发现时拒绝 PASS。

准备 JDK 21、Maven、Node 22.21.0、PowerShell 7、Docker、OpenSSL 和 Trivy 0.76.2，安装锁定依赖及对应 Chromium 后，运行：

```powershell
npm ci --prefix frontend --no-audit --no-fund
node frontend/node_modules/playwright-core/cli.js install chromium
pwsh.exe -NoLogo -NoProfile -File scripts/srf-verification.ps1 -Trivy '<Trivy executable>'
```

完整入口要求干净的候选工作树，未提交源码会在执行任何关卡前退出 2。Windows 选择经过版本探测的 Python 3.9+ 实体程序，避免误用应用商店别名。既有 `acceptance.ps1 -MySqlBin/-RedisBin` 保留为 native-explicit 组件验证；该模式不使用 Docker 适配器，其专项测试标为不适用，不能替代本票必需的 Docker 全矩阵。隔离原生 MySQL 与 JVM 统一使用 UTC，不改变系统时区或现有数据库服务。

完整入口顺序运行四个公共入口：

| 关卡 | 命令入口 | 证据 |
| --- | --- | --- |
| acceptance | `scripts/acceptance.ps1` | 全量后端与专用数据库、真实第二 JVM、前端类型/组件/构建、OpenAPI、秘密扫描及 runner 回归 |
| browser | `scripts/srf-browser.ps1` | HTTPS/API/SSE/WSS、真实协作协议与 UI、图表最终 DOM/网络四组合 |
| container | `scripts/container-verification.ps1` | 非 root、可写范围、端口发布、健康、依赖延迟/恢复与耗尽 |
| security | `scripts/security-verification.ps1` | 候选通用秘密扫描、解析后的 Maven SBOM、含开发依赖的 npm 锁文件漏洞扫描 |

每个入口非零都会保留失败；其他独立关卡继续执行。原始诊断留在本轮忽略目录；`.srf-runs/final/<run>/ledger.json` 汇总 T01–T24。每组关联审计标识、实施票、实际执行的测试类与所需关卡；缺报告、跳过、不同提交或不同运行批次均阻断。人工项始终单列，自动替身通过不能提升为生产 PASS。

Trivy 版本固定为 [0.76.2](https://github.com/aquasecurity/trivy/releases/tag/v0.76.2)，Maven SBOM 生成器固定为 CycloneDX 2.9.1；扫描数据库按运行时更新。扫描报告保留公告标识、包坐标、版本与级别，不上传原始扫描输入。原始 POM 扫描可能缺失受 BOM 管理的版本，因此正式关卡只扫描实际解析后的 SBOM。

## CI 与证据卫生

`.github/workflows/srf.yml` 在 PR、main push、merge queue 与手动触发中复用原验收和容器工作流，补上浏览器、安全扫描、同候选证据汇总，最终形成稳定检查名 **`SRF merge gate`**。缺失、失败、取消、跳过或仍待定的子关卡均不能使它成功。原工作流支持复用与单独手动诊断，避免同一事件重复运行整套业务测试。

上传前通过 `publish-evidence.py` 生成允许字段投影：保留 JUnit 测试身份和结果、状态汇总及公开依赖公告。原始日志、SQL、配置、Cookie、异常正文、临时凭据和私钥均不进入 CI artifact。完整原始诊断只保留在本地/runner 的本轮目录。`runtime-versions.py` 仅提取工具版本号，不导出环境变量或工具路径。

## 已实施的远程治理（2026-11-06）

GitHub 插件没有仓库治理接口，使用仓库既有 `gh` 登录执行并读回：

| 设置 / 验证 | 结果 |
| --- | --- |
| Ruleset `SRF required engineering checks` / ID `26689446` | active，main，零 bypass actor，禁止删除/非快进，要求 PR 与已解决的评审讨论 |
| 必需检查 | `SRF merge gate`，绑定 GitHub Actions app `15368`，严格要求最新基线 |
| 不合格候选受控验证 | 同一 Ruleset 临时覆盖独立 probe 分支；与 main 同树的空提交缺少该检查，更新被 HTTP 422 拒绝，响应明确命名该检查；从未尝试更新 main |
| 清理 | 恢复 Ruleset 仅覆盖 main，删除 probe 分支，读回有效主分支规则 |
| Secret Scanning / Push Protection | 保持 enabled |
| Dependabot alerts / security updates | 从 disabled 开启并读回 enabled；另有 npm/Maven/Actions 每周更新配置 |
| GitHub hosted generic patterns | 读回仍为 disabled，不能把 API 请求成功当成设置已生效；候选通用秘密覆盖由必需的 `secret-scan.ps1` 提供 |

GitHub 对 hosted generic patterns 有[仓库类型/产品范围限制](https://docs.github.com/en/code-security/how-tos/secure-your-secrets/detect-secret-leaks/enabling-secret-scanning-for-generic-patterns)。本票不购买或变更账户产品。原始治理读回和受控拒绝证据位于 `.srf-runs/governance.json`。

启用主分支必需检查不会给尚未运行的新工作流制造成功记录。现有 PR 必须包含新工作流并实际满足全部子关卡；不得绕过 Ruleset 合并。此轮没有推送源分支或发布 PR。

## 执行证据

实现阶段已完成：结果分类/门禁/证据投影回归、真实 Docker 隔离服务身份与清理、HTTPS 浏览器 9 场景、双实例协作 21 场景、协作浏览器 10 场景、图表两主题 × 配置/未配置四组合；新增实际内容读取场景已红→绿，授权读取四种内容，陌生人与撤权后读取均拒绝，存储仅收到四次授权读取。

初步完整扫描命中 125 条包-公告记录（18 CRITICAL、107 HIGH；不是独立 CVE 数），涉及既有 Spring/MCP/Tomcat/Tika 等依赖，包含主版本迁移和无修复版本项。保留真实失败，不抑制旧风险、不把安全扫描连通失败算作干净。该计数对应本轮本地依赖快照；漏洞库更新后可能变化，以新生成的脱敏公告清单为准。

初轮完整候选 `74cd6c2` 实际执行：672 条后端中 1 条租约断言失败；runner 出口和生成目录 lint 失败；浏览器全部通过；容器因 Docker Hub 获取构建基镜像时 EOF 而 BLOCKED_ENV；秘密扫描通过、依赖扫描失败。保留该轮 `.srf-runs/final/6a1cd14590fe47c18ffa5672599242d1/`，不能提升为 PASS。

双轴评审发现并修正：从 manifest 派生关卡并校验 workflow 同步；将 Docker 适配器回归接入 runner 清单；移除本次产生的旧不可达 Docker 分支；使用唯一运行 ID（Actions 同时包含 run attempt）、清除旧 summary，并让真实子进程失败覆盖报告状态；在启动边界移除继承的应用/JVM配置覆盖，明确固定数据库、Hikari 与 Redis URL 别名，以及受控模型配置。CI 原始诊断留在 runner 临时文件，不进入控制台或 artifacts。

首轮失败另有三项已定位：脚本负向夹具遗留的 native exit code 通过显式成功出口修正；`dist-harness` 作为生成产物加入 ESLint 忽略；同一隔离 MySQL、无第二实例的受控时区复现中，上海 JVM 失败而 UTC JVM 通过，因此将验收 JVM 固定为 UTC。Docker 基镜像重试拉取成功，仍须重跑容器验证才能判定。

修正后完整候选 `f1f48021c7de37dde0f5407da91501668d124f79`、唯一批次 `b9bdd2c89c13409bacca9198a2df125b`：

| 实际执行项 | 结果 |
| --- | --- |
| 后端完整 Surefire 与构建 | 672 / 672，0 failure、error、skip，PASS |
| 前端组件 | 22 文件、186 / 186，PASS |
| 前端 lint / format / typecheck / build / OpenAPI contract | 全部 PASS |
| HTTPS 9 场景 / 双实例协议 21 场景 / 协作 UI 10 场景 / 图表四组合 | 全部 PASS；同时注入了指向拒绝端口的 Hikari/Redis/模型覆盖变量，仍只使用本轮隔离服务 |
| 容器构建、非 root、端口/写入边界、健康、延迟恢复及重试耗尽 | ALL PASS，cleanup PASS |
| 候选秘密扫描 | PASS |
| Maven/npm 依赖扫描 | FAIL，仍有既有 HIGH / CRITICAL 命中 |
| runner Python 入口 | FAIL：本机 `python3` 为应用商店别名；保留该失败，没有修改该轮报告 |
| 整体 / 生产 / 远程完整 CI | 工程 BLOCKED；生产 NOT_RUN；远程 CI NOT_RUN |

该轮完整证据保留于 `.srf-runs/final/134243677fcf44ff876004b9f004a7b7/`。其 runner 失败由后续解释器探测修正，并通过定向执行验证：三个清单内 PowerShell 回归入口、12 个 Python 用例、仓库卫生和扫描规则回归均通过。没有将这些后续结果改写到 `f1f4802` 的旧台账中。

最后收尾还完成：通用浏览器完整恢复调用者环境（真实执行九场景后断言四类继承变量恢复，PASS）；配置 PlantUML 的图表测试改用本轮拥有的随机回环服务，不访问固定端口上的未知进程；四组合定向执行 PASS；原生 MySQL UTC 参数经实际程序帮助入口验证；隔离运行目录与并行工作树排除出 Docker 构建上下文。

本地主机工具版本实测：Python 3.14.7、JDK 21.0.7、Maven 3.9.9、Node 22.21.0、npm 10.9.4、PowerShell 7.6.9、Docker Engine 29.2.1、Trivy 0.76.2。Docker 服务使用 MySQL 8.0.36 / Redis 7.2.4；主机版本不等同于容器内每个工具的版本。版本投影位于 `.srf-runs/runtime-final.json`，最终治理 PASS/422 拒绝与 probe 清理证据位于 `.srf-runs/governance-final.json`。

最后收尾没有改变 Java 后端/后端测试与 `frontend/src` 业务源码；完整基准仍以明确记录的 `f1f4802` 为准。最终修正版本的完整远程 CI 尚未运行，不能把定向回归升级为新候选全矩阵 PASS。依赖风险仍须后续整改，#47 保持 BLOCKED，不关闭工单、不放行部署。
