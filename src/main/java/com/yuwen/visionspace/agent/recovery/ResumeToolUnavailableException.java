package com.yuwen.visionspace.agent.recovery;

/**
 * 续跑所需的工具环境已经不可用（MCP 服务停用、撤销或删除）。
 *
 * <p>单独一个类型是为了让入口层能把「权限已撤销」与「执行失败」分开：
 * 前者要用户重新发起请求，后者可以稍后重试。
 */
public class ResumeToolUnavailableException extends RuntimeException {

    public ResumeToolUnavailableException(String message) {
        super(message);
    }
}
