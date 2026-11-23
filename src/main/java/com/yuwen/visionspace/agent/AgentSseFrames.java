package com.yuwen.visionspace.agent;

import org.springframework.http.codec.ServerSentEvent;

/**
 * 对话流的固定控制帧。
 *
 * <p>结束与失败都用<b>默认事件</b>（不设置 {@code event:} 名）。浏览器的
 * {@code EventSource} 与 {@code @microsoft/fetch-event-source} 的 {@code onmessage}
 * 只接收无名事件，命名事件必须额外注册监听；早期实现用 {@code event: error/done}
 * 发出，前端收不到，失败就变成了无声断流。
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
