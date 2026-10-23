package com.yuwen.visionspace.agent.mcp;

/**
 * MCP 工具「结果未知」：请求可能已送达外部并产生了副作用，但本地没有拿到结果
 * （传输超时、连接中断、响应丢失）。
 *
 * <p>它以 {@code ToolExecutionException} 的 cause 穿过框架的异常处理器：
 * 非幂等工具必须由动作台账判成「结果未知」并暂停自动执行，绝不能被折成一条
 * 普通响应记成成功。
 */
public class McpToolOutcomeUnknownException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public McpToolOutcomeUnknownException(String message) {
        super(message);
    }
}
