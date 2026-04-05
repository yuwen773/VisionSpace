# MCP 用户配置服务实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在现有 VisionSpace Agent 模块上集成 MCP 服务配置功能，允许用户通过前端管理 MCP 服务器并为 Agent 动态添加工具。

**Architecture:** 采用 ToolCallback Provider 模式，将 MCP 工具适配为 Spring AI 的 ToolCallback。ImageAgent 保持纯净（仅内置 @Tool 工具），MCP 工具由 TextMcpModelInterceptor 在 per-request 层面动态注入。

**Tech Stack:** Spring Boot 3.5, Java 21, Spring AI 1.1.2, MyBatis Plus 3.5, Redis, MCP Client SDK (io.modelcontextprotocol:mcp-client)

---

## Phase 1: 数据库与基础实体

### Task 1: 创建 mcp_server 表

**Files:**
- Create SQL: `sql/mcp_server.sql`

**Step 1: 创建 SQL 脚本**

```sql
-- sql/mcp_server.sql
CREATE TABLE `mcp_server` (
  `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
  `mcpServerCode`  VARCHAR(64)  NOT NULL COMMENT '服务器编码',
  `userId`         BIGINT      NOT NULL COMMENT '所属用户ID',
  `name`           VARCHAR(128) NOT NULL COMMENT '服务器名称',
  `description`    VARCHAR(512) COMMENT '描述',
  `deployConfig`   TEXT         NOT NULL COMMENT '部署配置(JSON)',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用 1=启用 3=删除',
  `installType`    VARCHAR(32)  NOT NULL DEFAULT 'SSE' COMMENT '安装类型',
  `host`           VARCHAR(256) COMMENT 'MCP服务器地址',
  `gmtCreate`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmtModified`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_mcp_server_code_user` (`mcpServerCode`, `userId`),
  KEY `idx_user_id` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP服务器配置表';
```

**Step 2: 执行 SQL**

Run: `mysql -u root -p vision_space < sql/mcp_server.sql`

---

### Task 2: 创建 user_mcp_preference 表

**Files:**
- Create SQL: `sql/user_mcp_preference.sql`

**Step 1: 创建 SQL 脚本**

```sql
-- sql/user_mcp_preference.sql
CREATE TABLE `user_mcp_preference` (
  `id`                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  `userId`               BIGINT      NOT NULL UNIQUE COMMENT '用户ID',
  `defaultEnabledServers` TEXT        COMMENT '默认启用的MCP服务列表(JSON数组)',
  `gmtCreate`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmtModified`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户MCP偏好配置表';
```

---

### Task 3: 创建 McpServerEntity

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/entity/McpServerEntity.java`

**Step 1: 编写实体类**

```java
package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("mcp_server")
public class McpServerEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("mcpServerCode")
    private String mcpServerCode;

    @TableField("userId")
    private Long userId;

    private String name;

    private String description;

    @TableField("deployConfig")
    private String deployConfig;

    private Integer status;

    @TableField("installType")
    private String installType;

    private String host;

    @TableField(value = "gmtCreate", fill = FieldFill.INSERT)
    private Date gmtCreate;

    @TableField(value = "gmtModified", fill = FieldFill.INSERT_UPDATE)
    private Date gmtModified;
}
```

---

### Task 4: 创建 UserMcpPreferenceEntity

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/entity/UserMcpPreferenceEntity.java`

**Step 1: 编写实体类**

```java
package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user_mcp_preference")
public class UserMcpPreferenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("userId")
    private Long userId;

    @TableField("defaultEnabledServers")
    private String defaultEnabledServers;

    @TableField(value = "gmtCreate", fill = FieldFill.INSERT)
    private Date gmtCreate;

    @TableField(value = "gmtModified", fill = FieldFill.INSERT_UPDATE)
    private Date gmtModified;
}
```

---

### Task 5: 创建 MCP 相关 DTO

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/dto/mcp/McpServerDetail.java`
- Create: `src/main/java/com/yuwen/visionspace/model/dto/mcp/McpServerDeployConfig.java`
- Create: `src/main/java/com/yuwen/visionspace/model/dto/mcp/McpTool.java`
- Create: `src/main/java/com/yuwen/visionspace/model/dto/mcp/InputSchema.java`
- Create: `src/main/java/com/yuwen/visionspace/model/dto/mcp/McpServerCallToolRequest.java`
- Create: `src/main/java/com/yuwen/visionspace/model/dto/mcp/McpServerCallToolResponse.java`

---

## Phase 2: Mapper 层

### Task 6: 创建 Mapper 接口

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/mapper/McpServerMapper.java`
- Create: `src/main/java/com/yuwen/visionspace/mapper/UserMcpPreferenceMapper.java`

**Step 1: McpServerMapper**

```java
package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.McpServerEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface McpServerMapper extends BaseMapper<McpServerEntity> {
}
```

**Step 2: UserMcpPreferenceMapper**

```java
package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.UserMcpPreferenceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMcpPreferenceMapper extends BaseMapper<UserMcpPreferenceEntity> {
}
```

---

## Phase 3: Service 层

### Task 7: 创建 McpServerService 接口

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/service/McpServerService.java`

**Step 1: 定义接口**

```java
package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.model.dto.mcp.*;

public interface McpServerService {

    String createMcp(McpServerDetail detail, Long userId);

    void updateMcp(McpServerDetail detail, Long userId);

    void deleteMcp(String mcpServerCode, Long userId);

    McpServerDetail getMcp(String mcpServerCode, Long userId, boolean needTools);

    Page<McpServerDetail> listMcpServers(Long userId, String name, int current, int size);

    List<McpTool> getTools(String mcpServerCode, Long userId);

    McpServerCallToolResponse callTool(McpServerCallToolRequest request, Long userId);
}
```

---

### Task 8: 创建 McpServerServiceImpl

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/service/impl/McpServerServiceImpl.java`

**Step 1: 实现类（核心逻辑）**

```java
package com.yuwen.visionspace.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.agent.service.McpServerService;
import com.yuwen.visionspace.mapper.McpServerMapper;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServerEntity;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper mcpServerMapper;
    private final MCPManager mcpManager;

    @Override
    public String createMcp(McpServerDetail detail, Long userId) {
        // 1. 生成唯一标识
        String mcpServerCode = IdGenerator.idStr();

        // 2. 解析并验证配置
        String processedConfig = processDeployConfig(detail.getDeployConfig());
        String host = extractHost(detail.getDeployConfig());

        // 3. 构建实体
        McpServerEntity entity = new McpServerEntity();
        entity.setMcpServerCode(mcpServerCode);
        entity.setUserId(userId);
        entity.setName(detail.getName());
        entity.setDescription(detail.getDescription());
        entity.setDeployConfig(processedConfig);
        entity.setHost(host);
        entity.setStatus(1);
        entity.setInstallType(StringUtils.hasText(detail.getInstallType()) ? detail.getInstallType() : "SSE");

        mcpServerMapper.insert(entity);
        return mcpServerCode;
    }

    @Override
    public void deleteMcp(String mcpServerCode, Long userId) {
        McpServerEntity entity = getByCodeAndUser(mcpServerCode, userId);
        entity.setStatus(3);
        mcpServerMapper.updateById(entity);
    }

    @Override
    public McpServerDetail getMcp(String mcpServerCode, Long userId, boolean needTools) {
        McpServerEntity entity = getByCodeAndUser(mcpServerCode, userId);
        McpServerDetail detail = new McpServerDetail();
        detail.setMcpServerCode(entity.getMcpServerCode());
        detail.setName(entity.getName());
        detail.setDescription(entity.getDescription());
        detail.setDeployConfig(entity.getDeployConfig());
        detail.setStatus(entity.getStatus());
        detail.setInstallType(entity.getInstallType());
        detail.setHost(entity.getHost());

        if (needTools) {
            detail.setTools(mcpManager.getTools(entity));
        }
        return detail;
    }

    private McpServerEntity getByCodeAndUser(String mcpServerCode, Long userId) {
        return mcpServerMapper.selectOne(
            new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getMcpServerCode, mcpServerCode)
                .eq(McpServerEntity::getUserId, userId)
                .ne(McpServerEntity::getStatus, 3)
        );
    }

    private String processDeployConfig(String deployConfig) {
        Map<String, Object> config = JsonUtils.fromJsonToMap(deployConfig);
        if (config == null || !config.containsKey("mcpServers")) {
            throw new IllegalArgumentException("Invalid deployConfig: mcpServers is required");
        }
        return deployConfig;
    }

    private String extractHost(String deployConfig) {
        Map<String, Object> config = JsonUtils.fromJsonToMap(deployConfig);
        Map<String, Object> mcpServers = (Map<String, Object>) config.get("mcpServers");
        if (mcpServers != null && !mcpServers.isEmpty()) {
            Map<String, Object> firstServer = (Map<String, Object>) mcpServers.values().iterator().next();
            String url = (String) firstServer.get("url");
            if (url != null) {
                try {
                    URL urlObj = new URL(url);
                    String host = urlObj.getProtocol() + "://" + urlObj.getHost();
                    if (urlObj.getPort() != -1) {
                        host += ":" + urlObj.getPort();
                    }
                    return host;
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return "";
    }
}
```

---

### Task 9: 创建 UserMcpPreferenceService

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/service/UserMcpPreferenceService.java`
- Create: `src/main/java/com/yuwen/visionspace/agent/service/impl/UserMcpPreferenceServiceImpl.java`

**Step 1: 接口定义**

```java
package com.yuwen.visionspace.agent.service;

import java.util.List;

public interface UserMcpPreferenceService {

    List<String> getDefaultEnabledServers(Long userId);

    void setDefaultEnabledServers(Long userId, List<String> mcpServerCodes);
}
```

**Step 2: 实现类（含 Redis 缓存）**

```java
package com.yuwen.visionspace.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yuwen.visionspace.agent.service.UserMcpPreferenceService;
import com.yuwen.visionspace.mapper.UserMcpPreferenceMapper;
import com.yuwen.visionspace.model.entity.UserMcpPreferenceEntity;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMcpPreferenceServiceImpl implements UserMcpPreferenceService {

    private static final String REDIS_KEY_PREFIX = "user:mcp:preference:";
    private static final long CACHE_TTL_DAYS = 7;

    private final UserMcpPreferenceMapper preferenceMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public List<String> getDefaultEnabledServers(Long userId) {
        String cacheKey = REDIS_KEY_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return JsonUtils.fromJson(cached, new TypeReference<List<String>>() {});
        }

        UserMcpPreferenceEntity entity = preferenceMapper.selectOne(
            new LambdaQueryWrapper<UserMcpPreferenceEntity>()
                .eq(UserMcpPreferenceEntity::getUserId, userId)
        );

        List<String> servers = null;
        if (entity != null && entity.getDefaultEnabledServers() != null) {
            servers = JsonUtils.fromJson(entity.getDefaultEnabledServers(),
                new TypeReference<List<String>>() {});
        }

        // 缓存结果
        String toCache = servers != null ? JsonUtils.toJson(servers) : "[]";
        redisTemplate.opsForValue().set(cacheKey, toCache, CACHE_TTL_DAYS, java.util.concurrent.TimeUnit.DAYS);

        return servers;
    }

    @Override
    public void setDefaultEnabledServers(Long userId, List<String> mcpServerCodes) {
        UserMcpPreferenceEntity entity = preferenceMapper.selectOne(
            new LambdaQueryWrapper<UserMcpPreferenceEntity>()
                .eq(UserMcpPreferenceEntity::getUserId, userId)
        );

        if (entity == null) {
            entity = new UserMcpPreferenceEntity();
            entity.setUserId(userId);
            entity.setDefaultEnabledServers(JsonUtils.toJson(mcpServerCodes));
            preferenceMapper.insert(entity);
        } else {
            entity.setDefaultEnabledServers(JsonUtils.toJson(mcpServerCodes));
            preferenceMapper.updateById(entity);
        }

        // 刷新缓存
        String cacheKey = REDIS_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(mcpServerCodes), CACHE_TTL_DAYS, java.util.concurrent.TimeUnit.DAYS);
    }
}
```

---

## Phase 4: MCPManager 核心

### Task 10: 创建 MCPManager

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/manager/MCPManager.java`

**Step 1: 核心 MCP 客户端管理器**

```java
package com.yuwen.visionspace.agent.manager;

import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServerEntity;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MCPManager {

    private static final String TOOLS_CACHE_PREFIX = "mcp:tools:";
    private static final long TOOLS_CACHE_TTL_MINUTES = 5;

    private final StringRedisTemplate redisTemplate;
    private final ExecutorService mcpExecutor = Executors.newCachedThreadPool();

    public McpSyncClient getMcpSyncClient(McpServerEntity entity) {
        McpServerDeployConfig deployConfig = parseConfig(entity.getDeployConfig());

        String remoteEndpoint = deployConfig.getRemoteEndpoint();
        if (remoteEndpoint == null || remoteEndpoint.isBlank()) {
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

    public List<McpTool> getTools(McpServerEntity entity) {
        if (entity == null || !entity.getStatus().equals(1)) {
            return Collections.emptyList();
        }

        String cacheKey = TOOLS_CACHE_PREFIX + entity.getUserId() + ":" + entity.getMcpServerCode();
        List<McpTool> cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            List<McpTool> tools = CompletableFuture.supplyAsync(() -> fetchTools(entity), mcpExecutor).get(30, TimeUnit.SECONDS);
            if (tools != null && !tools.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(tools), TOOLS_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            return tools;
        } catch (Exception e) {
            log.error("Failed to get MCP tools for server: {}", entity.getMcpServerCode(), e);
            return Collections.emptyList();
        }
    }

    private List<McpTool> fetchTools(McpServerEntity entity) {
        McpSyncClient client = getMcpSyncClient(entity);
        try {
            client.initialize();
            McpSchema.ListToolsResult result = client.listTools();
            List<McpTool> tools = new ArrayList<>();

            for (McpSchema.Tool t : result.tools()) {
                McpTool tool = new McpTool();
                tool.setName(t.name());
                tool.setDescription(t.description());

                InputSchema schema = new InputSchema();
                schema.setType(t.inputSchema().type());
                schema.setProperties(t.inputSchema().properties());
                schema.setRequired(t.inputSchema().required());
                tool.setInputSchema(schema);

                tools.add(tool);
            }
            return tools;
        } finally {
            client.close();
        }
    }

    public McpServerCallToolResponse callTool(McpServerCallToolRequest request, McpServerEntity entity) {
        McpSyncClient client = getMcpSyncClient(entity);
        McpServerCallToolResponse response = new McpServerCallToolResponse();

        try {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest(request.getToolName(), request.getToolParams())
            );

            response.setIsError(result.isError());

            List<Content> contents = new ArrayList<>();
            for (McpSchema.Content c : result.content()) {
                if ("text".equals(c.type())) {
                    TextContent text = new TextContent();
                    text.setType("text");
                    text.setText(((McpSchema.TextContent) c).text());
                    contents.add(text);
                }
            }
            response.setContent(contents);
        } catch (Exception e) {
            log.error("MCP tool call failed: {}", request.getToolName(), e);
            response.setIsError(true);
            TextContent text = new TextContent();
            text.setType("text");
            text.setText("Tool call failed: " + e.getMessage());
            response.setContent(List.of(text));
        } finally {
            client.close();
        }
        return response;
    }

    private McpServerDeployConfig parseConfig(String configJson) {
        return JsonUtils.fromJson(configJson, McpServerDeployConfig.class);
    }

    public void invalidateToolsCache(Long userId, String mcpServerCode) {
        String cacheKey = TOOLS_CACHE_PREFIX + userId + ":" + mcpServerCode;
        redisTemplate.delete(cacheKey);
    }
}
```

---

## Phase 5: Controller 层

### Task 11: 创建 McpServerController

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/controller/McpServerController.java`

**Step 1: REST API 控制器**

```java
package com.yuwen.visionspace.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuwen.visionspace.agent.service.McpServerService;
import com.yuwen.visionspace.common.Result;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mcp-server")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;

    @PostMapping
    public Result<String> createMcpServer(@RequestBody McpServerDetail detail) {
        validateCreateParams(detail);
        Long userId = getCurrentUserId();
        String serverCode = mcpServerService.createMcp(detail, userId);
        return Result.success(serverCode);
    }

    @PutMapping
    public Result<Void> updateMcpServer(@RequestBody McpServerDetail detail) {
        validateUpdateParams(detail);
        Long userId = getCurrentUserId();
        mcpServerService.updateMcp(detail, userId);
        return Result.success();
    }

    @DeleteMapping("/{mcpServerCode}")
    public Result<Void> deleteMcpServer(@PathVariable String mcpServerCode) {
        Long userId = getCurrentUserId();
        mcpServerService.deleteMcp(mcpServerCode, userId);
        return Result.success();
    }

    @GetMapping("/{mcpServerCode}")
    public Result<McpServerDetail> getMcpServer(
            @PathVariable String mcpServerCode,
            @RequestParam(value = "needTools", defaultValue = "false") boolean needTools) {
        Long userId = getCurrentUserId();
        McpServerDetail detail = mcpServerService.getMcp(mcpServerCode, userId, needTools);
        return Result.success(detail);
    }

    @GetMapping
    public Result<IPage<McpServerDetail>> listMcpServers(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Long userId = getCurrentUserId();
        IPage<McpServerDetail> page = mcpServerService.listMcpServers(userId, name, current, size);
        return Result.success(page);
    }

    private void validateCreateParams(McpServerDetail detail) {
        if (!StringUtils.hasText(detail.getName())) {
            throw new IllegalArgumentException("name is required");
        }
        if (!StringUtils.hasText(detail.getDeployConfig())) {
            throw new IllegalArgumentException("deployConfig is required");
        }
        JsonUtils.fromJsonToMap(detail.getDeployConfig()); // 验证 JSON
    }

    private void validateUpdateParams(McpServerDetail detail) {
        validateCreateParams(detail);
        if (!StringUtils.hasText(detail.getMcpServerCode())) {
            throw new IllegalArgumentException("mcpServerCode is required");
        }
    }

    private Long getCurrentUserId() {
        // 从安全上下文获取当前用户ID
        return 1L; // TODO: 实现真实用户上下文
    }
}
```

---

### Task 12: 创建 UserMcpPreferenceController

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/controller/UserMcpPreferenceController.java`

**Step 1: 用户偏好 API**

```java
package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.agent.service.UserMcpPreferenceService;
import com.yuwen.visionspace.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/preference/mcp")
@RequiredArgsConstructor
public class UserMcpPreferenceController {

    private final UserMcpPreferenceService userMcpPreferenceService;

    @GetMapping
    public Result<List<String>> getDefaultEnabledServers() {
        Long userId = getCurrentUserId();
        List<String> servers = userMcpPreferenceService.getDefaultEnabledServers(userId);
        return Result.success(servers);
    }

    @PutMapping
    public Result<Void> setDefaultEnabledServers(@RequestBody List<String> mcpServerCodes) {
        Long userId = getCurrentUserId();
        userMcpPreferenceService.setDefaultEnabledServers(userId, mcpServerCodes);
        return Result.success();
    }

    private Long getCurrentUserId() {
        return 1L; // TODO: 实现真实用户上下文
    }
}
```

---

## Phase 6: MCP 工具回调

### Task 13: 创建 McpToolCallback

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/tool/McpToolCallback.java`

**Step 1: 单个 MCP 工具回调**

```java
package com.yuwen.visionspace.agent.tool;

import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServerEntity;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolMetadata;
import org.springframework.ai.tool.support.AbstractToolCallback;

import java.util.Map;

public class McpToolCallback extends AbstractToolCallback {

    private final MCPManager mcpManager;
    private final McpServerEntity mcpServerEntity;
    private final McpTool mcpTool;

    public McpToolCallback(MCPManager mcpManager, McpServerEntity entity, McpTool tool) {
        this.mcpManager = mcpManager;
        this.mcpServerEntity = entity;
        this.mcpTool = tool;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
            .name(mcpTool.getName())
            .description(mcpTool.getDescription())
            .inputSchema(JsonUtils.toJson(mcpTool.getInputSchema()))
            .build();
    }

    @Override
    public ToolMetadata getMetadata() {
        return ToolMetadata.builder().returnDirect(false).build();
    }

    @Override
    public String call(String functionInput) {
        Map<String, Object> params = parseInput(functionInput);

        McpServerCallToolRequest request = new McpServerCallToolRequest();
        request.setToolName(mcpTool.getName());
        request.setToolParams(params);

        McpServerCallToolResponse response = mcpManager.callTool(request, mcpServerEntity);
        return formatResponse(response);
    }

    private Map<String, Object> parseInput(String functionInput) {
        if (functionInput == null || functionInput.isBlank()) {
            return Map.of();
        }
        try {
            return JsonUtils.fromJsonToMap(functionInput);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String formatResponse(McpServerCallToolResponse response) {
        if (response.getContent() == null || response.getContent().isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        for (Content c : response.getContent()) {
            if (c instanceof TextContent) {
                sb.append(((TextContent) c).getText());
            }
        }
        return sb.toString();
    }
}
```

---

### Task 14: 创建 McpToolCallbackProvider

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/tool/McpToolCallbackProvider.java`

**Step 1: MCP 工具提供者**

```java
package com.yuwen.visionspace.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.agent.service.McpServerService;
import com.yuwen.visionspace.mapper.McpServerMapper;
import com.yuwen.visionspace.model.dto.mcp.McpServerDetail;
import com.yuwen.visionspace.model.dto.mcp.McpTool;
import com.yuwen.visionspace.model.entity.McpServerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class McpToolCallbackProvider {

    private final McpServerService mcpServerService;
    private final MCPManager mcpManager;
    private final McpServerMapper mcpServerMapper;

    public ToolCallback[] getCallbacks(List<String> mcpServerCodes) {
        if (mcpServerCodes == null || mcpServerCodes.isEmpty()) {
            return new ToolCallback[0];
        }

        List<ToolCallback> callbacks = new ArrayList<>();

        for (String serverCode : mcpServerCodes) {
            try {
                McpServerEntity entity = mcpServerMapper.selectOne(
                    new LambdaQueryWrapper<McpServerEntity>()
                        .eq(McpServerEntity::getMcpServerCode, serverCode)
                        .eq(McpServerEntity::getStatus, 1)
                );

                if (entity == null) {
                    continue;
                }

                List<McpTool> tools = mcpManager.getTools(entity);
                if (tools != null) {
                    for (McpTool tool : tools) {
                        callbacks.add(new McpToolCallback(mcpManager, entity, tool));
                    }
                }
            } catch (Exception e) {
                // 单个服务失败不影响其他服务
            }
        }

        return callbacks.toArray(new ToolCallback[0]);
    }
}
```

---

## Phase 7: 请求拦截器

### Task 15: 创建 TextMcpModelInterceptor

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/interceptor/TextMcpModelInterceptor.java`

**Step 1: per-request MCP 工具注入拦截器**

```java
package com.yuwen.visionspace.agent.interceptor;

import com.yuwen.visionspace.agent.service.McpServerService;
import com.yuwen.visionspace.agent.service.UserMcpPreferenceService;
import com.yuwen.visionspace.agent.tool.McpToolCallbackProvider;
import com.yuwen.visionspace.common.RequestContextHolder;
import com.yuwen.visionspace.model.dto.agent.AgentChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.tool.ToolCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.model.ChatModelRequestContext;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.model.chat.metadata.ChatModelOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TextMcpModelInterceptor {

    private final UserMcpPreferenceService userMcpPreferenceService;
    private final McpToolCallbackProvider mcpToolCallbackProvider;

    public ToolCallback[] resolveMcpCallbacks(AgentChatRequest chatRequest, Long userId) {
        List<String> enabledMcpServers = chatRequest.getEnabledMcpServers();

        // 优先级1: 会话覆盖
        if (enabledMcpServers != null && !enabledMcpServers.isEmpty()) {
            return mcpToolCallbackProvider.getCallbacks(enabledMcpServers);
        }

        // 优先级2: 用户默认偏好
        if (!Boolean.TRUE.equals(chatRequest.getDisableMcpDefault())) {
            List<String> defaultServers = userMcpPreferenceService.getDefaultEnabledServers(userId);
            if (defaultServers != null && !defaultServers.isEmpty()) {
                return mcpToolCallbackProvider.getCallbacks(defaultServers);
            }
        }

        return new ToolCallback[0];
    }
}
```

---

## Phase 8: Agent 对话接口扩展

### Task 16: 扩展 AgentChatRequest

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/model/dto/agent/AgentChatRequest.java`

**Step 1: 添加 MCP 相关字段**

```java
@Data
public class AgentChatRequest implements Serializable {
    private String sessionId;
    private String message;
    private String modelName;

    // 新增 MCP 字段
    private List<String> enabledMcpServers;  // 会话级别 MCP 覆盖
    private Boolean disableMcpDefault;         // 是否禁用默认 MCP
}
```

---

## Phase 9: 前端组件

### Task 17: 创建 MCP 管理抽屉组件

**Files:**
- Create: `frontend/src/components/agent/settings/McpServiceDrawer.vue`

**Step 1: MCP 服务管理抽屉**

```vue
<template>
  <a-drawer
    v-model:open="visible"
    title="管理 MCP 服务"
    width="600"
    @close="handleClose"
  >
    <div class="mcp-actions">
      <a-button type="primary" @click="handleCreate">新增 MCP 服务</a-button>
    </div>

    <a-list :data-source="serverList" :loading="loading">
      <template #renderItem="{ item }">
        <a-list-item>
          <a-list-item-meta :title="item.name" :description="item.description" />
          <template #actions>
            <a @click="handleEdit(item)">编辑</a>
            <a @click="handleDelete(item.mcpServerCode)">删除</a>
          </template>
        </a-list-item>
      </template>
    </a-list>

    <a-drawer v-model:open="drawerVisible" :title="isEdit ? '编辑 MCP 服务' : '新增 MCP 服务'" size="large">
      <a-form :form="form" layout="vertical">
        <a-form-item label="服务名称" name="name" :rules="[{ required: true }]">
          <a-input v-model:value="form.name" placeholder="请输入服务名称" />
        </a-form-item>
        <a-form-item label="描述" name="description">
          <a-textarea v-model:value="form.description" :rows="2" />
        </a-form-item>
        <a-form-item label="安装类型">
          <a-select v-model:value="form.installType">
            <a-select-option value="SSE">SSE</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="配置方式">
          <a-radio-group v-model:value="configMode">
            <a-radio value="form">表单配置</a-radio>
            <a-radio value="json">JSON 配置</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="configMode === 'json'" label="MCP 服务配置 (JSON)" name="deployConfig" :rules="[{ required: true }]">
          <a-textarea v-model:value="form.deployConfig" :rows="8" placeholder='{"mcpServers": {...}}' />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button @click="drawerVisible = false">取消</a-button>
        <a-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</a-button>
      </template>
    </a-drawer>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const visible = defineModel<boolean>('open')
const emit = defineEmits(['update'])

const serverList = ref([])
const loading = ref(false)
const drawerVisible = ref(false)
const isEdit = ref(false)
const configMode = ref('json')
const submitLoading = ref(false)
const form = ref({
  mcpServerCode: '',
  name: '',
  description: '',
  installType: 'SSE',
  deployConfig: '{"mcpServers": {}}'
})

const handleCreate = () => {
  isEdit.value = false
  form.value = { mcpServerCode: '', name: '', description: '', installType: 'SSE', deployConfig: '{"mcpServers": {}}' }
  drawerVisible.value = true
}

const handleEdit = (item) => {
  isEdit.value = true
  form.value = { ...item }
  drawerVisible.value = true
}

const handleDelete = async (code) => {
  await deleteMcpServer(code)
  fetchList()
}

const handleSubmit = async () => {
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateMcpServer(form.value)
    } else {
      await createMcpServer(form.value)
    }
    drawerVisible.value = false
    fetchList()
  } finally {
    submitLoading.value = false
  }
}

const handleClose = () => {
  emit('update')
}
</script>
```

---

### Task 18: 创建 MCP 选择器组件

**Files:**
- Create: `frontend/src/components/agent/settings/McpServerSelector.vue`

---

### Task 19: 创建 Agent 设置面板

**Files:**
- Create: `frontend/src/components/agent/settings/AgentSettings.vue`
- Modify: `frontend/src/components/agent/AgentChatLayout.vue` (添加设置入口)

---

## Phase 10: API 层

### Task 20: 创建前端 API

**Files:**
- Create: `frontend/src/api/mcpController.ts`

**Step 1: API 定义**

```typescript
import { request } from '@/utils/request'

export const createMcpServer = (data: any) =>
  request('/mcp-server', { method: 'POST', data })

export const updateMcpServer = (data: any) =>
  request('/mcp-server', { method: 'PUT', data })

export const deleteMcpServer = (mcpServerCode: string) =>
  request(`/mcp-server/${mcpServerCode}`, { method: 'DELETE' })

export const getMcpServer = (mcpServerCode: string, needTools = false) =>
  request(`/mcp-server/${mcpServerCode}`, { params: { needTools } })

export const listMcpServers = (params: { name?: string; current?: number; size?: number }) =>
  request('/mcp-server', { params })

export const getUserMcpPreference = () =>
  request('/user/preference/mcp')

export const setUserMcpPreference = (data: string[]) =>
  request('/user/preference/mcp', { method: 'PUT', data })
```

---

## Phase 11: 测试验证

### Task 21: 验证 MCP 服务 CRUD

**Step 1: 使用 curl 测试**

```bash
# 创建 MCP 服务
curl -X POST http://localhost:8080/mcp-server \
  -H "Content-Type: application/json" \
  -d '{"name":"Jina Reader","description":"网页内容提取","deployConfig":"{\"mcpServers\":{\"jina-reader\":{\"url\":\"https://mcp.jina.ai/sse\",\"headers\":{}}}}","installType":"SSE"}'

# 获取服务详情（含工具列表）
curl http://localhost:8080/mcp-server/{serverCode}?needTools=true

# 列出用户所有 MCP 服务
curl http://localhost:8080/mcp-server

# 删除服务
curl -X DELETE http://localhost:8080/mcp-server/{serverCode}
```

### Task 22: 验证 Agent 对话使用 MCP

**Step 1: 对话请求中传入 enabledMcpServers**

```bash
curl -X POST http://localhost:8080/agent/image/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"帮我总结一下 https://example.com 的内容","sessionId":"test","enabledMcpServers":["{serverCode}"]}'
```

---

## 执行方式选择

**Plan complete and saved to `docs/plans/2026-04-04-mcp-user-configuration-implementation.md`**

**Two execution options:**

1. **Subagent-Driven (this session)** — I dispatch fresh subagent per task, review between tasks, fast iteration

2. **Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints

**Which approach?**
