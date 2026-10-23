package com.yuwen.visionspace.agent.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.mapper.McpServerMapper;
import com.yuwen.visionspace.model.entity.McpServer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 服务归属与启用状态的唯一判定入口。
 *
 * <p>工具回调可能存活很久（模型一次调用链、已缓存的工具定义），因此不能在创建时
 * 校验一次就认为授权一直成立：执行前必须用 {@code mcpServerCode + userId} 回读，
 * 并要求仍处于启用状态。停用、删除或换成他人 code 都会在这里得到空结果，
 * 调用方据此拒绝，不会产生任何外部连接或工具执行。
 *
 * <p>平台管理员没有旁路：这里只看归属。
 */
@Component
@RequiredArgsConstructor
public class McpServerAccess {

    /** 启用状态，与 mcp_server.status 的约定一致。 */
    public static final int STATUS_ENABLED = 1;

    /** 停用状态：保留配置但不参与工具发现与执行。 */
    public static final int STATUS_DISABLED = 0;

    /** 软删除状态。 */
    public static final int STATUS_DELETED = 3;

    private final McpServerMapper mcpServerMapper;

    /**
     * 回读当前用户可执行的 MCP 服务；不存在、属于他人或未启用时返回 null。
     */
    public McpServer findExecutable(String mcpServerCode, Long userId) {
        if (mcpServerCode == null || mcpServerCode.isBlank() || userId == null) {
            return null;
        }
        return mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getMcpServerCode, mcpServerCode)
                .eq(McpServer::getUserId, userId)
                .eq(McpServer::getStatus, STATUS_ENABLED));
    }

    /**
     * 回读当前用户可见（未删除）的 MCP 服务，用于管理预览；仍校验归属。
     */
    public McpServer findOwned(String mcpServerCode, Long userId) {
        if (mcpServerCode == null || mcpServerCode.isBlank() || userId == null) {
            return null;
        }
        return mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getMcpServerCode, mcpServerCode)
                .eq(McpServer::getUserId, userId)
                .ne(McpServer::getStatus, STATUS_DELETED));
    }

    /**
     * 批量回读当前用户已启用的服务。
     *
     * @param mcpServerCodes 需要回读的 code；为 null 表示该用户全部已启用服务
     */
    public List<McpServer> findEnabled(List<String> mcpServerCodes, Long userId) {
        if (userId == null) {
            return List.of();
        }
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getUserId, userId)
                .eq(McpServer::getStatus, STATUS_ENABLED);
        if (mcpServerCodes != null) {
            if (mcpServerCodes.isEmpty()) {
                return List.of();
            }
            wrapper.in(McpServer::getMcpServerCode, mcpServerCodes);
        }
        List<McpServer> servers = mcpServerMapper.selectList(wrapper);
        return servers == null ? List.of() : servers;
    }

    /**
     * 执行前复核：不可执行时统一拒绝，不区分"不存在""属于他人""已停用"。
     */
    public McpServer requireExecutable(String mcpServerCode, Long userId) {
        McpServer entity = findExecutable(mcpServerCode, userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "MCP 服务不可用");
        }
        return entity;
    }
}
