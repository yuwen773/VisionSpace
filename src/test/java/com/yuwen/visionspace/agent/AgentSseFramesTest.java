package com.yuwen.visionspace.agent;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 控制帧必须走默认事件：命名事件的帧前端 onmessage 收不到。
 */
class AgentSseFramesTest {

    @Test
    void doneFrameUsesDefaultEvent() {
        ServerSentEvent<String> frame = AgentSseFrames.done();

        assertNull(frame.event(), "结束帧不能带 event 名，否则前端收不到");
        assertEquals("{\"messageType\":\"done\"}", frame.data());
    }

    @Test
    void errorFrameUsesDefaultEventAndCarriesMessage() {
        ServerSentEvent<String> frame = AgentSseFrames.error();

        assertNull(frame.event(), "错误帧不能带 event 名，否则前端收不到");
        assertEquals("{\"messageType\":\"error\",\"content\":\""
                + AgentSseFrames.ERROR_CONTENT + "\"}", frame.data());
    }
}
