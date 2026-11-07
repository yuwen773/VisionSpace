# #47 dependency remediation research

Date: 2026-11-07. Scope: primary-source research for the isolated dependency remediation worktree. These findings are not compatibility acceptance or a production security decision.

## Decisions supported by current sources

### Spring Framework requires a supported release-line decision

The vendor advisories for CVE-2026-47884, CVE-2026-47890 and CVE-2026-47892 list affected Framework 6.2.0–6.2.19. The OSS fix is 7.0.9; 6.2.20 is an enterprise-only release. Maven Central metadata currently ends the OSS 6.2 line at 6.2.19. Raising Boot 3.5.8 to the final Central 3.5.16 therefore does not remove those package-version findings from an unchanged Trivy gate. Application exploitability and package inventory findings are distinct: the vendor identifies specific view-rendering, fragment-SSE or functional-routing prerequisites, but the current gate does not implement an applicability exception mechanism. Do not claim that absence of these features makes the package scan pass.

Sources: [47884](https://spring.io/security/cve-2026-47884/), [47890](https://spring.io/security/cve-2026-47890/), [47892](https://spring.io/security/cve-2026-47892/), [Framework metadata](https://repo.maven.apache.org/maven2/org/springframework/spring-framework-bom/maven-metadata.xml).

Boot 4.1.2's published BOM manages Framework 7.0.10, Jackson 2.21.7 and Jackson 3.1.7, Tomcat 11.0.27, Netty 4.2.19.Final and Micrometer 1.17.2. These exact managed versions were read from its Central POM, not inferred from release numbers. Boot 4 migration must be tested as a runtime migration; overriding Framework 7 below a Boot 3 BOM has no source-backed compatibility claim here.

Source: [Boot 4.1.2 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom).

### Spring AI 1.1.8 does not mean MCP SDK 1.x

The official compatibility table maps AI 1.1.x to Boot 3.5.x and AI 2.x to Boot 4.x. AI 1.1.8's published `spring-ai-mcp` POM still declares SDK `mcp`, `mcp-spring-webflux` and `mcp-spring-webmvc` 0.18.3. The March 2026 blog's transport relocation and Jackson 3 migration apply to its AI 2.0.0-M3 section, not its AI 1.1.3 section.

Sources: [official compatibility table](https://github.com/spring-projects/spring-ai/blob/main/README.md), [AI 1.1.8 MCP POM](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-mcp/1.1.8/spring-ai-mcp-1.1.8.pom), [release announcement](https://spring.io/blog/2026/03/17/spring-ai-2-0-0-M3-and-1-1-3-and-1-0-4-available/).

GHSA-8jxr-pr72-r468 still lists `mcp-core <1.0.0` and fix 1.0.0. Its vendor advisory explicitly distinguishes Spring AI server origin checks from vulnerable server defaults; that statement does not change the current raw package gate. An SDK issue records a claimed 0.18.3 backport, but its closed/invalid state and the still-published affected range are insufficient to declare the package scan green.

Sources: [vendor GHSA](https://github.com/modelcontextprotocol/java-sdk/security/advisories/GHSA-8jxr-pr72-r468), [SDK issue 1129](https://github.com/modelcontextprotocol/java-sdk/issues/1129).

Smallest candidate to investigate: replace the application's `spring-ai-starter-mcp-client` with direct, aligned SDK `mcp-core` + `mcp-json-jackson2` dependencies. Local source inspection found that `MCPManager` builds native SDK HTTP/STDIO clients and that application Java has no `org.springframework.ai.mcp` imports. Removing an unused starter therefore may avoid old Spring transport classes while retaining the existing client features; this remains an inference until context-startup and real controlled MCP tests pass. Candidate stable SDK lines from Central are 1.0.2 (latest 1.0 patch) or 1.1.5 (latest 1.1 patch).

SDK 1.0 migration removes `io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper`; Jackson 2 users must import `io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper`. Its `mcp` convenience artifact defaults to Jackson 3, so use explicit core + Jackson 2 modules when retaining the application's existing Jackson API. Deprecated `CallToolResult` constructors are also removed. Inspection of AI 1.1.8 published sources found those constructors in `McpToolUtils`; compile alone cannot establish compatibility if that path remains callable.

Sources: [official SDK migration guide](https://github.com/modelcontextprotocol/java-sdk/blob/582c416cff97e463eaccc15b1998e01441c2f831/MIGRATION-1.0.md), [SDK 1.0 release](https://github.com/modelcontextprotocol/java-sdk/releases/tag/v1.0.0), [SDK metadata](https://repo.maven.apache.org/maven2/io/modelcontextprotocol/sdk/mcp-bom/maven-metadata.xml), [AI 1.1.8 published sources](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-mcp/1.1.8/spring-ai-mcp-1.1.8-sources.jar).

## No-fix transitive dependencies

| Existing path | Source-backed candidate | Required verification |
| --- | --- | --- |
| Alibaba agent framework → fastjson 1.2.83 | Manage the same-coordinate `com.alibaba:fastjson` compatibility artifact at 2.0.66. Upstream adopted 2.0.58 in PR 4871. | Agent/checkpoint/tool behavior, fresh resolved SBOM and Trivy. |
| Aliyun OSS → aliyun-java-sdk-core 4.5.10 → ini4j 0.5.4 | Upgrade SDK core to 4.7.10, whose published POM and sources no longer contain ini4j. OSS latest stable is 3.18.5. | Storage initialization and controlled OSS/STS behavior; verify ini4j absent from resolved SBOM. |
| x-file-storage-core → Tika 2.4.1 | Override Tika to a fixed 3.x, such as 3.3.3; storage 2.3.0 still declares Tika 2.4.1. | Upload type detection/metadata and existing image/storage tests. Do not exclude Tika: storage uses it. |
| Alibaba DashScope → OpenNLP 2.3.3 | Upgrade to stable 2.5.12 (2.5.9 is listed minimum fix for current findings). | DashScope/tokenization tests and context startup. |

The Alibaba `1.1.2.4-security-fix` agent framework POM still declares fastjson 1.2.83; the security suffix does not establish a clean inventory. Its published source references fastjson in A2A code. Removing the dependency without replacing that capability is not justified by this research.

Sources: [Alibaba published agent POM](https://repo.maven.apache.org/maven2/com/alibaba/cloud/ai/spring-ai-alibaba-agent-framework/1.1.2.4-security-fix/spring-ai-alibaba-agent-framework-1.1.2.4-security-fix.pom), [upstream fastjson change](https://github.com/alibaba/spring-ai-alibaba/pull/4871), [fastjson metadata](https://repo.maven.apache.org/maven2/com/alibaba/fastjson/maven-metadata.xml), [Aliyun core 4.7.10 POM](https://repo.maven.apache.org/maven2/com/aliyun/aliyun-java-sdk-core/4.7.10/aliyun-java-sdk-core-4.7.10.pom), [storage 2.3.0 POM](https://repo.maven.apache.org/maven2/org/dromara/x-file-storage/x-file-storage-core/2.3.0/x-file-storage-core-2.3.0.pom), [OpenNLP metadata](https://repo.maven.apache.org/maven2/org/apache/opennlp/opennlp-tools/maven-metadata.xml).

## mockjs: replacement must remove the vulnerable behavior

The registry's latest `@umijs/openapi` is still 1.14.1 and depends on mockjs 1.1.0. `openapi-ts-request` 1.12.8 also depends on it; changing to that generator does not remove the vulnerable dependency. GHSA-mh8j-9jvh-gjf6 has no patched mockjs version and names recursive `Util.extend` plus its aliases, including `Random.extend`, as the prototype-pollution path.

Sources: [npm registry metadata](https://registry.npmjs.org/@umijs%2fopenapi), [alternative generator metadata](https://registry.npmjs.org/openapi-ts-request), [reviewed GHSA](https://github.com/advisories/GHSA-mh8j-9jvh-gjf6).

Candidate: scope an npm alias replacement of the generator's mockjs dependency to `better-mock` 0.3.7. The published tarball has a rewritten, frozen `Util` export with no recursive `extend`; its `Random.extend` performs a shallow copy into `Random`. Local inspection of the installed generator's `dist/mockGenerator.js` found only `Random.extend` and `mock` consumption, both retained by this fork. This is a code-level candidate, not a claim that the fork is actively security-maintained or a safe fix solely because it has a different package name.

Sources: [fork source](https://github.com/lavyun/better-mock/blob/master/src/utils/index.ts), [fork registry release](https://registry.npmjs.org/better-mock/0.3.7), [fork source tree](https://github.com/lavyun/better-mock/tree/master/src).

Required behavioral regression: resolve mockjs from the generator package, call `Mock.Random.extend(JSON.parse('{"__proto__":{"vs47PrototypePolluted":"unexpected"}}'))`, then assert a fresh plain object's `vs47PrototypePolluted` is undefined. Repeat with `{"constructor":{"prototype":{"vs47PrototypePolluted":"unexpected"}}}`. The original recursive implementation should fail; the replacement must pass. Clean the test marker from `Object.prototype` and restore the original `Random` prototype in `finally`. Also extend a normal fixed-value random function and confirm `Mock.mock('@vs47FixedValue')` still works, then run the existing byte-for-byte OpenAPI generation/contract check. This preserves the generator convention and verifies actual vulnerable behavior instead of hiding a package coordinate.

## Acceptance boundary

Every version is a candidate until its resolved dependency inventory and compatibility checks pass. Preserve HIGH/CRITICAL thresholds, development dependency coverage, scanner errors as failures/blockers, existing Ruleset requirements, and separate production/manual acceptance. No remote setting changes, commits, pushes or merges were performed by this research task.

## Follow-up: confirmed Alibaba / AI 2 GA incompatibilities

The implementation task reported a runtime `HttpHeaders.addAll` `NoSuchMethodError` during OpenAI startup with AI 1.1.8 + Framework 7. This research did not rerun that test. Independent source inspection confirms that switching only Spring AI to 2.0.2 then encounters additional, core Alibaba ABI incompatibilities; those are not resolved by excluding optional model providers.

The first-party `2.x` branch was read through the GitHub connector and pinned at **`ddb807be824813a226474a6b0b6815f28c9437ad`**. Its root POM declares unreleased revision `2.0.0.0-RC1`, Spring AI 2.0.0, and Boot 4.1.0. This is a source reference for an actual GA compatibility port, not a stable published release. The published `v2.0.0-M1.1` release explicitly targets **AI 2.0.0-M1 / Boot 4.0.0**. Inspection of that tag's `AgentLlmNode` found the same now-removed APIs as the stable 1.1 security-fix release. It is therefore not a drop-in fix for AI 2 GA.

Sources: [pinned branch commit](https://github.com/alibaba/spring-ai-alibaba/commit/ddb807be824813a226474a6b0b6815f28c9437ad), [pinned POM](https://github.com/alibaba/spring-ai-alibaba/blob/ddb807be824813a226474a6b0b6815f28c9437ad/pom.xml), [milestone release](https://github.com/alibaba/spring-ai-alibaba/releases/tag/v2.0.0-M1.1), [milestone LLM node](https://github.com/alibaba/spring-ai-alibaba/blob/v2.0.0-M1.1/spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/node/AgentLlmNode.java).

The following calls were read from the **published 1.1.2.4-security-fix source jars**, and compared to the official AI 2.0.2 upgrade notes. They describe concrete linkage failures on used call paths, not failures that this research independently executed:

| Application call path / module | Binary call in published Alibaba code | AI 2 GA change / necessary port |
| --- | --- | --- |
| `ImageAgent.init` / resume-agent construction → `ReactAgent.builder().model(chatModel).build()` → `DefaultBuilder` | `ChatClient.Builder.defaultOptions(ChatOptions)` and `ModelOptionsUtils.merge(...)` | Options now use builders; pinned upstream uses `effectiveOptions.mutate()` and `mutate().combineWith(...)`. |
| Graph model dispatch → `AgentLlmNode` | `ToolCallingChatOptions.copy()`, `setInternalToolExecutionEnabled(false)`, builder `internalToolExecutionEnabled(false)`, `ChatClientRequestSpec.options(ChatOptions)`, `DefaultChatClientRequestSpec.getChatOptions()` | Copy/setter/internal-loop APIs removed; pass mutated builders and preserve provider-specific options. |
| Every configured model interceptor → `InterceptorChain` | `src.getOptions().copy()` | Replace with `src.getOptions().mutate().build()` as in pinned upstream. |
| State diagnostic serialization → graph-core `OverAllState.toString()` | `org.springframework.ai.util.json.JsonParser.toJson(...)` | Static utility removed; pinned upstream uses `new JsonHelper().toJson(...)`. A linkage `Error` is not caught by the old method's `catch (RuntimeException)`. |
| DashScope chat provider / options / streaming API | `ToolExecutionEligibilityPredicate` and `DefaultToolExecutionEligibilityPredicate`; `ModelOptionsUtils.jsonToObject/jsonToMap/toJsonString/copyToTarget/merge`; legacy mutable options | Removed types and methods require a provider port; the old options object cannot simply be passed through the new ChatClient options-builder contract. |

Sources: [stable agent sources](https://repo.maven.apache.org/maven2/com/alibaba/cloud/ai/spring-ai-alibaba-agent-framework/1.1.2.4-security-fix/spring-ai-alibaba-agent-framework-1.1.2.4-security-fix-sources.jar), [stable graph sources](https://repo.maven.apache.org/maven2/com/alibaba/cloud/ai/spring-ai-alibaba-graph-core/1.1.2.4-security-fix/spring-ai-alibaba-graph-core-1.1.2.4-security-fix-sources.jar), [stable DashScope sources](https://repo.maven.apache.org/maven2/com/alibaba/cloud/ai/spring-ai-alibaba-dashscope/1.1.2.4-security-fix/spring-ai-alibaba-dashscope-1.1.2.4-security-fix-sources.jar), [AI 2 upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html).

### Minimum port estimate and safety constraints

For the application's ChatClient graph path, the static lower bound is **four changed classes across two modules**: `DefaultBuilder`, `AgentLlmNode`, `InterceptorChain` in agent-framework, plus `OverAllState` in graph-core. This is a lower bound, not a complete migration estimate: source/binary compatibility checks and the graph tool/checkpoint/HITL tests may reveal additional required classes. Optional `AgentTool`, multimodal/skills converters and loop strategies also reference removed `JsonParser`; they need porting if their features are retained or reachable. Do not silently exclude or disable them and claim equivalent behavior.

The upstream LLM-node port contains a security-relevant semantic change: it sets the `ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER` advisor parameter to `false`. AI 2 otherwise automatically executes tools inside a `ToolCallingAdvisor`. Leaving this enabled while graph/HITL code expects to own the tool loop could execute a tool before the graph's confirmation boundary. A correct port must prove unconfirmed tool requests produce **zero tool side effects**, and preserve confirm/cancel, persisted terminal results, crash/reconnect recovery, and model/tool options. Merely replacing removed methods is insufficient.

Sources: [pinned DefaultBuilder](https://github.com/alibaba/spring-ai-alibaba/blob/ddb807be824813a226474a6b0b6815f28c9437ad/spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/DefaultBuilder.java), [pinned AgentLlmNode](https://github.com/alibaba/spring-ai-alibaba/blob/ddb807be824813a226474a6b0b6815f28c9437ad/spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/node/AgentLlmNode.java), [pinned InterceptorChain](https://github.com/alibaba/spring-ai-alibaba/blob/ddb807be824813a226474a6b0b6815f28c9437ad/spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/interceptor/InterceptorChain.java), [pinned OverAllState](https://github.com/alibaba/spring-ai-alibaba/blob/ddb807be824813a226474a6b0b6815f28c9437ad/spring-ai-alibaba-graph-core/src/main/java/com/alibaba/cloud/ai/graph/OverAllState.java).

Retaining the configured DashScope Spring provider adds at least the **DashScope provider module and its Boot auto-configuration module** to the investigation. The minimum chat path spans `DashScopeChatModel`, `DashScopeChatOptions`, `DashScopeApi`, `DashScopeApiSpec`, response-format serialization and tool execution/validation. Other auto-configured provider beans may add more work. A separate native DashScope Java image SDK call is not proof that the Spring DashScope chat provider still works. The source evidence does not support a compatible all-stable Alibaba + AI 2 GA combination at present.

### Jackson 2 HTTP does not replace Jackson 3 model internals

Retaining a Jackson 2 MVC converter can preserve the application's existing HTTP JSON contract while AI 2 internally uses Jackson 3; these libraries use different mapper namespaces. That arrangement does not adapt old mutable `DashScopeChatOptions`, removed static JSON utilities, graph state serialization, or Spring AI options ABI. Keep MVC wire-contract verification and model-provider/tool/graph serialization verification separate. In particular, test configured model options and tool definitions reaching the controlled model endpoint, not just context startup or HTTP DTO round trips.

Source: [AI 2 GA announcement](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/), [AI 2 upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html).

Follow-up task performed only additional source reads and this append. No builds, provider calls, source/POM edits, remote settings or Git mutations were performed.
