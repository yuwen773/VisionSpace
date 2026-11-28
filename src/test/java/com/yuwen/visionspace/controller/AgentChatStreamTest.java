package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.agent.AgentSseFrames;
import com.yuwen.visionspace.agent.ImageAgent;
import com.yuwen.visionspace.agent.service.AgentAttachmentService;
import com.yuwen.visionspace.agent.service.AgentSessionGuard;
import com.yuwen.visionspace.exception.GlobalExceptionHandler;
import com.yuwen.visionspace.model.dto.agent.MessageDTO;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentChatStreamTest {
    private static final String FAILURE_DETAIL = "private upstream response detail";
    private final ImageAgent agent = mock(ImageAgent.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new AgentController();
        var users = mock(UserService.class);
        var user = new User();
        user.setId(42L);
        when(users.getLoginUser(any())).thenReturn(user);
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "sessionGuard", mock(AgentSessionGuard.class));
        ReflectionTestUtils.setField(controller, "agentAttachmentService", mock(AgentAttachmentService.class));
        ReflectionTestUtils.setField(controller, "imageAgent", agent);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void synchronousFailureBeforePublisherExistsReturnsSafeErrorFrame() throws Exception {
        when(agent.stream("question", List.of(), "thread", 42L, null, null))
                .thenThrow(new IllegalStateException(FAILURE_DETAIL));
        assertEquals(List.of(AgentSseFrames.error().data()), responseFrames());
    }

    @Test
    void subscribedFailureBeforeFirstFrameReturnsSafeErrorFrame() throws Exception {
        when(agent.stream("question", List.of(), "thread", 42L, null, null))
                .thenReturn(Mono.delay(Duration.ofMillis(10))
                        .then(Mono.<MessageDTO>error(new IllegalStateException(FAILURE_DETAIL))).flux());
        assertEquals(List.of(AgentSseFrames.error().data()), responseFrames());
    }

    @Test
    void failureAfterContentFinishesWithErrorAndOmitsDone() throws Exception {
        when(agent.stream("question", List.of(), "thread", 42L, null, null))
                .thenReturn(Flux.concat(Flux.just(contentMessage()),
                        Flux.error(new IllegalStateException(FAILURE_DETAIL))));
        List<String> frames = responseFrames();
        assertEquals(2, frames.size());
        assertEquals(AgentSseFrames.error().data(), frames.get(1));
    }

    @Test
    void successStillFinishesWithDone() throws Exception {
        when(agent.stream("question", List.of(), "thread", 42L, null, null))
                .thenReturn(Flux.just(contentMessage()));
        List<String> frames = responseFrames();
        assertEquals(2, frames.size());
        assertEquals(AgentSseFrames.done().data(), frames.get(1));
    }

    private static MessageDTO contentMessage() {
        var message = new MessageDTO();
        message.setContent("answer");
        return message;
    }

    private List<String> responseFrames() throws Exception {
        var result = mvc.perform(post("/agent/image/chat/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"question\",\"threadId\":\"thread\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains(FAILURE_DETAIL));
        return body.lines().filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length())).toList();
    }
}
