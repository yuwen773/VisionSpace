package com.yuwen.visionspace.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 WebSocket 协议验收（工单 44，单实例语义部分）。
 *
 * <p>走真实 TCP -> 握手拦截器（Cookie 会话 + 资源授权）-> WebSocket -> Disruptor
 * -> 租约管理器（隔离 Redis）-> 广播。跨实例部分由独立脚本
 * scripts/collab-verification/collab-dual-instance.ps1 驱动两个真实后端进程验证。
 */
@IsolatedSpringBootTest
class WsPictureEditContractTest {

    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    private static final long BASE = 44900_000L;
    private static final String PASSWORD = "acceptance-password";
    private final ConcurrentLinkedQueue<WebSocket> sockets = new ConcurrentLinkedQueue<>();
    private final AtomicInteger idSeq = new AtomicInteger();
    private long fixtureSeq;

    @BeforeEach
    void cleanFixtures() {
        fixtureSeq = System.nanoTime() % 100_000;
        jdbc.update("DELETE FROM picture WHERE id >= ? AND id < ?", BASE, BASE + 10_000_000);
        jdbc.update("DELETE FROM space WHERE id >= ? AND id < ?", BASE, BASE + 10_000_000);
        jdbc.update("DELETE FROM space_user WHERE spaceId >= ? AND spaceId < ?", BASE, BASE + 10_000_000);
    }

    @AfterEach
    void closeSockets() {
        sockets.forEach(s -> {
            try {
                s.abort();
            } catch (Exception ignored) {
            }
        });
        sockets.clear();
        userClients.values().forEach(c -> {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        });
        userClients.clear();
        userAccounts.clear();
    }

    // ---- 29.1 竞争只产生一个有效持有者 ----

    @Test
    void twoConnectionsCompeteAndOnlyOneHoldsLease() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);

        try (WsClient first = connect(ownerId, pictureId); WsClient second = connect(ownerId, pictureId)) {
            first.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(first.awaitType("ENTER_EDIT", 5).isPresent(), "first claimer must win the lease");
            Thread.sleep(300);
            assertEquals(1, first.count("ENTER_EDIT"), "pub/sub must not replay the local broadcast twice");

            second.send(Map.of("type", "ENTER_EDIT"));
            var rejected = second.awaitType("ERROR", 5);
            assertTrue(rejected.isPresent(), "second claimer must be rejected");
            assertTrue(rejected.get().path("message").asText().contains("正在编辑"), rejected.toString());

            // 编辑权只在持有者：second 发操作不广播
            int firstEditsBefore = first.count("EDIT_ACTION");
            second.send(Map.of("type", "EDIT_ACTION", "editAction", "ZOOM_IN"));
            assertTrue(second.awaitType("ERROR", 5).isPresent(), "non-holder action must be rejected");
            Thread.sleep(500);
            assertEquals(firstEditsBefore, first.count("EDIT_ACTION"), "non-holder must not broadcast edit");
        }
    }

    // ---- 29.2 同用户另一标签关闭不释放当前租约 ----

    @Test
    void closingAnotherTabDoesNotReleaseCurrentLease() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);

        // holder 不进 try-with-resources：它模拟仍然打开的标签页
        WsClient holder = connect(ownerId, pictureId);
        holder.send(Map.of("type", "ENTER_EDIT"));
        assertTrue(holder.awaitType("ENTER_EDIT", 5).isPresent());

        try (WsClient otherTab = connect(ownerId, pictureId)) {
            otherTab.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(otherTab.awaitType("ERROR", 5).isPresent(), "same user second tab must be rejected");
        }
        // otherTab 已关闭：holder 的租约必须还在（同用户关闭另一标签不误释放）
        try (WsClient probe = connect(ownerId, pictureId)) {
            probe.send(Map.of("type", "ENTER_EDIT"));
            var rejected = probe.awaitType("ERROR", 5);
            assertTrue(rejected.isPresent(), "lease must survive another tab closing: " + rejected);
            assertTrue(redis.opsForValue().get("picture:edit:lease:" + pictureId) != null,
                    "lease key must still exist after another tab closed");
        }
    }

    // ---- 29.2 断线后过期接管：持有者断开，租约过期后其他连接可接管 ----

    @Test
    void leaseExpiresAndAllowsTakeoverAfterHolderDisconnects() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);

        WsClient holder = connect(ownerId, pictureId);
        holder.send(Map.of("type", "ENTER_EDIT"));
        assertTrue(holder.awaitType("ENTER_EDIT", 5).isPresent());
        holder.close();
        // 等待 Redis TTL（集成 profile 租约期限短）——期限由配置注入，这里等待后探测
        awaitLeaseGone(pictureId);
        try (WsClient successor = connect(ownerId, pictureId)) {
            successor.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(successor.awaitType("ENTER_EDIT", 5).isPresent(), "successor must take over after expiry");
        }
    }

    // ---- 29.2 迟到事件：旧连接的迟到 EXIT 不影响新持有者 ----

    @Test
    void staleExitFromOldConnectionDoesNotDisturbNewLease() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);

        try (WsClient successor = connect(ownerId, pictureId)) {
            successor.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(successor.awaitType("ENTER_EDIT", 5).isPresent());
            // 模拟旧连接：用一条不持有租约的连接发送 EXIT
            try (WsClient stale = connect(ownerId, pictureId)) {
                stale.send(Map.of("type", "EXIT_EDIT"));
                Thread.sleep(500);
                // 新租约不受影响
                assertTrue(redis.opsForValue().get("picture:edit:lease:" + pictureId) != null,
                        "stale exit must not release the new lease");
            }
        }
    }

    // ---- 29.2 撤权即时生效 ----

    @Test
    void roleDemotionImmediatelyStopsWrites() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);
        long editorId = seedUser("editor_" + fixtureSeq);
        joinTeam(spaceId, editorId, "editor");

        WsClient editor = connect(editorId, pictureId);
        editor.send(Map.of("type", "ENTER_EDIT"));
        assertTrue(editor.awaitType("ENTER_EDIT", 5).isPresent(), "editor must acquire lease");

        // 降权为 viewer
        jdbc.update("UPDATE space_user SET spaceRole = 'viewer' WHERE spaceId = ? AND userId = ?", spaceId, editorId);

        editor.send(Map.of("type", "EDIT_ACTION", "editAction", "ZOOM_IN"));
        var denied = editor.awaitType("ERROR", 5);
        assertTrue(denied.isPresent(), "demoted user must not perform actions");
        // 拒绝后旧租约被收回：其他编辑可立即接管
        awaitLeaseGone(pictureId);
        try (WsClient owner = connect(ownerId, pictureId)) {
            owner.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(owner.awaitType("ENTER_EDIT", 5).isPresent(), "owner must take over after demotion");
        }
        editor.socket().abort();
    }

    // ---- 29.2 注销即时生效 ----

    @Test
    void logoutImmediatelyStopsWrites() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);
        long editorId = seedUser("gone_" + fixtureSeq);
        joinTeam(spaceId, editorId, "editor");

        WsClient editor = connect(editorId, pictureId);
        editor.send(Map.of("type", "ENTER_EDIT"));
        assertTrue(editor.awaitType("ENTER_EDIT", 5).isPresent());

        // 旁观者：用来证明注销后的操作没有产生任何编辑广播
        try (WsClient observer = connect(ownerId, pictureId)) {
            // 同一 Cookie 会话注销（REST logout）
            logout(editor.http());
            Thread.sleep(800); // 注销失效 Redis 会话后，下一条消息的复核会拒绝

            // 服务端可能在收到消息前就关掉了连接：发送失败本身就是「写不进去」
            boolean sent = editor.sendIfOpen(Map.of("type", "EDIT_ACTION", "editAction", "ZOOM_IN"));
            var denied = editor.awaitType("ERROR", 3);
            assertTrue(denied.isPresent() || !sent || editor.isClosed(),
                    "logged-out user must be rejected or disconnected before writing");
            assertFalse(observer.awaitType("EDIT_ACTION", 2).isPresent(),
                    "a logged-out editor must not produce any edit broadcast");
        }
        editor.socket().abort();
    }

    @Test
    void idleLoggedOutHolderCannotKeepRenewing() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);
        WsClient holder = connect(ownerId, pictureId);
        holder.send(Map.of("type", "ENTER_EDIT"));
        assertTrue(holder.awaitType("ENTER_EDIT", 5).isPresent());
        logout(holder.http());
        // 不发送任何 WS 消息：续期本身必须复核登录态并释放。
        awaitLeaseGone(pictureId);
        try (WsClient successor = connect(ownerId, pictureId)) {
            successor.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(successor.awaitType("ENTER_EDIT", 5).isPresent());
        }
    }

    @Test
    void changingHttpLoginCannotChangeAnExistingSocketIdentity() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        long editorId = seedUser("switch_" + fixtureSeq);
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);
        joinTeam(spaceId, editorId, "editor");
        try (WsClient holder = connect(ownerId, pictureId); WsClient observer = connect(editorId, pictureId)) {
            holder.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(holder.awaitType("ENTER_EDIT", 5).isPresent());
            var switched = post(holder.http(), "/user/login", Map.of(
                    "userAccount", userAccounts.get(editorId), "userPassword", PASSWORD));
            assertEquals(0, switched.path("code").asInt());
            holder.send(Map.of("type", "EDIT_ACTION", "editAction", "ZOOM_IN"));
            awaitLeaseGone(pictureId);
            Thread.sleep(300);
            assertEquals(0, observer.count("EDIT_ACTION"), "old socket must never borrow the new login identity");
        }
    }

    @Test
    void onlineAuthorizedHolderIsRenewedBeyondOriginalTtl() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);
        try (WsClient holder = connect(ownerId, pictureId); WsClient rival = connect(ownerId, pictureId)) {
            holder.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(holder.awaitType("ENTER_EDIT", 5).isPresent());
            Thread.sleep(4500);
            rival.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(rival.awaitType("ERROR", 5).isPresent(), "live holder must renew beyond the 3s TTL");
        }
    }

    // ---- 29.3 非法消息隔离：坏消息后合法消息仍可处理 ----

    @Test
    void invalidMessagesAreIsolatedAndLaterValidOnesStillProcessed() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);

        try (WsClient client = connect(ownerId, pictureId)) {
            // 空/非法类型
            client.send(Map.of("type", ""));
            assertTrue(client.awaitType("ERROR", 5).isPresent(), "empty type must be rejected");
            client.send(Map.of("type", "HACK"));
            assertTrue(client.awaitType("ERROR", 5).isPresent(), "unknown type must be rejected");
            // 损坏 JSON
            client.sendRaw("this is not json {{{");
            assertTrue(client.awaitType("ERROR", 5).isPresent(), "broken json must be rejected");
            // 之后的合法消息仍然正常处理
            client.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(client.awaitType("ENTER_EDIT", 5).isPresent(), "valid message after failures must work");
            client.send(Map.of("type", "EXIT_EDIT"));
            assertTrue(client.awaitType("EXIT_EDIT", 5).isPresent(), "exit after failures must work");
        }
    }

    // ---- 29.4 背压：洪泛后有界拒绝，连接存活，后续消息仍可处理 ----

    @Test
    void floodTriggersBoundedRejectionAndConnectionStaysUsable() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);

        try (WsClient client = connect(ownerId, pictureId)) {
            client.send(Map.of("type", "ENTER_EDIT"));
            assertTrue(client.awaitType("ENTER_EDIT", 5).isPresent());
            // 快速洪泛大量动作消息（远超队列容量上限在隔离环境不会，
            // 但消费被复核拖慢时部分消息会被拒绝或排队，两种都合法：
            // 断言最终通道仍可用且无未处理堆积导致的挂死）
            for (int i = 0; i < 2000; i++) {
                client.sendRaw("{\"type\":\"EDIT_ACTION\",\"editAction\":\"ZOOM_IN\"}");
            }
            // 洪泛之后通道仍能处理合法消息（复核逐条走 Redis+DB，留足排空时间）
            client.send(Map.of("type", "EXIT_EDIT"));
            assertTrue(client.awaitType("EXIT_EDIT", 60).isPresent(),
                    "channel must remain usable after flood");
        }
    }

    // ---- 29.4 可观察性：admin 端点可读，普通用户拒绝 ----

    @Test
    void monitorEndpointIsAdminOnlyAndReadable() throws Exception {
        long ownerId = seedOwner();
        try (HttpClient userClient = loginClient("viewer_" + fixtureSeq);
             HttpClient adminClient = loginClient("admin_" + fixtureSeq, true)) {
            var denied = get(userClient, "/picture/collab/monitor");
            assertEquals(40101, denied.path("code").asInt(), denied.toString());
            var allowed = get(adminClient, "/picture/collab/monitor");
            assertEquals(0, allowed.path("code").asInt(), allowed.toString());
            assertTrue(allowed.path("data").has("received"));
            assertTrue(allowed.path("data").has("queueRemaining"));
            assertTrue(allowed.path("data").has("activeLocalLeases"));
            assertTrue(allowed.path("data").has("broadcastQueueRemaining"));
        }
    }

    // ---- 授权边界：无权用户握手被拒、匿名被拒 ----

    @Test
    void unauthorizedUsersAreRejectedAtHandshake() throws Exception {
        long spaceId = nextId();
        long pictureId = nextId();
        long ownerId = seedOwner();
        seedTeamSpaceWithPicture(spaceId, pictureId, ownerId);
        long viewerId = seedUser("viewer_" + fixtureSeq);
        joinTeam(spaceId, viewerId, "viewer");

        // viewer（有登录、无编辑权）握手被拒
        try {
            HttpClient viewerClient = client();
            loginAs(viewerClient, "viewer_" + fixtureSeq);
            viewerClient.newWebSocketBuilder().buildAsync(wsUri(pictureId), new WsListener(new ConcurrentLinkedQueue<>(), new AtomicBoolean())).join();
            throw new AssertionError("viewer handshake must fail");
        } catch (Exception expected) {
            // 握手被拒绝表现为 join 异常
        }
        // 匿名（无 Cookie）握手被拒
        HttpClient anonymous = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        try {
            anonymous.newWebSocketBuilder().buildAsync(wsUri(pictureId), new WsListener(new ConcurrentLinkedQueue<>(), new AtomicBoolean())).join();
            throw new AssertionError("anonymous handshake must fail");
        } catch (Exception expected) {
            // 握手被拒绝表现为 join 异常
        }
    }

    // ---- helpers ----

    private void loginAs(HttpClient client, String account) throws Exception {
        var login = post(client, "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, login.path("code").asInt(), login.toString());
    }

    private void awaitLeaseGone(long pictureId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (redis.opsForValue().get("picture:edit:lease:" + pictureId) == null) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("lease for picture " + pictureId + " never expired");
    }

    private long nextId() {
        return BASE + (fixtureSeq * 1000) + idSeq.incrementAndGet() * 10L;
    }

    private long seedOwner() throws Exception {
        return seedUser("owner_" + fixtureSeq);
    }

    private long seedUser(String account) throws Exception {
        try (HttpClient client = client()) {
            var registration = post(client, "/user/register", Map.of(
                    "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
            assertEquals(0, registration.path("code").asInt(), registration.toString());
            long userId = registration.path("data").asLong();
            userAccounts.put(userId, account);
            return userId;
        }
    }

    private void seedTeamSpaceWithPicture(long spaceId, long pictureId, long ownerId) {
        jdbc.update("""
                INSERT INTO space (id, spaceName, userId, spaceLevel, spaceType, maxSize, maxCount,
                                   totalSize, totalCount, isDelete)
                VALUES (?, 'team space', ?, 1, 1, 1000, 1000, 0, 1, 0)
                """, spaceId, ownerId);
        jdbc.update("""
                INSERT INTO picture (id, url, name, userId, picSize, spaceId, reviewStatus, isDelete)
                VALUES (?, 'team-picture', 'original', ?, 10, ?, 1, 0)
                """, pictureId, ownerId, spaceId);
        joinTeam(spaceId, ownerId, "admin");
    }

    private void joinTeam(long spaceId, long userId, String role) {
        jdbc.update("INSERT INTO space_user (spaceId, userId, spaceRole) VALUES (?, ?, ?)",
                spaceId, userId, role);
    }

    /** userId -> 该用户的登录会话（同用户多标签共享同一 Cookie 会话，符合真实浏览器语义）。 */
    private final Map<Long, HttpClient> userClients = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, String> userAccounts = new java.util.concurrent.ConcurrentHashMap<>();

    private WsClient connect(long userId, long pictureId) throws Exception {
        HttpClient client = userClients.computeIfAbsent(userId, id -> client());
        String account = userAccounts.get(userId);
        if (account == null) {
            // 该用户未经过本测试注册（admin 用例等），无法登录
            throw new IllegalStateException("user " + userId + " was not registered by this test");
        }
        var login = post(client, "/user/login", Map.of("userAccount", account, "userPassword", PASSWORD));
        assertEquals(0, login.path("code").asInt(), login.toString());
        return new WsClient(client, pictureId);
    }

    private void logout(HttpClient client) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri("/user/logout"))
                        .timeout(Duration.ofSeconds(10))
                        .header(CsrfToken.HEADER, CsrfToken.of(client))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    private HttpClient loginClient(String account) throws Exception {
        return loginClient(account, false);
    }

    private HttpClient loginClient(String account, boolean admin) throws Exception {
        HttpClient client = client();
        String suffix = admin ? "_admin_" : "_";
        var registration = post(client, "/user/register", Map.of(
                "userAccount", account + suffix + fixtureSeq, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        assertEquals(0, registration.path("code").asInt(), registration.toString());
        if (admin) {
            jdbc.update("UPDATE user SET userRole = 'admin' WHERE id = ?", registration.path("data").asLong());
        }
        var login = post(client, "/user/login", Map.of(
                "userAccount", account + suffix + fixtureSeq, "userPassword", PASSWORD));
        assertEquals(0, login.path("code").asInt(), login.toString());
        return client;
    }

    private void login(HttpClient client, String account) throws Exception {
        post(client, "/user/register", Map.of(
                "userAccount", account, "userPassword", PASSWORD, "checkPassword", PASSWORD));
        post(client, "/user/login", Map.of(
                "userAccount", account, "userPassword", PASSWORD));
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private JsonNode get(HttpClient client, String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private JsonNode post(HttpClient client, String path, Map<?, ?> body) throws Exception {
        var response = client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header(CsrfToken.HEADER, CsrfToken.of(client))
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api" + path);
    }

    private URI wsUri(long pictureId) {
        return URI.create("ws://127.0.0.1:" + port + "/api/ws/picture/edit?pictureId=" + pictureId);
    }

    /** 真实 WebSocket 客户端封装：cookie 握手 + 类型计数 + 有界等待。 */
    private final class WsClient implements AutoCloseable {
        private final HttpClient http;
        private final WebSocket socket;
        private final ConcurrentLinkedQueue<JsonNode> messages = new ConcurrentLinkedQueue<>();
        /** 服务端已关闭连接：此时发送会抛 IOException，用例要能把它当成预期结果。 */
        private final AtomicBoolean closed = new AtomicBoolean();
        private int scanned;

        WsClient(HttpClient http, long pictureId) {
            this.http = http;
            this.socket = http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(wsUri(pictureId), new WsListener(messages, closed)).join();
            sockets.add(socket);
        }

        void send(Map<?, ?> body) throws Exception {
            socket.sendText(json.writeValueAsString(body), true).join();
        }

        /**
         * 连接已被服务端关闭时发送必然失败：返回 false 表示「这条写没有发出去」，
         * 由用例把它与「收到 ERROR 拒绝」一起当作预期结果，而不是让整个用例报错。
         */
        boolean sendIfOpen(Map<?, ?> body) throws Exception {
            if (closed.get()) {
                return false;
            }
            try {
                socket.sendText(json.writeValueAsString(body), true).join();
                return true;
            } catch (java.util.concurrent.CompletionException e) {
                if (e.getCause() instanceof java.io.IOException) {
                    closed.set(true);
                    return false;
                }
                throw e;
            }
        }

        boolean isClosed() {
            return closed.get();
        }

        void sendRaw(String raw) {
            socket.sendText(raw, true).join();
        }

        java.util.Optional<JsonNode> awaitType(String type, int seconds) throws Exception {
            long deadline = System.currentTimeMillis() + seconds * 1000L;
            while (System.currentTimeMillis() < deadline) {
                List<JsonNode> received = List.copyOf(messages);
                while (scanned < received.size()) {
                    JsonNode node = received.get(scanned++);
                    if (type.equals(node.path("type").asText())) {
                        return java.util.Optional.of(node);
                    }
                }
                Thread.sleep(50);
            }
            return java.util.Optional.empty();
        }

        int count(String type) {
            return (int) messages.stream().filter(n -> type.equals(n.path("type").asText())).count();
        }

        HttpClient http() {
            return http;
        }

        WebSocket socket() {
            return socket;
        }

        @Override
        public void close() {
            if (!socket.isOutputClosed()) socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private static final class WsListener implements WebSocket.Listener {
        private final ConcurrentLinkedQueue<JsonNode> messages;
        private final AtomicBoolean closed;
        private final StringBuilder buffer = new StringBuilder();

        WsListener(ConcurrentLinkedQueue<JsonNode> messages, AtomicBoolean closed) {
            this.messages = messages;
            this.closed = closed;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.set(true);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.set(true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                try {
                    messages.add(new ObjectMapper().readTree(buffer.toString()));
                } catch (Exception ignored) {
                }
                buffer.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }
    }
}
