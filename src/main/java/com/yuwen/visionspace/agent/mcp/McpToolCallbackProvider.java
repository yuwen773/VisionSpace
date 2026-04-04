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
