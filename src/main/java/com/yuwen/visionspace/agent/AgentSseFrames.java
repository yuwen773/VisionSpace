package com.yuwen.visionspace.agent;

import org.springframework.http.codec.ServerSentEvent;

/**
 * 对话流的固定控制帧。
 *
 * <p>结束与失败都用<b>默认事件</b>（不设置 {@code event:} 名）：无名事件是所有
 * SSE 客户端都会交给通用 {@code onmessage} 的通道，前端只需按 {@code messageType}
 * 分派；带名字的事件要求客户端显式注册监听，多一条容易漏处理的路径。此前找图
 * 分支的结束帧还带着空 data，被前端的 {@code if (!event.data) return} 直接丢掉。
 */
public final class AgentSseFrames {

    public static final String ERROR_CONTENT = "系统繁忙，请稍后重试";

    static final String DONE_JSON = "{\"messageType\":\"done\"}";

    static final String ERROR_JSON =
            "{\"messageType\":\"error\",\"content\":\"" + ERROR_CONTENT + "\"}";

    private AgentSseFrames() {
    }

    /** 正常结束帧。 */
    public static ServerSentEvent<String> done() {
        return ServerSentEvent.<String>builder().data(DONE_JSON).build();
    }

    /** 运行期失败帧：说明这次没有成功，而不是让流无声结束。 */
    public static ServerSentEvent<String> error() {
        return ServerSentEvent.<String>builder().data(ERROR_JSON).build();
    }
}
