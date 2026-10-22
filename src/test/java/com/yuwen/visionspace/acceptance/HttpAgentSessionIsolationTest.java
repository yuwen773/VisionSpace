package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.service.AgentSessionGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 全入口会话隔离（原 06.x），全部经真实 HTTP。
 *
 * <p>覆盖：首次创建原子绑定归属；同步聊天、SSE、反馈、历史与会话列表都拒绝其他
 * 用户与平台管理员；拒绝路径零消息插入、零 Checkpoint 变更、零模型或工具执行；
 * 并发首次创建只认一个所有者；请求体里的 userId 不能替代服务端身份。
 *
 * <p>重试关闭：本类的「零执行」断言依赖外部调用记录器，而 Spring AI 默认对
 * 503 做长退避重试，会让本该失败的请求挂上几分钟。拒绝路径本来就不该发起调用，
 * 关闭重试反而让断言更严格。
 */
@IsolatedSpringBootTest
@TestPropertySource(properties = {
        "spring.ai.retry.max-attempts=1",
        "spring.ai.retry.backoff.initial-interval=10ms",
        "spring.ai.retry.backoff.max-interval=20ms",
})
class HttpAgentSessionIsolationTest {

    private static final String PASSWORD = "acceptance-password";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExternalCallRecorder recorder;

    @Autowired
    private AgentSessionGuard sessionGuard;

    private long ownerId;
    private long strangerId;

    private final List<String> threadIds = new ArrayList<>();

    @BeforeEach
    void fixture() throws Exception {
        ownerId = register("agent_owner_" + System.nanoTime());
        strangerId = register("agent_stranger_" + System.nanoTime());
    }

    @AfterEach
    void tearDown() {
        for (String threadId : threadIds) {
            jdbc.update("DELETE FROM agent_message WHERE sessionId = ?", threadId);
            jdbc.update("DELETE FROM agent_session WHERE sessionId = ?", threadId);
        }
    }

    // ---- 入口隔离 ----

    @Test
    void historyAndSessionsRejectStrangersAndAdminsWithoutMembership() throws Exception {
        String threadId = seedSession(ownerId, "owner thread");
        long adminId = registerAdmin("agent_admin_" + System.nanoTime());

        try (var stranger = client(); var admin = client()) {
            login(stranger, strangerId);
            assertRejected(get(stranger, "/agent/history/" + threadId));
            // 平台管理员没有旁路：会话归属只看服务端身份
            login(admin, adminId);
            assertRejected(get(admin, "/agent/history/" + threadId));

            // 会话列表只返回本人的会话，不因知道 threadId 而泄露标题
            var strangerSessions = get(stranger, "/agent/sessions");
            assertEquals(0, strangerSessions.path("code").asInt(), strangerSessions.toString());
            assertFalse(containsSession(strangerSessions, threadId),
                    "stranger listing must not contain another user's session: " + strangerSessions);
        }
    }

    @Test
    void ownerStillReadsOwnHistory() throws Exception {
        String threadId = seedSession(ownerId, "owner thread");
        jdbc.update("INSERT INTO agent_message (sessionId, role, subType, content, createdTime) " +
                "VALUES (?, 'USER', NULL, 'hello', NOW())", threadId);

        try (var owner = client()) {
            login(owner, ownerId);
            var response = get(owner, "/agent/history/" + threadId);
            assertEquals(0, response.path("code").asInt(), response.toString());
            assertEquals("hello", response.path("data").path("messages").get(0).path("content").asText());
        }
    }

    @Test
    void feedbackRejectsStrangersWithoutSideEffects() throws Exception {
        String threadId = seedSession(ownerId, "owner thread");

        try (var stranger = client()) {
            login(stranger, strangerId);
            int beforeCalls = recorder.calls().size();
            var response = post(stranger, "/agent/image/feedback", Map.of(
                    "threadId", threadId, "satisfied", false,
                    "reason", "forged", "action", "REGENERATE"));
            assertRejected(response);
            assertNoExecution(beforeCalls);
        }
    }

    @Test
    void syncChatRejectsStrangersWithoutSideEffects() throws Exception {
        String threadId = seedSession(ownerId, "owner thread");

        try (var stranger = client()) {
            login(stranger, strangerId);
            int beforeCalls = recorder.calls().size();
            var response = post(stranger, "/agent/image/chat", Map.of(
                    "message", "越权执行", "threadId", threadId));
            assertRejected(response);
            assertNoExecution(beforeCalls);
        }
    }

    @Test
    void streamRejectsStrangersWithoutSideEffects() throws Exception {
        String threadId = seedSession(ownerId, "owner thread");

        try (var stranger = client()) {
            login(stranger, strangerId);
            int beforeCalls = recorder.calls().size();
            var response = send(stranger, HttpRequest.newBuilder(uri("/agent/image/chat/stream"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("message", "越权执行", "threadId", threadId)))).build());
            // 归属在返回 Flux 之前判定，客户端拿到的是 JSON 错误而不是事件流；
            // 只声明 SSE 的客户端也读得到它（produces 同时声明了 application/json）
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                    "rejection must be a readable JSON error: "
                            + response.headers().firstValue("Content-Type"));
            assertRejected(json.readTree(response.body()));
            assertNoExecution(beforeCalls);
        }
    }

    @Test
    void anonymousStreamIsRejected() throws Exception {
        try (var anonymous = client()) {
            int beforeCalls = recorder.calls().size();
            var response = send(anonymous, HttpRequest.newBuilder(uri("/agent/image/chat/stream"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"hi\"}")).build());
            assertRejected(json.readTree(response.body()));
            assertNoExecution(beforeCalls);
        }
    }

    // ---- 身份与首次创建 ----

    @Test
    void clientSuppliedUserIdDoesNotReplaceServerIdentity() throws Exception {
        // 1) 已有他人会话：伪造 userId 指向所有者仍然必须被拒绝，且零执行
        String ownedThread = seedSession(ownerId, "owner thread");
        try (var stranger = client()) {
            login(stranger, strangerId);
            int beforeCalls = recorder.calls().size();
            var response = post(stranger, "/agent/image/chat", Map.of(
                    "message", "冒充", "threadId", ownedThread,
                    "userId", String.valueOf(ownerId)));
            assertRejected(response);
            assertNoExecution(beforeCalls);
            assertEquals(ownerId, jdbc.queryForObject(
                    "SELECT userId FROM agent_session WHERE sessionId = ?", Long.class, ownedThread),
                    "forged userId must not change session ownership");
        }

        // 2) 新会话：绑定的是登录用户，不是请求体里声明的 userId
        String freshThread = newThreadId();
        try (var stranger = client()) {
            login(stranger, strangerId);
            post(stranger, "/agent/image/chat", Map.of(
                    "message", "新会话", "threadId", freshThread,
                    "userId", String.valueOf(ownerId)));
            assertEquals(1, sessionCount(freshThread), "first use must create the session");
            assertEquals(strangerId, jdbc.queryForObject(
                    "SELECT userId FROM agent_session WHERE sessionId = ?", Long.class, freshThread),
                    "session must belong to the logged-in user, not the forged body userId");
        }
    }

    /**
     * 并发首次创建：两个用户同时用同一个 threadId 绑定，数据库里只能有一个所有者。
     *
     * <p>这里直接打服务层的归属绑定，而不是走 HTTP 聊天：聊天一旦通过归属判定就会
     * 去调模型，而验收环境没有真实模型，验证「谁赢」会变成验证「模型怎么失败」。
     * 归属绑定本身是这条不变量的全部；HTTP 入口的拒绝路径由上面几条用例覆盖。
     */
    @Test
    void concurrentFirstCreationBindsExactlyOneOwner() throws Exception {
        String threadId = newThreadId();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> results = new ArrayList<>();
            results.add(pool.submit(bindAfter(start, threadId, ownerId)));
            results.add(pool.submit(bindAfter(start, threadId, strangerId)));
            start.countDown();

            Long first = results.get(0).get(30, TimeUnit.SECONDS);
            Long second = results.get(1).get(30, TimeUnit.SECONDS);

            // 绑定成功返回所有者，失败返回 null（被拒绝）
            assertTrue((first == null) != (second == null),
                    "exactly one concurrent binding may succeed: " + first + " / " + second);
            assertEquals(1, sessionCount(threadId), "exactly one session row");

            Long boundOwner = jdbc.queryForObject(
                    "SELECT userId FROM agent_session WHERE sessionId = ?", Long.class, threadId);
            assertNotNull(boundOwner);
            assertEquals(first != null ? first : second, boundOwner,
                    "session must belong to the binding that succeeded");
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- assertions ----

    /**
     * 拒绝路径的副作用必须为零：没有消息、没有 Checkpoint、没有模型或工具执行。
     */
    private void assertNoExecution(int callsBefore) {
        assertEquals(callsBefore, recorder.calls().size(),
                "rejected request must not call models, tools or storage: "
                        + recorder.calls().subList(callsBefore, recorder.calls().size()));
    }

    private int sessionCount(String threadId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM agent_session WHERE sessionId = ?",
                Integer.class, threadId);
    }

    private boolean containsSession(JsonNode response, String threadId) {
        for (JsonNode session : response.path("data")) {
            if (threadId.equals(session.path("sessionId").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean isRejected(JsonNode response) {
        int code = response.path("code").asInt();
        return code == 40100 || code == 40101;
    }

    private void assertRejected(JsonNode response) {
        assertTrue(isRejected(response), "expected authorization rejection: " + response);
    }

    private Callable<Long> bindAfter(CountDownLatch start, String threadId, long userId) {
        return () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                return sessionGuard.bindOwner(threadId, userId, "concurrent first message").getUserId();
            } catch (com.yuwen.visionspace.exception.BusinessException e) {
                // 被判定为他人会话：并发场景下的预期结果
                return null;
            }
        };
    }

    // ---- fixtures ----

    private String newThreadId() {
        String threadId = "it-agent-" + UUID.randomUUID();
        threadIds.add(threadId);
        return threadId;
    }

    private String seedSession(long userId, String title) {
        String threadId = newThreadId();
        jdbc.update("""
                INSERT INTO agent_session (sessionId, userId, title, status, lastMessage, messageCount,
                                           createdTime, updatedTime, isDelete)
                VALUES (?, ?, ?, 1, '', 0, NOW(), NOW(), 0)
                """, threadId, userId, title);
        return threadId;
    }

    private long register(String account) throws Exception {
        try (var client = client()) {
            var response = post(client, "/user/register", Map.of(
                    "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            assertEquals(0, response.path("code").asInt(), response.toString());
            return response.path("data").asLong();
        }
    }

    private long registerAdmin(String account) throws Exception {
        long id = register(account);
        jdbc.update("UPDATE user SET userRole = 'admin' WHERE id = ?", id);
        return id;
    }

    private void login(HttpClient client, long userId) throws Exception {
        String account = jdbc.queryForObject("SELECT userAccount FROM user WHERE id = ?", String.class, userId);
        var response = post(client, "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, response.path("code").asInt(), response.toString());
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path)).GET().build()).body());
    }

    private JsonNode post(HttpClient client, String path, Map<String, ?> body) throws Exception {
        return json.readTree(send(client, HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build()).body());
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }
}
