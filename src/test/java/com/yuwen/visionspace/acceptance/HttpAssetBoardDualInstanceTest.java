package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** 第二 JVM 由本用例启动、停止和重启，始终共享脚本提供的隔离库。 */
@IsolatedSpringBootTest
class HttpAssetBoardDualInstanceTest {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    void twoInstancesAndRestartRetainOperationUniquenessAndRevisionConditions() throws Exception {
        Path logs = Path.of("target/board-dual-instance").toAbsolutePath();
        Files.createDirectories(logs);
        try (var browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()) {
            String account = "dual_board_" + System.nanoTime();
            long user = call(browser, port, "POST", "/user/register", Map.of("userAccount", account, "userPassword", "acceptance-password", "checkPassword", "acceptance-password")).path("data").asLong();
            assertEquals(0, call(browser, port, "POST", "/user/login", Map.of("userAccount", account, "userPassword", "acceptance-password")).path("code").asInt());
            long space = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
            jdbc.update("INSERT INTO space (id, userId, spaceType, spaceName) VALUES (?, ?, 1, 'dual-board')", space, user);
            jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, 'viewer')", space, user);
            var result = call(browser, port, "POST", "/agent/asset-search", Map.of("spaceId", String.valueOf(space), "criteria", Map.of("orientation", "LANDSCAPE"))).path("data");
            var create = Map.of("operationId", "dual-create", "resultId", result.path("resultId").asText(), "title", "双实例板", "items", List.of(), "groups", List.of());
            String board;
            try (Instance second = start(logs, "before-restart")) {
                var receipts = race(() -> call(browser, port, "POST", "/agent/boards", create),
                        () -> call(browser, second.port, "POST", "/agent/boards", create));
                assertEquals(0, receipts.get(0).path("code").asInt(), receipts.toString());
                assertEquals(receipts.get(0), receipts.get(1));
                board = receipts.get(0).path("data").path("boardId").asText();
                var one = Map.of("operationId", "dual-one", "expectedRevision", 1, "title", "一", "items", List.of(), "groups", List.of());
                var two = Map.of("operationId", "dual-two", "expectedRevision", 1, "title", "二", "items", List.of(), "groups", List.of());
                var outcomes = race(() -> call(browser, port, "PUT", "/agent/boards/" + board, one),
                        () -> call(browser, second.port, "PUT", "/agent/boards/" + board, two));
                assertEquals(1, outcomes.stream().filter(r -> r.path("code").asInt() == 0).count(), outcomes.toString());
                assertEquals(1, outcomes.stream().filter(r -> "REVISION_CONFLICT".equals(r.path("message").asText())).count());
            }
            try (Instance restarted = start(logs, "after-restart")) {
                assertEquals(2, call(browser, restarted.port, "GET", "/agent/boards/" + board, null).path("data").path("revision").asInt());
                var receipt = call(browser, restarted.port, "GET", "/agent/board-operations/dual-create", null).path("data");
                assertEquals(board, receipt.path("boardId").asText());
                assertEquals(1, receipt.path("revision").asInt());
                assertEquals(1, call(browser, restarted.port, "POST", "/agent/boards", create).path("data").path("revision").asInt());
                assertEquals(2, call(browser, port, "GET", "/agent/boards/" + board, null).path("data").path("revision").asInt());
                var newWrite = new HashMap<String, Object>(create); newWrite.put("operationId", "rollback-new");
                assertEquals("INVALID_OPERATION_STATE", call(browser, restarted.port, "POST", "/agent/boards", newWrite).path("message").asText());
                assertEquals("NOT_RECORDED", call(browser, restarted.port, "GET", "/agent/board-operations/rollback-new", null).path("data").path("status").asText());
            }
        }
    }

    private List<JsonNode> race(Callable<JsonNode> one, Callable<JsonNode> two) throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return one.call(); });
            var b = pool.submit(() -> { gate.await(); return two.call(); });
            gate.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }
    private JsonNode call(HttpClient browser, int servicePort, String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + servicePort + "/api" + path)).timeout(java.time.Duration.ofSeconds(20));
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").header(CsrfToken.HEADER, CsrfToken.of(browser))
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return json.readTree(browser.send(request.build(), HttpResponse.BodyHandlers.ofString()).body());
    }
    private Instance start(Path logs, String name) throws Exception {
        Path ready = logs.resolve(name + "-" + UUID.randomUUID() + ".port");
        Path log = logs.resolve(name + ".log");
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("surefire.test.class.path"),
                VipSecondInstanceMain.class.getName(), ready.toString(), "--vs.instance-id-file=" + logs.resolve(name + ".instance"),
                "--vision-space.asset-board.writes-enabled=" + !name.equals("after-restart"))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(100);
            assertTrue(Files.exists(ready), "Second JVM did not start: " + log);
            return new Instance(process, ready, Integer.parseInt(Files.readString(ready).strip()));
        } catch (Throwable failure) { process.destroyForcibly(); throw failure; }
    }
    private record Instance(Process process, Path ready, int port) implements AutoCloseable {
        public void close() throws Exception {
            Files.writeString(Path.of(ready + ".stop"), "stop");
            if (!process.waitFor(25, TimeUnit.SECONDS)) { process.destroyForcibly(); assertTrue(process.waitFor(10, TimeUnit.SECONDS)); }
        }
    }
}
