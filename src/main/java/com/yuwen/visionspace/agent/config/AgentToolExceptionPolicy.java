package com.yuwen.visionspace.agent.config;

import com.yuwen.visionspace.agent.tools.ImageGenerationException;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

import java.util.List;

/**
 * 工具异常策略：让「外部调用失败」以异常形式到达动作台账。
 *
 * <p>框架默认的处理器是 {@code alwaysThrow=false}：工具抛出的异常会被
 * {@code AgentToolNode.executeSyncTool} 转成一条**非 error** 的工具响应。
 * 拦截器看到的是「工具正常返回了内容」，于是把一次超时的付费生成记成成功——
 * 只测工具本身发现不了，必须走框架的真实调用链才暴露。
 *
 * <p>这里只把 {@link ImageGenerationException} 原样重抛：生成类动作无法证明
 * 外部没发生，必须由台账判成「结果未知」；其他工具的异常仍按框架默认行为
 * 转成错误文本，不改变既有语义。
 */
public final class AgentToolExceptionPolicy {

    private static final ToolExecutionExceptionProcessor PROCESSOR =
            DefaultToolExecutionExceptionProcessor.builder()
                    .alwaysThrow(false)
                    .rethrowExceptions(List.of(ImageGenerationException.class))
                    .build();

    private AgentToolExceptionPolicy() {
    }

    public static ToolExecutionExceptionProcessor processor() {
        return PROCESSOR;
    }
}
