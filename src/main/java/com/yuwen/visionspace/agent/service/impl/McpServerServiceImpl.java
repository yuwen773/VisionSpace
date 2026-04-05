package com.yuwen.visionspace.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.agent.service.McpServerService;
import com.yuwen.visionspace.mapper.McpServerMapper;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServer;
import com.yuwen.visionspace.utils.IdGenerator;
import com.yuwen.visionspace.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.util.Date;
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

        // 2. 解析并验证配置（extractHost 内部完成验证）
        String host = extractHost(detail.getDeployConfig());

        // 3. 构建实体
        McpServer entity = new McpServer();
        entity.setMcpServerCode(mcpServerCode);
        entity.setUserId(userId);
        entity.setName(detail.getName());
        entity.setDescription(detail.getDescription());
        entity.setDeployConfig(detail.getDeployConfig());
        entity.setHost(host);
        entity.setStatus(1);
        entity.setInstallType(StringUtils.hasText(detail.getInstallType()) ? detail.getInstallType() : "SSE");
        entity.setGmtCreate(new Date());
        entity.setGmtModified(new Date());

        mcpServerMapper.insert(entity);
        return mcpServerCode;
    }

    @Override
    public void updateMcp(McpServerDetail detail, Long userId) {
        McpServer entity = getByCodeAndUser(detail.getMcpServerCode(), userId);
        String host = extractHost(detail.getDeployConfig());

        entity.setName(detail.getName());
        entity.setDescription(detail.getDescription());
        entity.setDeployConfig(detail.getDeployConfig());
        entity.setHost(host);
        entity.setInstallType(StringUtils.hasText(detail.getInstallType()) ? detail.getInstallType() : "SSE");
        entity.setGmtModified(new Date());

        mcpServerMapper.updateById(entity);
    }

    @Override
    public void deleteMcp(String mcpServerCode, Long userId) {
        McpServer entity = getByCodeAndUser(mcpServerCode, userId);
        entity.setStatus(3); // 软删除
        mcpServerMapper.updateById(entity);
    }

    @Override
    public McpServerDetail getMcp(String mcpServerCode, Long userId, boolean needTools) {
        McpServer entity = getByCodeAndUser(mcpServerCode, userId);
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

    @Override
    public IPage<McpServerDetail> listMcpServers(Long userId, String name, int current, int size) {
        Page<McpServer> page = new Page<>(current, size);
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<McpServer>()
            .eq(McpServer::getUserId, userId)
            .ne(McpServer::getStatus, 3) // 排除已删除
            .orderByDesc(McpServer::getGmtCreate);

        if (StringUtils.hasText(name)) {
            wrapper.like(McpServer::getName, name);
        }

        IPage<McpServer> entityPage = mcpServerMapper.selectPage(page, wrapper);

        // 转换为 Detail DTO
        Page<McpServerDetail> detailPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        detailPage.setRecords(entityPage.getRecords().stream().map(this::convertToDetail).toList());
        return detailPage;
    }

    @Override
    public List<McpTool> getTools(String mcpServerCode, Long userId) {
        McpServer entity = getByCodeAndUser(mcpServerCode, userId);
        return mcpManager.getTools(entity);
    }

    @Override
    public McpServerCallToolResponse callTool(McpServerCallToolRequest request, Long userId) {
        McpServer entity = mcpServerMapper.selectOne(
            new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getMcpServerCode, request.getMcpServerCode())
                .eq(McpServer::getUserId, userId)
                .ne(McpServer::getStatus, 3)
        );
        if (entity == null) {
            throw new IllegalArgumentException("MCP server not found");
        }
        return mcpManager.callTool(request, entity);
    }

    private McpServer getByCodeAndUser(String mcpServerCode, Long userId) {
        return mcpServerMapper.selectOne(
            new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getMcpServerCode, mcpServerCode)
                .eq(McpServer::getUserId, userId)
                .ne(McpServer::getStatus, 3)
        );
    }

    /**
     * 从 deployConfig 中解析并提取 host，同时完成验证（只解析一次）
     */
    private String extractHost(String deployConfig) {
        try {
            Map<String, Object> config = JsonUtils.fromJsonToMap(deployConfig);
            if (config == null || !config.containsKey("mcpServers")) {
                throw new IllegalArgumentException("Invalid deployConfig: mcpServers is required");
            }
            Map<String, Object> mcpServers = (Map<String, Object>) config.get("mcpServers");
            if (mcpServers != null && !mcpServers.isEmpty()) {
                Map<String, Object> firstServer = (Map<String, Object>) mcpServers.values().iterator().next();
                String type = (String) firstServer.get("type");
                if ("stdio".equalsIgnoreCase(type)) {
                    return "";
                }
                String url = (String) firstServer.get("url");
                if (url != null) {
                    URL urlObj = new URL(url);
                    String host = urlObj.getProtocol() + "://" + urlObj.getHost();
                    if (urlObj.getPort() != -1) {
                        host += ":" + urlObj.getPort();
                    }
                    return host;
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // 解析失败，返回空
        }
        return "";
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