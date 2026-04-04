# MCP 用户配置服务设计方案

> 日期：2026-04-04
> 分支：feature/agent-dev
> 状态：待审批

---

## 1. 目标

在现有 VisionSpace Agent 模块基础上，集成 MCP（Model Context Protocol）服务配置功能，允许用户通过前端界面管理自己的 MCP 服务器连接，为 Agent 提供更多工具能力。

### 核心价值

- 用户可管理多个 MCP 服务，按需启用/禁用
- 支持 JSON 手动输入和表单化配置两种方式
- MCP 工具与现有 7 个内置 @Tool 注解工具共存
- 会话级别临时调整 + 用户默认偏好双重机制

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         用户操作                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. MCP 服务管理（侧边栏「设置」→「管理 MCP 服务」）                  │
│     ┌──────────────┐    ┌─────────────────┐    ┌──────────────┐ │
│     │ 前端表单/JSON │───▶│ McpServerController │───▶│ 数据库      │ │
│     └──────────────┘    └─────────────────────┘ └──────────────┘ │
│                                                                  │
│  2. 用户偏好设置（侧边栏「设置」→「默认启用的 MCP」）                 │
│     ┌──────────────┐    ┌─────────────────┐    ┌──────────────┐ │
│     │ 前端多选器    │───▶│ UserMcpPreferenceApi │───▶│ Redis 缓存   │ │
│     └──────────────┘    └─────────────────────┘ └──────────────┘ │
│                                                                  │
│  3. Agent 对话时加载 MCP 工具                                      │
│     ┌──────────────┐    ┌────────────────────┐    ┌────────────┐ │
│     │ TextMcpModel │───▶│ 获取用户偏好 + 会话覆盖│───▶│ MCPManager │ │
│     │ Interceptor  │    │ 合并生成 ToolCallback │    │ .getTools()│ │
│     └──────────────┘    └────────────────────┘    └────────────┘ │
│                                                                  │
│  4. 工具调用执行                                                  │
│     ┌──────────────┐    ┌─────────────────┐    ┌──────────────┐ │
│     │ Agent 调用   │───▶│ McpToolCallback │───▶│ MCP Server   │ │
│     │   tool      │    │                 │    │ (外部服务)    │ │
│     └──────────────┘    └─────────────────┘    └──────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 数据库设计

### 3.1 mcp_server 表

```sql
CREATE TABLE `mcp_server` (
  `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
  `mcpServerCode`  VARCHAR(64)  NOT NULL COMMENT '服务器编码',
  `userId`         BIGINT      NOT NULL COMMENT '所属用户ID',
  `name`           VARCHAR(128) NOT NULL COMMENT '服务器名称',
  `description`    VARCHAR(512) COMMENT '描述',
  `deployConfig`   TEXT         NOT NULL COMMENT '部署配置(JSON)',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用 1=启用 3=删除',
  `installType`    VARCHAR(32)  NOT NULL DEFAULT 'SSE' COMMENT '安装类型',
  `host`           VARCHAR(256) COMMENT 'MCP服务器地址(提取自URL)',
  `gmtCreate`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmtModified`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_mcp_server_code_user` (`mcpServerCode`, `userId`),
  KEY `idx_user_id` (`userId`)
);
```

### 3.2 user_mcp_preference 表

```sql
CREATE TABLE `user_mcp_preference` (
  `id`                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  `userId`               BIGINT      NOT NULL UNIQUE COMMENT '用户ID',
  `defaultEnabledServers` TEXT        COMMENT '默认启用的MCP服务列表(JSON数组)',
  `gmtCreate`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmtModified`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

---

## 4. 数据模型

### 4.1 McpServerEntity

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| mcpServerCode | String | 服务器唯一标识 |
| userId | Long | 所属用户 |
| name | String | 服务器名称 |
| description | String | 描述 |
| deployConfig | String | 部署配置（JSON） |
| status | Integer | 状态：0=禁用 1=启用 3=删除 |
| installType | String | 安装类型，默认 SSE |
| host | String | MCP 服务器地址 |
| gmtCreate | Date | 创建时间 |
| gmtModified | Date | 修改时间 |

### 4.2 UserMcpPreferenceEntity

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| userId | Long | 用户ID |
| defaultEnabledServers | String | 默认启用的 MCP 列表（JSON 数组） |
| gmtCreate | Date | 创建时间 |
| gmtModified | Date | 修改时间 |

### 4.3 McpServerDetail（DTO）

```java
@Data
public class McpServerDetail implements Serializable {
    private String mcpServerCode;
    private String name;
    private String description;
    private String deployConfig;
    private Integer status;
    private String installType;
    private String host;
    private List<McpTool> tools;  // needTools=true 时返回
}
```

### 4.4 McpTool

```java
@Data
public class McpTool implements Serializable {
    private String name;
    private String description;
    private InputSchema inputSchema;
}

@Data
public class InputSchema implements Serializable {
    private String type;
    private Map<String, Object> properties;
    private List<String> required;
}
```

---

## 5. API 设计

### 5.1 MCP 服务管理

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/mcp-server` | 创建 MCP 服务 |
| PUT | `/mcp-server` | 更新 MCP 服务 |
| DELETE | `/mcp-server/{mcpServerCode}` | 删除 MCP 服务 |
| GET | `/mcp-server/{mcpServerCode}` | 获取服务详情（needTools=true 返回工具列表） |
| GET | `/mcp-server` | 分页获取用户的 MCP 服务列表 |

### 5.2 用户偏好

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/user/preference/mcp` | 获取用户默认启用的 MCP 列表 |
| PUT | `/user/preference/mcp` | 更新用户默认启用的 MCP 列表 |

### 5.3 Agent 对话

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/agent/image/chat/stream` | SSE 流式对话（已扩展 enabledMcpServers 参数） |

---

## 6. 工具加载机制

### 6.1 优先级规则

```
优先级顺序（从高到低）：
1. AgentChatRequest.enabledMcpServers（会话覆盖，非空时直接使用）
2. userMcpPreference.defaultEnabledServers（用户默认偏好）
3. disableMcpDefault=true 时，全部忽略，仅使用 enabledMcpServers
```

### 6.2 TextMcpModelInterceptor（per-request）

```java
@Component
public class TextMcpModelInterceptor implements ModelStartRequestHandler<Prompt> {

    @Override
    public void handle(Prompt prompt, ModelRequestContext context) {
        // 1. 从请求上下文获取 enabledMcpServers
        AgentChatRequest chatRequest = getFromContext();
        List<String> enabledMcpServers = chatRequest.getEnabledMcpServers();

        // 2. 获取用户默认偏好
        if (enabledMcpServers == null || enabledMcpServers.isEmpty()) {
            if (Boolean.TRUE.equals(chatRequest.getDisableMcpDefault())) {
                return; // 不加载任何 MCP 工具
            }
            enabledMcpServers = getUserDefaultMcpServers(chatRequest.getUserId());
        }

        // 3. 合并 + 去重 → MCP ToolCallback 列表
        if (CollectionUtils.isEmpty(enabledMcpServers)) {
            return;
        }
        ToolCallback[] mcpCallbacks = mcpToolCallbackProvider.getCallbacks(enabledMcpServers);

        // 4. 注入 ChatClient 调用
        context.getChatModelOptions().getToolCallbacks(); // 合并到现有工具
    }
}
```

### 6.3 ImageAgent 保持纯净

ImageAgent 只保留 7 个内置 @Tool 注解工具，不直接感知 MCP 工具：

```java
ImageAgent {
    // 7 个内置工具（@Tool 注解）
    - ImageSearchTool
    - ImageGeneratorTool
    - ImageFeatureAnalyzerTool
    - SimilarImageSearchTool
    - QualityEvaluatorTool
    - MermaidDiagramTool
    - UndrawIllustrationTool

    // MCP 工具由 TextMcpModelInterceptor 动态注入
}
```

---

## 7. MCPManager 核心逻辑

### 7.1 创建 MCP 同步客户端

```java
public McpSyncClient getMcpSyncClient(McpServerEntity entity) {
    McpServerDeployConfig deployConfig = JsonUtils.fromJson(
        entity.getDeployConfig(), McpServerDeployConfig.class);

    String remoteEndpoint = deployConfig.getRemoteEndpoint();
    if (StringUtils.isBlank(remoteEndpoint)) {
        remoteEndpoint = "/sse";
    }

    HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(60))
        .build();

    McpClientTransport transport = HttpClientSseClientTransport.builder(entity.getHost())
        .clientBuilder(httpClient)
        .sseEndpoint(remoteEndpoint)
        .build();

    return McpClient.sync(transport)
        .requestTimeout(Duration.ofSeconds(60))
        .initializationTimeout(Duration.ofSeconds(60))
        .capabilities(McpSchema.ClientCapabilities.builder().roots(true).build())
        .build();
}
```

### 7.2 获取 MCP 服务器工具列表

```
1. 检查服务器状态（status=1）
2. Redis 缓存查询（Key: mcp:tools:{userId}:{mcpServerCode}，TTL 5分钟）
3. 缓存未命中时，异步调用 MCP 列表工具接口
4. 缓存结果并返回
```

### 7.3 调用 MCP 工具

```
1. 构建 McpSyncClient
2. 调用 client.callTool(request)
3. 解析 CallToolResult.content（支持 text 类型）
4. 返回 McpServerCallToolResponse
```

---

## 8. Redis 缓存策略

```
用户偏好缓存：
  Key: user:mcp:preference:{userId}
  Value: { "defaultEnabledServers": ["code1", "code2"] }
  TTL: 7 天
  更新时：同时更新数据库和缓存

MCP 工具列表缓存：
  Key: mcp:tools:{userId}:{mcpServerCode}
  Value: List<McpTool>
  TTL: 5 分钟
  更新时：删除缓存，下次请求自动刷新
```

---

## 9. 前端组件

### 9.1 组件列表

| 组件 | 位置 | 职责 |
|------|------|------|
| AgentSettings.vue | frontend/src/components/agent/settings/ | Agent 设置面板（集成 MCP 选择） |
| McpServiceDrawer.vue | frontend/src/components/agent/settings/ | MCP 服务管理抽屉（创建/编辑/删除） |
| McpServerSelector.vue | frontend/src/components/agent/settings/ | MCP 服务多选器 |
| useMcpServers.ts | frontend/src/composables/ | MCP 服务相关 API 调用 |

### 9.2 用户操作入口

```
侧边栏 Footer 设置按钮
└── AgentSettings（设置面板）
    ├── 模型选择
    ├── MCP 服务设置
    │   ├── 管理 MCP 服务（打开 McpServiceDrawer）
    │   └── 默认启用的 MCP（打开 McpServerSelector）
    └── 当前会话 MCP（临时调整，会话级别）
```

### 9.3 会话级别临时调整

```
AgentSettings 面板（对话过程中可见）
└── 当前会话 MCP
    ├── 已启用列表（可取消勾选）
    └── 可添加更多（打开 McpServerSelector）
```

---

## 10. deploy_config 配置格式

### 10.1 标准格式

```json
{
  "mcpServers": {
    "my-mcp-service": {
      "url": "https://mcp.jina.ai/sse",
      "headers": {
        "Authorization": "Bearer ${API_KEY}"
      }
    }
  }
}
```

### 10.2 常用 MCP 服务示例

#### Jina Reader
```json
{
  "mcpServers": {
    "jina-reader": {
      "url": "https://mcp.jina.ai/sse",
      "headers": {}
    }
  }
}
```

#### 高德地图
```json
{
  "mcpServers": {
    "amap-maps": {
      "url": "https://mcp.amap.com/sse",
      "headers": {
        "Authorization": "Bearer ${AMAP_API_KEY}"
      }
    }
  }
}
```

---

## 11. 后端文件清单

| 序号 | 文件路径 | 说明 | 优先级 |
|------|----------|------|--------|
| 1 | `.../controller/McpServerController.java` | MCP 服务 REST API | 必选 |
| 2 | `.../controller/UserMcpPreferenceController.java` | 用户偏好 API | 必选 |
| 3 | `.../service/McpServerService.java` | MCP 服务接口 | 必选 |
| 4 | `.../service/impl/McpServerServiceImpl.java` | MCP 服务实现 | 必选 |
| 5 | `.../service/UserMcpPreferenceService.java` | 用户偏好接口 | 必选 |
| 6 | `.../service/impl/UserMcpPreferenceServiceImpl.java` | 用户偏好实现 | 必选 |
| 7 | `.../manager/MCPManager.java` | MCP 核心客户端管理器 | 必选 |
| 8 | `.../agent/tool/McpToolCallback.java` | MCP 工具回调 | 必选 |
| 9 | `.../agent/tool/McpToolCallbackProvider.java` | MCP 工具提供者 | 必选 |
| 10 | `.../agent/interceptor/TextMcpModelInterceptor.java` | 请求拦截器（动态注入工具） | 必选 |
| 11 | `.../entity/McpServerEntity.java` | MCP 服务数据库实体 | 必选 |
| 12 | `.../entity/UserMcpPreferenceEntity.java` | 用户偏好数据库实体 | 必选 |
| 13 | `.../mapper/McpServerMapper.java` | MyBatis Mapper | 必选 |
| 14 | `.../mapper/UserMcpPreferenceMapper.java` | 用户偏好 Mapper | 必选 |
| 15 | `.../domain/mcp/McpServerDetail.java` | MCP 详情 DTO | 必选 |
| 16 | `.../domain/mcp/McpServerDeployConfig.java` | 部署配置 DTO | 必选 |
| 17 | `.../domain/mcp/McpTool.java` | MCP 工具 DTO | 必选 |
| 18 | `.../domain/mcp/McpServerCallToolRequest.java` | 工具调用请求 | 必选 |
| 19 | `.../domain/mcp/McpServerCallToolResponse.java` | 工具调用响应 | 必选 |

---

## 12. 错误处理

| 错误码 | 说明 |
|--------|------|
| `MCP_SERVER_NOT_FOUND` | MCP 服务不存在 |
| `MCP_CONFIG_PARSE_ERROR` | 配置解析错误（JSON 格式错误） |
| `MCP_URL_PARSE_ERROR` | URL 格式错误 |
| `MCP_GET_TOOLS_ERROR` | 获取工具列表失败 |
| `MCP_TOOL_CALL_ERROR` | 工具调用失败 |
| `MCP_CONNECTION_TIMEOUT` | MCP 服务器连接超时 |

---

## 13. 限制与约束

| 限制项 | 值 | 说明 |
|--------|-----|------|
| 每个用户最多 MCP 服务数 | 20 | 防止资源浪费 |
| 每会话最多启用 MCP 数 | 5 | 防止工具过多影响性能 |
| MCP 连接超时 | 60s | 包括初始化和请求超时 |
| 工具列表缓存 TTL | 5min | 平衡实时性和性能 |
| 用户偏好缓存 TTL | 7天 | 长期偏好无需频繁刷新 |

---

## 14. 参考资料

- Spring AI Alibaba Admin MCP 实现：`mcp-integration-guide.md`
- Spring AI Alibaba 实现详解：`mcp-user-configuration.md`
- Spring AI MCP Client SDK：`io.modelcontextprotocol:mcp-client`
