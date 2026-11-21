# #58 站内检索不再阻塞启动

1.0.3 发布后留下的一个取舍：开启 RAG 的部署里，Chroma 不可用会导致后端起不来。
本轮把它解决掉。

## 1. 问题本质

`ChromaVectorStore` 的构造函数里就调 `afterPropertiesSet()`，而 `initializeSchema=true` 时
它会真的发 HTTP 请求建 tenant / database / collection。Spring 在上下文刷新阶段创建这个 Bean，
一抛异常整个应用就退出——**一个可选功能被装配成了启动期的硬依赖**。

1.0.3 里靠 `restart: unless-stopped` 等 Chroma 就绪后重启兜底，这只是「重启到好为止」，
不是设计。

## 2. 方案

两件事一起做：

1. **懒初始化 + 失败降级**（代码层）：不再使用 Spring AI 的
   `ChromaVectorStoreAutoConfiguration`，改由本项目装配一个容错门面。
2. **健康探针 + 依赖门控**（运维层）：给 `chromadb` 加 healthcheck，backend 改成
   `condition: service_healthy`，避免「部署顺序」这一种最常见的不可达。

## 3. 实现

### `ResilientVectorStore`

包住真正的 `ChromaVectorStore`，实现 `VectorStore`：

- **启动期不连库**。委托对象在第一次真正用到时才创建；失败后按 30 秒退避重试，
  不在每个请求上都撞一次连接超时。
- **降级语义分两类**：写入（`add`/`delete`）抛异常，让调用方看见「没写进去」——
  全量同步不能谎报成功；检索返回空列表，站内检索本来就是尽力而为。
- **运行期失败自动重连**：某次调用抛异常说明向量库已经不可用（重启、断网），
  丢弃委托并重新进入退避，下次调用时重建。

### `RagVectorStoreConfiguration`

`@ConditionalOnProperty(spring.ai.vectorstore.type=chroma)`。用 `ChromaApiProperties` /
`ChromaVectorStoreProperties` 绑定同一套属性（排除自动配置后这两个 properties Bean 不会
被注册，所以显式 `@EnableConfigurationProperties`）。构造 `ChromaVectorStore` 时传
`initializeImmediately(false)`，再在工厂方法里显式调 `afterPropertiesSet()`——这样
建集合的时机就落在门面的懒初始化里，而不是上下文刷新阶段。

### `RagHealthIndicator`

`UP` = 已连上；`UNKNOWN` = 还没用过（不等于坏了）；`DOWN` = 尝试过且失败。
**不进 readiness 组**（`management.endpoint.health.group.readiness` 只含
`readinessState,db,redis`）：向量库挂了应当降级，不该让容器被判为不可用而反复重启。

### 自动配置排除

`ChromaVectorStoreAutoConfiguration` 在 `application.yml` 与 `acceptance.yml` 里排除。
后者是必须的：验收配置通过 `spring.config.location` 整体替换默认配置，不会继承
`application.yml`。

### chromadb 健康探针

`ghcr.io/chroma-core/chroma:1.0.0` 是个精简镜像：`curl`、`wget`、`python3`、`nc` 全都没有，
`/usr/local/bin` 里只有一个 `chroma` 二进制（CLI 没有探活子命令），`/bin/sh` 是 dash。
能用的只有 `bash` 和 `grep`，所以探针用 bash 内建的 `/dev/tcp` 发一次 HTTP 请求：

```yaml
test:
  - CMD-SHELL
  - >-
    bash -c "exec 3<>/dev/tcp/127.0.0.1/8000 && printf 'GET /api/v2/heartbeat HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n' >&3 && head -n 1 <&3 | grep -q ' 200 '"
interval: 10s
timeout: 5s
retries: 6
start_period: 30s
```

两个失败路径都是快速失败：端口没开时 `/dev/tcp` 直接 `Connection refused`（实测 0.5 秒退出 1）；
端口开了但服务没应答时由 `timeout: 5s` 兜住。实测启动后 10 秒内转为 `healthy`，
单次探针耗时约 90 毫秒。

`backend.depends_on.chromadb` 相应从 `service_started` 改为 `service_healthy`。

## 4. 回归测试

- `ResilientVectorStoreTest`（单测）：构造期不连接、不可用时检索返回空、写入抛异常、
  退避后重连、运行期失败降级后再恢复。
- `RagVectorStoreConfigurationTest`（装配层）：把开关切到 `chroma`，地址指向必定拒绝
  连接的 `127.0.0.1:1`，断言**装配照常完成**、启动期不连库、检索降级为空；
  开关为 `none` 时容器里没有向量库 Bean。

这条路径在 1.0.3 之前完全没有覆盖——基线 / 验收默认 `vectorstore.type=none`，
723 个测试全绿也拦不住那两个只在开启 RAG 时才出现的启动失败。

**为什么不用完整应用上下文。** 最初写的是 `@IsolatedSpringBootTest`（和别的验收用例
一样起完整上下文），结果它把整个验收套件拖垮：同一次 surefire 运行里多出来的那份上下文
与后面的 WebSocket 用例抢资源，洪泛背压用例撞上 60 秒预算超时；加上 `@DirtiesContext`
让它用完即关之后，紧接着的 `WsPictureEditContractTest` 拿到的就是那个已关闭的上下文——
Tomcat 还在收请求，Redis 连接工厂已经销毁，18 个用例全部在登录时返回 `50000 系统错误`：

```
java.lang.IllegalStateException: LettuceConnectionFactory was destroyed and cannot be used anymore
    at com.yuwen.visionspace.service.impl.UserServiceImpl.userLogin(UserServiceImpl.java:133)
```

完整应用的启动由容器门禁用真实 compose 栈验证，本地这条用例只负责「装配不连库」这个
因果关系，用 `ApplicationContextRunner` 只加载 RAG 装配层即可，不起 Tomcat / 连接池 / 调度线程。

## 5. 验收

`scripts/acceptance.ps1`（不带参数，隔离 MySQL + Redis 容器），14 个门禁全 PASS，
runId `7f034454a9a74bdf96892ba3fc6889e3`。被验收的提交是 `4246aa2`；之后只补了本节
记录（纯文档改动，不进构建），代码未变。

| 门禁 | 结果 | 数量 |
| --- | --- | --- |
| runner-tests | PASS | 3 |
| secret-scan | PASS | 896 个候选文件，0 命中 |
| isolation | PASS | |
| backend | PASS | 732 个测试（723 + 9 新增），0 失败 |
| frontend-install | PASS | |
| frontend-lint | PASS | |
| frontend-format | PASS | |
| frontend-typecheck | PASS | |
| frontend-tests | PASS | 210 个测试 / 27 个文件 |
| frontend-build | PASS | |
| asset-color-calibration | PASS | |
| asset-search-browser | PASS | 3 个浏览器 IT |
| frontend-api-contract | PASS | 生成客户端与契约逐字节一致 |
| cleanup | PASS | |

`productionAcceptance` 为 `NOT_RUN`。容器门禁（CI 的 `container-verification.yml`）用真实
compose 栈验证「Chroma 不可用时后端照常启动」，本轮未在本地重跑。

### 过程中踩到的两个坑

**一、`@DirtiesContext` 关掉的上下文会被下一个用例复用。** 见 §4：18 个 WebSocket 用例
全部在登录时返回 `50000 系统错误`。不是断言问题，是上下文生命周期问题。

**二、`WsPictureEditContractTest` 在本机时序抖动，与本次改动无关。** 连续几轮全量验收里
它每次挂的用例都不一样（洪泛排空、租约续期、断连等待写事务），每轮都只有 1–3 个失败。
用 `origin/main`（`8e7257b`，723 个测试，不含本次任何改动）在同一台机器、同一个 worktree
跑同一套隔离环境做对照，**基线同样挂 `onlineAuthorizedHolderIsRenewedBeyondOriginalTtl`**：

| 运行 | 代码 | 测试数 | 失败 |
| --- | --- | --- | --- |
| C | `feature/rag-resilience` | 732 | 2（flood、renewal） |
| D | `feature/rag-resilience` | 732 | 3（disconnect、flood、renewal） |
| E | `origin/main` | 723 | 1（renewal） |
| F | `feature/rag-resilience` | 732 | 0 |

这几条用例都建立在真实时间预算上（洪泛后 60 秒排空、3 秒租约靠 1 秒续期顶住），
本机同时还有别的会话在跑构建时会失手。要判断「是不是代码问题」，先看失败集合是否稳定，
再用基线跑同一套环境对照——不要按单次结果改断言。
