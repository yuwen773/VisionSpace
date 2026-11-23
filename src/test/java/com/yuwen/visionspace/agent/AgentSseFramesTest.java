package com.yuwen.visionspace.agent;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 控制帧走默认事件：前端只有一条按 messageType 分派的通用通道。
 */
class AgentSseFramesTest {

    @Test
    void doneFrameUsesDefaultEvent() {
        ServerSentEvent<String> frame = AgentSseFrames.done();

        assertNull(frame.event(), "结束帧不应依赖带名字的事件");
        assertEquals("{\"messageType\":\"done\"}", frame.data());
    }

    @Test
    void errorFrameUsesDefaultEventAndCarriesMessage() {
        ServerSentEvent<String> frame = AgentSseFrames.error();

        assertNull(frame.event(), "错误帧不应依赖带名字的事件");
        assertEquals("{\"messageType\":\"error\",\"content\":\""
                + AgentSseFrames.ERROR_CONTENT + "\"}", frame.data());
    }
}
