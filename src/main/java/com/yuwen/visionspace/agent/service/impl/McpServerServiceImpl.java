package com.yuwen.visionspace.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.agent.mcp.McpCapabilityGuard;
import com.yuwen.visionspace.agent.mcp.McpServerAccess;
import com.yuwen.visionspace.agent.service.McpServerService;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.outbound.OutboundPolicy;
import com.yuwen.visionspace.mapper.McpServerMapper;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServer;
import com.yuwen.visionspace.utils.IdGenerator;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper mcpServerMapper;
    private final MCPManager mcpManager;
    private final McpServerAccess serverAccess;
    private final McpCapabilityGuard capabilityGuard;
    private final OutboundPolicy outboundPolicy;

    @Override
    public String createMcp(McpServerDetail detail, Long userId) {
        // 危险能力与出站目标在落库前拒绝：生产不保存 STDIO 配置，
        // 也不会把指向受限地址的远程目标保存下来
        validateDeployConfig(detail);

        String mcpServerCode = IdGenerator.idStr();
        String host = extractHost(detail.getDeployConfig());

        McpServer entity = new McpServer();
        entity.setMcpServerCode(mcpServerCode);
        entity.setUserId(userId);
        entity.setName(detail.getName());
        entity.setDescription(detail.getDescription());
        entity.setDeployConfig(detail.getDeployConfig());
        entity.setHost(host);
        entity.setStatus(resolveStatus(detail.getStatus()));
        entity.setInstallType(resolveInstallType(detail));
        entity.setGmtCreate(new Date());
        entity.setGmtModified(new Date());

        mcpServerMapper.insert(entity);
        return mcpServerCode;
    }

    @Override
    public void updateMcp(McpServerDetail detail, Long userId) {
        McpServer entity = getByCodeAndUser(detail.getMcpServerCode(), userId);
        boolean configChanged = !Objects.equals(entity.getDeployConfig(), detail.getDeployConfig())
                || !Objects.equals(entity.getInstallType(), resolveInstallType(detail));
        // 只改启用状态时不重新校验旧配置：否则生产里一条历史 STDIO 配置
        // 会因为「无法保存」而连停用都做不到，反而一直保持启用
        if (configChanged) {
            validateDeployConfig(detail);
        }
        String host = configChanged ? extractHost(detail.getDeployConfig()) : entity.getHost();

        entity.setName(detail.getName());
        entity.setDescription(detail.getDescription());
        entity.setDeployConfig(detail.getDeployConfig());
        entity.setHost(host);
        entity.setInstallType(resolveInstallType(detail));
        if (detail.getStatus() != null) {
            entity.setStatus(resolveStatus(detail.getStatus()));
        }
        entity.setGmtModified(new Date());

        mcpServerMapper.updateById(entity);
        // 部署配置/名称变更后必须让缓存的工具列表失效，否则最多 5 分钟内仍返回旧工具
        mcpManager.invalidateToolsCache(userId, detail.getMcpServerCode());
    }

    @Override
    public void deleteMcp(String mcpServerCode, Long userId) {
        McpServer entity = getByCodeAndUser(mcpServerCode, userId);
        entity.setStatus(McpServerAccess.STATUS_DELETED);
        mcpServerMapper.updateById(entity);
        // 已删除的服务不应继续命中工具缓存
        mcpManager.invalidateToolsCache(userId, mcpServerCode);
    }

    @Override
    public McpServerDetail getMcp(String mcpServerCode, Long userId, boolean needTools) {
        McpServer entity = getByCodeAndUser(mcpServerCode, userId);
        McpServerDetail detail = convertToDetail(entity);
        if (needTools) {
            // 预览同样校验归属与启用状态：停用服务不触发工具发现，
            // 被禁用的能力即使处于启用状态也不建立连接
            McpServer executable = serverAccess.findExecutable(mcpServerCode, userId);
            if (executable != null
                    && capabilityGuard.isInstallTypeAllowed(executable.getInstallType())) {
                detail.setTools(mcpManager.getTools(executable));
            } else {
                detail.setTools(List.of());
            }
        }
        return detail;
    }

    @Override
    public IPage<McpServerDetail> listMcpServers(Long userId, String name, int current, int size) {
        Page<McpServer> page = new Page<>(current, size);
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<McpServer>()
            .eq(McpServer::getUserId, userId)
            .ne(McpServer::getStatus, McpServerAccess.STATUS_DELETED) // 排除已删除
            .orderByDesc(McpServer::getGmtCreate);

        if (StringUtils.hasText(name)) {
            wrapper.like(McpServer::getName, name);
        }

        IPage<McpServer> entityPage = mcpServerMapper.selectPage(page, wrapper);

        Page<McpServerDetail> detailPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        detailPage.setRecords(entityPage.getRecords().stream().map(this::convertToDetail).toList());
        return detailPage;
    }

    @Override
    public List<McpTool> getTools(String mcpServerCode, Long userId) {
        // 工具发现会建立连接：只对当前用户已启用的服务执行
        McpServer entity = serverAccess.requireExecutable(mcpServerCode, userId);
        return mcpManager.getTools(entity);
    }

    @Override
    public McpServerCallToolResponse callTool(McpServerCallToolRequest request, Long userId) {
        // 执行前回读归属与启用状态，模型给出的 code 不构成授权
        McpServer entity = serverAccess.requireExecutable(request.getMcpServerCode(), userId);
        return mcpManager.callTool(request, entity);
    }

    private McpServer getByCodeAndUser(String mcpServerCode, Long userId) {
        McpServer entity = serverAccess.findOwned(mcpServerCode, userId);
        if (entity == null) {
            // 不区分「不存在」与「属于他人」，避免用错误信息探测他人配置
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "MCP 服务不存在");
        }
        return entity;
    }

    /**
     * 保存前的能力校验：禁用能力与受限目标都不落库。
     */
    private void validateDeployConfig(McpServerDetail detail) {
        Map<String, Object> serverConfig = firstServerConfig(detail.getDeployConfig());
        // 声明 SSE/HTTP 却在配置里写 command 同样会启动进程：按 STDIO 处理。
        // 判定与 MCPManager 创建客户端时共用 McpCapabilityGuard#isStdio
        capabilityGuard.assertStdioAllowedIfDeclared(
                resolveInstallType(detail), asText(serverConfig.get("command")),
                "McpServerServiceImpl#validateDeployConfig");
        String url = asText(serverConfig.get("url"));
        if (StringUtils.hasText(url)) {
            // 用户提供的远程目标复用同一份出站策略
            outboundPolicy.requireAllowed(url);
        }
    }

    private int resolveStatus(Integer status) {
        if (status == null) {
            return McpServerAccess.STATUS_ENABLED;
        }
        // 只有「启用」「停用」是客户端可设置的状态，删除走专用入口
        if (status != McpServerAccess.STATUS_DISABLED && status != McpServerAccess.STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态取值不合法");
        }
        return status;
    }

    private String resolveInstallType(McpServerDetail detail) {
        if (StringUtils.hasText(detail.getInstallType())) {
            return detail.getInstallType().trim().toUpperCase(java.util.Locale.ROOT);
        }
        Map<String, Object> serverConfig = firstServerConfig(detail.getDeployConfig());
        String declared = serverConfig == null ? null : asText(serverConfig.get("type"));
        if (StringUtils.hasText(declared)) {
            return declared.trim().toUpperCase(java.util.Locale.ROOT);
        }
        return "SSE";
    }

    /**
     * 从 deployConfig 中解析并提取 host，同时完成验证（只解析一次）
     */
    private String extractHost(String deployConfig) {
        Map<String, Object> serverConfig = firstServerConfig(deployConfig);
        if (serverConfig == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid deployConfig: mcpServers is required");
        }
        if ("stdio".equalsIgnoreCase(asText(serverConfig.get("type")))) {
            return "";
        }
        String url = asText(serverConfig.get("url"));
        if (!StringUtils.hasText(url)) {
            return "";
        }
        URI uri = outboundPolicy.requireAllowed(url);
        String host = uri.getScheme() + "://" + uri.getHost();
        if (uri.getPort() != -1) {
            host += ":" + uri.getPort();
        }
        return host;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstServerConfig(String deployConfig) {
        Map<String, Object> config = JsonUtils.fromJsonToMap(deployConfig);
        if (config == null || !config.containsKey("mcpServers")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid deployConfig: mcpServers is required");
        }
        Object mcpServers = config.get("mcpServers");
        if (!(mcpServers instanceof Map<?, ?> servers) || servers.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid deployConfig: mcpServers is required");
        }
        Object first = servers.values().iterator().next();
        if (!(first instanceof Map<?, ?>)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid deployConfig: server config is invalid");
        }
        return (Map<String, Object>) first;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private McpServerDetail convertToDetail(McpServer entity) {
        McpServerDetail detail = new McpServerDetail();
        detail.setMcpServerCode(entity.getMcpServerCode());
        detail.setName(entity.getName());
        detail.setDescription(entity.getDescription());
        detail.setDeployConfig(entity.getDeployConfig());
        detail.setStatus(entity.getStatus());
        detail.setInstallType(entity.getInstallType());
        detail.setHost(entity.getHost());
        return detail;
    }
}
