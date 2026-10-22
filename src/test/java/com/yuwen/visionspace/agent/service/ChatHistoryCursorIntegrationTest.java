package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.mapper.AgentMessageMapper;
import com.yuwen.visionspace.mapper.AgentSessionMapper;
import com.yuwen.visionspace.model.dto.agent.ChatHistoryVO;
import com.yuwen.visionspace.model.dto.agent.MessageVO;
import com.yuwen.visionspace.model.entity.AgentSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.yuwen.visionspace.acceptance.IsolatedSpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 聊天历史游标分页的集成回归测试。
 * <p>
 * 这些用例必须打真实数据库：{@link ChatHistoryServiceTest} 用 Mockito 直接把
 * 「SQL 已经返回的一页」喂给 service，排序与游标不同源的问题在那一层根本不可见。
 * <p>
 * 背景：{@code createdTime} 是秒级 datetime，同一秒常有多条消息（本地库里 126 条
 * 消息中有 42 条落在并列组里）。旧实现按 {@code createdTime DESC} 排序却用
 * {@code id < beforeId} 翻页，并列组被分页边界切开时就会跳页或重复。
 */
@IsolatedSpringBootTest
class ChatHistoryCursorIntegrationTest {

    private static final int TIE_GROUP_SIZE = 3;
    private static final int LIMIT = 2;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private AgentMessageMapper messageMapper;

    @Autowired
    private AgentSessionMapper sessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String sessionId;

    @BeforeEach
    void setUp() {
        sessionId = "it-cursor-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM agent_message WHERE sessionId = ?", sessionId);
        jdbcTemplate.update("DELETE FROM agent_session WHERE sessionId = ?", sessionId);
    }

    /**
     * 同一秒的多条消息必须全部返回且只返回一次。
     * <p>
     * 造 3 条 createdTime 完全相同、id 递增的消息，页大小设为 2，
     * 使分页边界正好落在并列组中间——这正是旧实现丢消息的场景。
     */
    @Test
    void loadHistory_tieOnCreatedTime_returnsEveryMessageExactlyOnce() {
        Date sameSecond = new Date(1_700_000_000_000L);
        insertMessage("m1", sameSecond);
        insertMessage("m2", sameSecond);
        insertMessage("m3", sameSecond);

        List<MessageVO> all = drainHistory();

        assertEquals(List.of("m1", "m2", "m3"), contentsOf(all),
                "同一秒的消息应按 id 升序完整返回，不得重复或丢失");
    }

    /**
     * 并列组横跨分页边界时，游标页之间不得有交集。
     */
    @Test
    void loadHistory_acrossPages_noOverlapAndNoGap() {
        // 两组并列时间 + 一条独立时间，共 5 条
        Date t1 = new Date(1_700_000_000_000L);
        Date t2 = new Date(1_700_000_010_000L);
        insertMessage("a1", t1);
        insertMessage("a2", t1);
        insertMessage("b1", t2);
        insertMessage("b2", t2);
        insertMessage("c1", new Date(1_700_000_020_000L));

        List<MessageVO> all = drainHistory();

        assertEquals(List.of("a1", "a2", "b1", "b2", "c1"), contentsOf(all));
        assertEquals(5, all.stream().map(MessageVO::getId).distinct().count(),
                "分页过程中不得重复返回同一条消息");
    }

    /**
     * 首页必须返回最新消息，而不是最老的消息。
     * （9c46db9 之前的实现首页用 ASC，最新消息永远取不到。）
     */
    @Test
    void loadHistory_firstPage_returnsNewestMessages() {
        Date base = new Date(1_700_000_000_000L);
        for (int i = 1; i <= 5; i++) {
            insertMessage("m" + i, new Date(base.getTime() + i * 1000L));
        }

        ChatHistoryVO first = chatHistoryService.loadHistory(sessionId, null, LIMIT);

        assertTrue(first.isHasMore());
        assertEquals(List.of("m4", "m5"), contentsOf(first.getMessages()),
                "首页应是最近的两条，且按旧→新排列");
        assertNotNull(first.getNextCursor());
    }

    /**
     * id 与 createdTime 逆序时，分页不得丢消息。
     * <p>
     * 写入走 {@code @Async("agentAsyncExecutor")} 的线程池，消息的 id 是写入时分配的，
     * createdTime 也是写入时打的，两者在同一条记录上一致；但不同记录之间并不保证
     * id 顺序等于时间顺序——线程调度会让后开始的消息先落库。
     * <p>
     * 旧实现按 {@code createdTime DESC} 排序、用 {@code id < beforeId} 翻页，
     * 只要一页里出现「id 大但时间旧」的行，下一页就会整体跳过这些行。
     */
    @Test
    void loadHistory_idOutOfOrderWithTime_returnsEveryMessage() {
        Date base = new Date(1_700_000_000_000L);
        // 按 createdTime 升序：t+0s, t+10s, t+20s, t+30s
        // 但 id 顺序被打乱：t+20s 的记录先落库拿到最小 id
        insertMessage("oldest", new Date(base.getTime()));
        insertMessage("newest", new Date(base.getTime() + 30_000L));
        insertMessage("middle", new Date(base.getTime() + 20_000L));
        insertMessage("second", new Date(base.getTime() + 10_000L));

        List<MessageVO> all = drainHistory();

        assertEquals(List.of("oldest", "second", "middle", "newest"), contentsOf(all),
                "按时间翻页时不得因为 id 与时间逆序而跳过消息");
    }

    /**
     * 走完整的分页链后，nextCursor 在最后一页必须为 null。
     */
    @Test
    void loadHistory_lastPage_hasNoMoreAndNullCursor() {
        insertMessage("only", new Date(1_700_000_000_000L));

        ChatHistoryVO page = chatHistoryService.loadHistory(sessionId, null, LIMIT);

        assertFalse(page.isHasMore());
        assertEquals(null, page.getNextCursor());
        assertEquals(List.of("only"), contentsOf(page.getMessages()));
    }

    /**
     * 跨用户写入：归属条件在 SQL 里，别人写入不产生任何行，也不改会话摘要。
     * <p>
     * Mockito 那层看不见这条 SQL 的 WHERE，只有真实数据库能证明「零消息插入」。
     */
    @Test
    void writesFromAnotherUserProduceNoRows() {
        Long owner = 910_000_000L + (long) (Math.random() * 100_000);
        Long stranger = owner + 1;
        insertSession(owner, sessionId, new Date(1_700_000_000_000L));

        // 直接调用目标对象：@Async 代理会让写入变成异步，断言会在写入前跑完。
        // 这里验证的是 SQL 的归属条件，与线程调度无关。
        ChatHistoryService target = targetService();
        int before = messageCount();
        target.saveAssistantMessage(sessionId, stranger, "forged reply", "text");
        // 跨用户调用 saveUserMessage 会先做归属核对并抛出，属于预期的拒绝
        assertThrows(RuntimeException.class,
                () -> target.saveUserMessage(sessionId, stranger, "forged message"));
        target.saveSummary(sessionId, stranger, "forged summary", 1);

        assertEquals(before, messageCount(), "stranger writes must insert no messages");
        assertEquals(null, jdbcTemplate.queryForObject(
                "SELECT summaryContent FROM agent_session WHERE sessionId = ?", String.class, sessionId),
                "stranger must not overwrite the summary");
        assertEquals(owner, jdbcTemplate.queryForObject(
                "SELECT userId FROM agent_session WHERE sessionId = ?", Long.class, sessionId),
                "ownership must not change");
    }

    /**
     * 首次创建的原子性：并发用同一 threadId 绑定两个用户，只保留一个所有者，
     * 并且落库的消息全部属于这个所有者。
     */
    @Test
    void concurrentFirstCreationBindsOneOwner() throws Exception {
        Long first = 920_000_000L + (long) (Math.random() * 100_000);
        Long second = first + 1;

        ChatHistoryService target = targetService();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (Long userId : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        target.saveUserMessage(sessionId, userId, "hello from " + userId);
                    } catch (RuntimeException ignored) {
                        // 归属核对失败是允许的结果；关键是数据库里不能出现第二个所有者
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<Long> owners = jdbcTemplate.queryForList(
                "SELECT DISTINCT userId FROM agent_session WHERE sessionId = ?", Long.class, sessionId);
        assertEquals(1, owners.size(), "exactly one owner must be bound: " + owners);
        Long owner = owners.get(0);
        assertEquals(owner, jdbcTemplate.queryForObject(
                "SELECT userId FROM agent_session WHERE sessionId = ?", Long.class, sessionId));
        // 归属未成立的一方即使先跑了应用层逻辑，也不能留下消息
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message WHERE sessionId = ? AND content LIKE ?",
                Integer.class, sessionId, "%" + (owner.equals(first) ? second : first) + "%"),
                "rejected writer must leave no message");
    }

    /**
     * 会话列表同样用 (updatedTime, id) 复合游标：id 大但 updatedTime 旧的会话不得被跳过。
     * <p>
     * 旧实现 {@code id < beforeId} 配 {@code ORDER BY updatedTime DESC}，
     * 第二页会把「id 更大、updatedTime 更旧」的会话永久漏掉。
     */
    @Test
    void getUserSessions_doesNotSkipOlderSessionsWithLargerId() {
        Long userId = 900_000_000L + (long) (Math.random() * 100_000);
        // s1: id 小、最新；s2: id 大、最旧。旧实现在 s1 之后翻页会跳过 s2。
        String s1 = insertSession(userId, "s1", new Date(1_700_000_020_000L));
        String s2 = insertSession(userId, "s2", new Date(1_700_000_010_000L));
        String s3 = insertSession(userId, "s3", new Date(1_700_000_000_000L));

        try {
            List<String> seen = new ArrayList<>();
            Long cursor = null;
            for (int page = 0; page < 5; page++) {
                List<AgentSession> rows = sessionMapper.selectByUserIdCursor(userId, cursor, 1);
                if (rows.isEmpty()) {
                    break;
                }
                rows.forEach(r -> seen.add(r.getSessionId()));
                cursor = rows.get(rows.size() - 1).getId();
            }

            assertEquals(3, seen.size(), "三个会话都必须被翻到，实际: " + seen);
            assertTrue(seen.containsAll(List.of(s1, s2, s3)), "实际: " + seen);
        } finally {
            jdbcTemplate.update("DELETE FROM agent_session WHERE userId = ?", userId);
        }
    }

    // ==================== helpers ====================

    private List<MessageVO> drainHistory() {
        List<MessageVO> all = new ArrayList<>();
        Long cursor = null;
        for (int page = 0; page < 20; page++) {
            ChatHistoryVO history = chatHistoryService.loadHistory(sessionId, cursor, LIMIT);
            List<MessageVO> messages = history.getMessages();
            if (messages.isEmpty()) {
                break;
            }
            all.addAll(0, messages);
            if (!history.isHasMore()) {
                break;
            }
            cursor = history.getNextCursor();
        }
        return all;
    }

    private List<String> contentsOf(List<MessageVO> messages) {
        return messages.stream().map(MessageVO::getContent).toList();
    }

    /**
     * 去掉 @Async 代理，让写入在调用线程内完成，便于断言「零副作用」。
     */
    private ChatHistoryService targetService() {
        return org.springframework.test.util.AopTestUtils.getTargetObject(chatHistoryService);
    }

    private int messageCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message WHERE sessionId = ?", Integer.class, sessionId);
    }

    private void insertMessage(String content, Date createdTime) {
        jdbcTemplate.update(
                "INSERT INTO agent_message (sessionId, role, subType, content, tokenCount, isSummary, createdTime) " +
                        "VALUES (?, 'USER', NULL, ?, 0, 0, ?)",
                sessionId, content, new java.sql.Timestamp(createdTime.getTime()));
    }

    private String insertSession(Long userId, String sessionId, Date updatedTime) {
        jdbcTemplate.update(
                "INSERT INTO agent_session (sessionId, userId, title, status, lastMessage, messageCount, " +
                        "createdTime, updatedTime, isDelete) VALUES (?, ?, ?, 1, '', 0, ?, ?, 0)",
                sessionId, userId, sessionId,
                new java.sql.Timestamp(updatedTime.getTime()),
                new java.sql.Timestamp(updatedTime.getTime()));
        return sessionId;
    }
}
