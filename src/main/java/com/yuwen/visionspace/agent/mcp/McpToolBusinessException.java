package com.yuwen.visionspace.agent.mcp;

/**
 * MCP 服务明确报告的业务失败：请求已送达并得到应答，外部声明执行没有成功。
 *
 * <p>与「结果未知」分开：明确失败意味着外部没有产生预期副作用，以普通运行时
 * 异常抛出后由框架折成 error 响应，动作台账记为可安全重试的失败。
 */
public class McpToolBusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public McpToolBusinessException(String message) {
        super(message);
    }
}
