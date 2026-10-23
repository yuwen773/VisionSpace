package com.yuwen.visionspace.agent.recovery;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.yuwen.visionspace.agent.config.AgentToolExceptionPolicy;
import com.yuwen.visionspace.agent.config.DualCheckpointSaver;
import com.yuwen.visionspace.agent.mcp.McpToolCallback;
import com.yuwen.visionspace.agent.manager.MCPManager;
import com.yuwen.visionspace.agent.mcp.McpServerAccess;
import com.yuwen.visionspace.model.dto.mcp.InputSchema;
import com.yuwen.visionspace.model.dto.mcp.McpServerCallToolRequest;
import com.yuwen.visionspace.model.dto.mcp.McpServerCallToolResponse;
import com.yuwen.visionspace.model.dto.mcp.McpTool;
import com.yuwen.visionspace.model.entity.AgentActionRecord;
import com.yuwen.visionspace.model.entity.McpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 真实 MCP 适配链：模型 → ReactAgent → McpToolCallback → MCPManager → 动作台账。
 *
 * <p>评审 F2 的缺陷只在链路上存在：MCPManager 把客户端异常转成 isError 响应后，
 * 旧实现只提取文本，错误语义在适配层丢失，框架向拦截器返回普通成功响应，
 * 台账把「外部已写入但响应超时」记成 SUCCEEDED，模型继续推进。
 *
 * <p>只有模型与远端 MCP 客户端是受控替身：MCPManager 的替身按真实实现的
 * 异常转换行为返回响应（外部副作用已发生 + 结果未知），其余全部走真实代码。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpToolChainFailureTest {

    private static final Long OWNER = 77L;
    private static final String THREAD = "thread-mcp-chain";
    private static final String CALL_ID = "call-mcp-1";
    private static final String MCP_TOOL = "externalArchiveWrite";
    private static final String ACTION_ID = AgentActionLedger.actionId(THREAD, CALL_ID);

    @Mock
    private MCPManager mcpManager;

    @Mock
    private McpServerAccess serverAccess;

    private InMemoryActionRecordMapper store;
    private AgentActionInterceptor interceptor;
    private final AtomicInteger externalCalls = new AtomicInteger();
    private final AtomicInteger modelCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        store = new InMemoryActionRecordMapper();
        interceptor = new AgentActionInterceptor(new AgentActionLedger(store.proxy()));
        externalCalls.set(0);
        modelCalls.set(0);

        McpServer server = new McpServer();
        server.setMcpServerCode("remote-a");
        when(serverAccess.requireExecutable(any(), any())).thenReturn(server);
    }

    @Test
    void timeoutAfterExternalSideEffectPausesTheRunAsUnknownNotAsSuccess() {
        // 远端已执行写入，然后响应超时：MCPManager 按真实异常路径转换成
        // 「结果未知」响应（isError + outcomeUnknown）
        when(mcpManager.callTool(any(McpServerCallToolRequest.class), any()))
                .thenAnswer(invocation -> {
                    externalCalls.incrementAndGet();
                    McpServerCallToolResponse response = new McpServerCallToolResponse();
                    response.setIsError(true);
                    response.setOutcomeUnknown(true);
                    response.setContent(List.of(text("MCP 工具调用失败，请稍后重试或检查服务配置")));
                    return response;
                });

        ScriptedChatModel model = new ScriptedChatModel();
        ReactAgent agent = agent(model);

        assertThrows(PendingReviewException.class, () -> agent.call("归档这张图片", config()));

        assertEquals(1, externalCalls.get(),
                "the external write must happen exactly once before the timeout");
        assertEquals(1, modelCalls.get(),
                "an unknown result must stop the run: the model must not get another turn");
        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, status(),
                "a timed-out non-idempotent MCP call must wait for review, not be recorded as done");
    }

    @Test
    void explicitBusinessFailureIsRetryableAndTheModelMayContinue() throws Exception {
        // 服务明确应答失败：外部声明执行没有成功，与「结果未知」分开
        when(mcpManager.callTool(any(McpServerCallToolRequest.class), any()))
                .thenAnswer(invocation -> {
                    externalCalls.incrementAndGet();
                    McpServerCallToolResponse response = new McpServerCallToolResponse();
                    response.setIsError(true);
                    response.setContent(List.of(text("参数不合法")));
                    return response;
                });

        ReactAgent agent = agent(new ScriptedChatModel());
        AssistantMessage answer = agent.call("归档这张图片", config());

        assertNotNull(answer, "a business failure must not abort the run");
        assertEquals(2, modelCalls.get(),
                "the model sees the error and may answer without the tool");
        assertEquals(1, externalCalls.get());
        assertEquals(AgentActionRecord.STATUS_FAILED, status(),
                "an explicit server-side failure is safely retryable, not an unknown result");
    }

    @Test
    void unknownActionIsResolvedOnlyThroughTheManualReviewEntry() {
        when(mcpManager.callTool(any(McpServerCallToolRequest.class), any()))
                .thenAnswer(invocation -> {
                    externalCalls.incrementAndGet();
                    McpServerCallToolResponse response = new McpServerCallToolResponse();
                    response.setIsError(true);
                    response.setOutcomeUnknown(true);
                    response.setContent(List.of(text("MCP 工具调用失败")));
                    return response;
                });

        assertThrows(PendingReviewException.class,
                () -> agent(new ScriptedChatModel()).call("归档这张图片", config()));
        assertEquals(AgentActionRecord.STATUS_UNKNOWN_NEEDS_REVIEW, status());

        AgentActionLedger ledger = new AgentActionLedger(store.proxy());

        // 人工核对「已执行」：动作收敛为成功，后续认领不再触达外部
        assertTrue(ledger.confirmCompleted(THREAD, OWNER, ACTION_ID, "远端已确认写入"));
        assertEquals(AgentActionClaim.Outcome.ALREADY_SUCCEEDED,
                ledger.claim(THREAD, OWNER, ACTION_ID, MCP_TOOL, "{}",
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN).outcome());
        assertEquals(1, externalCalls.get(), "a confirmed action must not reach the provider again");

        // 人工核对「未执行」：终态 DISMISSED，也不会静默重放
        when(mcpManager.callTool(any(McpServerCallToolRequest.class), any()))
                .thenAnswer(invocation -> {
                    externalCalls.incrementAndGet();
                    McpServerCallToolResponse response = new McpServerCallToolResponse();
                    response.setIsError(true);
                    response.setOutcomeUnknown(true);
                    response.setContent(List.of(text("MCP 工具调用失败")));
                    return response;
                });
        String secondCall = "call-mcp-2";
        assertThrows(PendingReviewException.class,
                () -> agent(new ScriptedChatModel(secondCall)).call("再归档一次", config()));
        String secondActionId = AgentActionLedger.actionId(THREAD, secondCall);
        assertTrue(ledger.dismissUnknown(THREAD, OWNER, secondActionId, "确认未执行"));
        assertEquals(AgentActionClaim.Outcome.DISMISSED,
                ledger.claim(THREAD, OWNER, secondActionId, MCP_TOOL, "{}",
                        AgentActionRecord.IDEMPOTENCY_NOT_PROVEN).outcome());
    }

    @Test
    void outcomeUnknownOnAProvenIdempotentToolOnlyInformsTheModel() throws Exception {
        // 只读工具（集合内的可证明幂等工具）结果未知：没有副作用可重放，
        // 转回文本让模型继续，而不是中断整轮执行
        when(mcpManager.callTool(any(McpServerCallToolRequest.class), any()))
                .thenAnswer(invocation -> {
                    externalCalls.incrementAndGet();
                    McpServerCallToolResponse response = new McpServerCallToolResponse();
                    response.setIsError(true);
                    response.setOutcomeUnknown(true);
                    response.setContent(List.of(text("MCP 工具调用失败")));
                    return response;
                });

        AssistantMessage answer = agent(new ScriptedChatModel(CALL_ID, "searchSimilarImages"),
                CALL_ID, "searchSimilarImages").call("找相似图", config());

        assertNotNull(answer, "a read-only unknown result must not abort the run");
        assertEquals(2, modelCalls.get());
        assertEquals(1, externalCalls.get());
    }

    private String status() {
        return store.status(THREAD, OWNER, ACTION_ID);
    }

    private ReactAgent agent(ChatModel model) {
        return agent(model, CALL_ID, MCP_TOOL);
    }

    private ReactAgent agent(ChatModel model, String callId, String toolName) {
        McpTool tool = new McpTool();
        tool.setName(toolName);
        tool.setDescription("外部归档写入");
        InputSchema schema = new InputSchema();
        schema.setType("object");
        schema.setProperties(new HashMap<>());
        tool.setInputSchema(schema);
        McpToolCallback callback = new McpToolCallback(mcpManager, serverAccess, "remote-a", OWNER, tool);

        return ReactAgent.builder()
                .name("test_mcp_agent")
                .model(model)
                .instruction("test")
                .tools(List.of(callback))
                .interceptors(List.of(interceptor))
                .toolExecutionExceptionProcessor(AgentToolExceptionPolicy.processor())
                .build();
    }

    private RunnableConfig config() {
        return RunnableConfig.builder()
                .threadId(THREAD)
                .addMetadata(DualCheckpointSaver.OWNER_USER_ID_METADATA_KEY, OWNER)
                .build();
    }

    private McpServerCallToolResponse.TextContent text(String value) {
        McpServerCallToolResponse.TextContent content = new McpServerCallToolResponse.TextContent();
        content.setType("text");
        content.setText(value);
        return content;
    }

    /**
     * 第一次返回一个 MCP 工具调用，之后一律返回文本。
     *
     * <p>计数暴露「结果未知是否真的停住了模型」：缺陷形态下模型会拿到一条
     * 普通响应并再跑一轮（model_calls=2），修复后必须停在 1。
     */
    private final class ScriptedChatModel implements ChatModel {

        private final String callId;
        private final String toolName;

        private ScriptedChatModel() {
            this(CALL_ID, MCP_TOOL);
        }

        private ScriptedChatModel(String callId) {
            this(callId, MCP_TOOL);
        }

        private ScriptedChatModel(String callId, String toolName) {
            this.callId = callId;
            this.toolName = toolName;
        }

        private boolean issuedToolCall;

        public ChatResponse call(Prompt prompt) {
            modelCalls.incrementAndGet();
            if (!issuedToolCall) {
                issuedToolCall = true;
                AssistantMessage message = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                callId, "function", toolName, "{\"path\":\"archive/1\"}")))
                        .build();
                return new ChatResponse(List.of(new Generation(message)));
            }
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("已经处理完了"))));
        }
    }
}
