package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.agent.AgentSseFrames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IsolatedSpringBootTest
@Import(HttpAgentStreamTimingTest.ModelFixture.class)
class HttpAgentStreamTimingTest {
    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TimedChatModel provider;

    @Test
    @Timeout(30)
    void coldAuthorizedMcpDiscoveriesOverlapBeforeTheFirstRealContent() throws Exception {
        var discoveriesStarted = new CountDownLatch(2);
        var first = new Discovery(discoveriesStarted);
        var second = new Discovery(discoveriesStarted);
        try (var serverA = new ControlledMcpServer("unused", "echo_a", first);
             var serverB = new ControlledMcpServer("unused", "echo_b", second);
             var client = newClient()) {
            String account = uniqueAccount();
            long owner = registerAndLogIn(client, account);
            addMcpServer(owner, "timing-a", serverA.url());
            addMcpServer(owner, "timing-b", serverB.url());

            var request = request(client, "/agent/image/chat/stream",
                    Map.of("threadId", account, "message", "请直接输出结果，不要调用工具"))
                    .header("Accept", "text/event-stream").build();
            long started = System.nanoTime();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream"));
            long firstStatus = 0;
            long firstContent = 0;
            boolean done = false;
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    var event = json.readTree(line.substring(5));
                    String type = event.path("messageType").asText();
                    if (firstStatus == 0 && "reasoning".equals(type)) {
                        firstStatus = System.nanoTime();
                    }
                    if (firstContent == 0 && "assistant".equals(type) && !event.path("content").asText().isBlank()) {
                        assertEquals("受控回答", event.path("content").asText());
                        firstContent = System.nanoTime();
                    }
                    done |= "done".equals(type);
                }
            }
            assertTrue(done, "the chat must finish after delivering content");
            assertTrue(firstStatus > started && firstContent >= firstStatus);
            assertTrue(provider.started >= Math.max(first.finished, second.finished));
            assertTrue(first.started > started && second.started > started);
            System.out.printf("SSE fixture ms: MCP window=%d, provider first state=%d, provider first content=%d, "
                            + "HTTP first state=%d, HTTP first content=%d%n",
                    millis(Math.max(first.finished, second.finished) - Math.min(first.started, second.started)),
                    millis(provider.status - provider.started), millis(provider.content - provider.started),
                    millis(firstStatus - started), millis(firstContent - started));
            assertTrue(Math.max(first.started, second.started) < Math.min(first.finished, second.finished),
                    "cold tools/list requests must overlap");
            assertEquals(0, serverA.toolCalls() + serverB.toolCalls(), "discovery must not execute tools");
        }
    }

    @Test
    @Timeout(30)
    void providerFailureBeforeContentReturnsAReadableSseErrorOnTheRealServer() throws Exception {
        try (var client = newClient()) {
            String account = uniqueAccount();
            registerAndLogIn(client, account);
            var request = request(client, "/agent/image/chat/stream", Map.of(
                    "threadId", account, "message", TimedChatModel.FAIL_REQUEST))
                    .header("Accept", "text/event-stream").build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream"));
            assertEquals(List.of(AgentSseFrames.error().data()), response.body().lines()
                    .filter(line -> line.startsWith("data:")).map(line -> line.substring(5)).toList());
        }
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private static String uniqueAccount() {
        return "agent_sse_" + UUID.randomUUID().toString().replace("-", "");
    }

    private long registerAndLogIn(HttpClient client, String account) throws Exception {
        long owner = json.readTree(post(client, "/user/register", Map.of("userAccount", account,
                "userPassword", "acceptance-password", "checkPassword", "acceptance-password")).body())
                .path("data").asLong();
        assertTrue(owner > 0);
        assertEquals(0, json.readTree(post(client, "/user/login", Map.of("userAccount", account,
                "userPassword", "acceptance-password")).body()).path("code").asInt());
        return owner;
    }

    private void addMcpServer(long owner, String code, String url) {
        jdbc.update("""
                INSERT INTO mcp_server (mcp_server_code, user_id, name, deploy_config, status, install_type, host)
                VALUES (?, ?, ?, '{}', 1, 'SSE', ?)
                """, code, owner, code, url);
    }

    private HttpResponse<String> post(HttpClient client, String path, Map<String, String> payload) throws Exception {
        return client.send(request(client, path, payload).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(HttpClient client, String path, Map<String, ?> payload) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
        if (!path.equals("/user/register") && !path.equals("/user/login")) {
            builder.header(CsrfToken.HEADER, CsrfToken.of(client));
        }
        return builder;
    }

    private static long millis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private static class Discovery implements Runnable {
        private final CountDownLatch discoveriesStarted;
        private volatile long started;
        private volatile long finished;

        Discovery(CountDownLatch discoveriesStarted) {
            this.discoveriesStarted = discoveriesStarted;
        }

        @Override
        public void run() {
            started = System.nanoTime();
            discoveriesStarted.countDown();
            try {
                discoveriesStarted.await(5, TimeUnit.SECONDS);
                Thread.sleep(600);
                finished = System.nanoTime();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ModelFixture {
        @Bean
        @Primary
        TimedChatModel timingModel() {
            return new TimedChatModel();
        }
    }

    static class TimedChatModel implements ChatModel {
        private static final String FAIL_REQUEST = "fail-before-first-provider-chunk";
        private volatile long started;
        private volatile long status;
        private volatile long content;

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("the SSE request must stream from the provider");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            if (prompt.getUserMessage().getText().contains(FAIL_REQUEST)) {
                return Mono.delay(Duration.ofMillis(100))
                        .then(Mono.<ChatResponse>error(new IllegalStateException("fixture upstream detail"))).flux();
            }
            return Flux.defer(() -> {
                started = System.nanoTime();
                return Mono.delay(Duration.ofMillis(100)).map(ignored -> {
                    status = System.nanoTime();
                    return response(AssistantMessage.builder().content("")
                            .properties(Map.of("reasoningContent", "受控状态")).build());
                }).concatWith(Mono.delay(Duration.ofMillis(100)).map(ignored -> {
                    content = System.nanoTime();
                    return response(new AssistantMessage("受控回答"));
                }));
            });
        }

        private ChatResponse response(AssistantMessage message) {
            return new ChatResponse(List.of(new Generation(message)));
        }
    }
}
