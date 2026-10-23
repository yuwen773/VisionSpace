package com.yuwen.visionspace.model.dto.mcp;

import java.io.Serializable;

/**
 * 当前部署允许的 MCP 能力。
 *
 * <p>与服务端的拒绝规则同源：{@code stdioDisabled} 为真时，客户端不应提供
 * STDIO 选项，服务端也会在配置、发现与执行三处拒绝。
 */
public record McpCapabilities(boolean stdioDisabled) implements Serializable {
}
