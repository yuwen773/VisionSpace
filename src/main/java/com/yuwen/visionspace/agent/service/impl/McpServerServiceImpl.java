package com.yuwen.visionspace.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.agent.service.McpServerService;
import com.yuwen.visionspace.mapper.McpServerMapper;
import com.yuwen.visionspace.model.dto.mcp.*;
import com.yuwen.visionspace.model.entity.McpServerEntity;
import com.yuwen.visionspace.utils.IdGenerator;
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
    public void updateMcp(McpServerDetail detail, Long userId) {
        McpServerEntity entity = getByCodeAndUser(detail.getMcpServerCode(), userId);
        String processedConfig = processDeployConfig(detail.getDeployConfig());
        String host = extractHost(detail.getDeployConfig());

        entity.setName(detail.getName());
        entity.setDescription(detail.getDescription());
        entity.setDeployConfig(processedConfig);
        entity.setHost(host);
        entity.setInstallType(StringUtils.hasText(detail.getInstallType()) ? detail.getInstallType() : "SSE");

        mcpServerMapper.updateById(entity);
    }

    @Override
    public void deleteMcp(String mcpServerCode, Long userId) {
        McpServerEntity entity = getByCodeAndUser(mcpServerCode, userId);
        entity.setStatus(3); // 软删除
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

    @Override
    public IPage<McpServerDetail> listMcpServers(Long userId, String name, int current, int size) {
        Page<McpServerEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<McpServerEntity> wrapper = new LambdaQueryWrapper<McpServerEntity>()
            .eq(McpServerEntity::getUserId, userId)
            .ne(McpServerEntity::getStatus, 3) // 排除已删除
            .orderByDesc(McpServerEntity::getGmtCreate);

        if (StringUtils.hasText(name)) {
            wrapper.like(McpServerEntity::getName, name);
        }

        IPage<McpServerEntity> entityPage = mcpServerMapper.selectPage(page, wrapper);

        // 转换为 Detail DTO
        Page<McpServerDetail> detailPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        detailPage.setRecords(entityPage.getRecords().stream().map(this::convertToDetail).toList());
        return detailPage;
    }

    @Override
    public List<McpTool> getTools(String mcpServerCode, Long userId) {
        McpServerEntity entity = getByCodeAndUser(mcpServerCode, userId);
        return mcpManager.getTools(entity);
    }

    @Override
    public McpServerCallToolResponse callTool(McpServerCallToolRequest request, Long userId) {
        McpServerEntity entity = mcpServerMapper.selectOne(
            new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getMcpServerCode, request.getMcpServerCode())
                .eq(McpServerEntity::getUserId, userId)
                .ne(McpServerEntity::getStatus, 3)
        );
        if (entity == null) {
            throw new IllegalArgumentException("MCP server not found");
        }
        return mcpManager.callTool(request, entity);
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

    private McpServerDetail convertToDetail(McpServerEntity entity) {
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