package com.yuwen.visionspace.manager.websocket;

import com.yuwen.visionspace.acceptance.IsolatedSpringBootTest;
import com.yuwen.visionspace.manager.websocket.model.PictureEditResponseMessage;
import com.yuwen.visionspace.manager.websocket.monitor.PictureEditMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@IsolatedSpringBootTest
class PictureEditBroadcastBusTest {
    @Autowired private RedisConnectionFactory factory;
    @Autowired private StringRedisTemplate redis;

    private PictureEditBroadcastBus bus(PictureEditHandler handler, PictureEditMonitor monitor) {
        var bus = new PictureEditBroadcastBus();
        ReflectionTestUtils.setField(bus, "pictureEditHandler", handler);
        ReflectionTestUtils.setField(bus, "monitor", monitor);
        ReflectionTestUtils.setField(bus, "redisConnectionFactory", factory);
        ReflectionTestUtils.setField(bus, "stringRedisTemplate", redis);
        bus.subscribe();
        return bus;
    }

    private PictureEditResponseMessage message() {
        var message = new PictureEditResponseMessage();
        message.setType("ENTER_EDIT");
        message.setSessionId("holder");
        return message;
    }

    @Test
    void sourceInstanceIsSkippedEvenWithoutAnExcludedSession() throws Exception {
        var local = mock(PictureEditHandler.class);
        var remote = mock(PictureEditHandler.class);
        var first = bus(local, new PictureEditMonitor());
        var second = bus(remote, new PictureEditMonitor());
        try {
            first.publish(449900001L, null, message());
            verify(remote, timeout(5000).times(1)).relayBusMessage(eq(449900001L), any());
            Thread.sleep(200);
            verifyNoInteractions(local);
            verify(remote, times(1)).relayBusMessage(eq(449900001L), any());
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }

    @Test
    void slowRecipientCannotGrowBroadcastQueuePastItsBound() throws Exception {
        var remote = mock(PictureEditHandler.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(15, TimeUnit.SECONDS);
            return 0;
        }).when(remote).relayBusMessage(eq(449900002L), any());
        var monitor = new PictureEditMonitor();
        var first = bus(mock(PictureEditHandler.class), new PictureEditMonitor());
        var second = bus(remote, monitor);
        try {
            first.publish(449900002L, null, message());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 1100; i++) first.publish(449900002L, null, message());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (((Number) monitor.snapshot(0).get("broadcastRejections")).longValue() == 0
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(((Number) monitor.snapshot(0).get("broadcastRejections")).longValue() > 0);
            assertEquals(0, second.remainingCapacity(), "queue is full at its fixed bound");
        } finally {
            release.countDown();
            first.shutdown();
            second.shutdown();
        }
    }

    @Test
    void expiredConnectionCannotPublishIntoItsSuccessorsLease() throws Exception {
        long pictureId = 449900003L;
        String key = "picture:edit:lease:" + pictureId;
        var remote = mock(PictureEditHandler.class);
        var first = bus(mock(PictureEditHandler.class), new PictureEditMonitor());
        var second = bus(remote, new PictureEditMonitor());
        try {
            redis.opsForValue().set(key, "successor|101", java.time.Duration.ofSeconds(10));
            assertFalse(first.publishEditIfHolder(pictureId, "expired", message()));
            Thread.sleep(200);
            verifyNoInteractions(remote);
            assertTrue(first.publishEditIfHolder(pictureId, "successor", message()));
            verify(remote, timeout(5000)).relayBusMessage(eq(pictureId), any());
            assertEquals("successor|101", redis.opsForValue().get(key));
        } finally {
            redis.delete(key);
            first.shutdown();
            second.shutdown();
        }
    }
}
