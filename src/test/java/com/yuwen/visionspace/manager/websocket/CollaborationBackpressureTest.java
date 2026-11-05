package com.yuwen.visionspace.manager.websocket;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.yuwen.visionspace.manager.websocket.disruptor.PictureEditEvent;
import com.yuwen.visionspace.manager.websocket.disruptor.PictureEditEventProducer;
import com.yuwen.visionspace.manager.websocket.disruptor.PictureEditEventWorkHandler;
import com.yuwen.visionspace.manager.websocket.model.PictureEditRequestMessage;
import com.yuwen.visionspace.manager.websocket.lease.PictureEditLeaseManager;
import com.yuwen.visionspace.manager.websocket.lease.PictureEditLeaseCoordinator;
import com.yuwen.visionspace.manager.websocket.monitor.PictureEditMonitor;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.UserVO;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollaborationBackpressureTest {
    @Test
    void closingDuringAcquisitionReleasesTheNewLease() throws Exception {
        var handler = new PictureEditHandler(30);
        var leases = mock(PictureEditLeaseManager.class);
        var coordinator = mock(PictureEditLeaseCoordinator.class);
        var socket = mock(WebSocketSession.class);
        var user = new User();
        user.setId(9L);
        when(socket.getId()).thenReturn("closed-during-acquire");
        when(socket.isOpen()).thenReturn(true, false);
        when(coordinator.acquire(eq(1L), eq("closed-during-acquire"), eq(9L), any(), anyString()))
                .thenReturn(PictureEditLeaseManager.LeaseAcquireResult.acquired(9L));
        ReflectionTestUtils.setField(handler, "leaseManager", leases);
        ReflectionTestUtils.setField(handler, "leaseCoordinator", coordinator);
        handler.handleEnterEditMessage(new PictureEditRequestMessage(), socket, user, 1L);
        verify(leases).release(1L, "closed-during-acquire");
        verify(socket, never()).sendMessage(any());
    }

    @Test
    void revocationWhileWaitingForPictureLockDoesNotIssueCredential() throws Exception {
        var handler = new PictureEditHandler(30);
        var leases = mock(PictureEditLeaseManager.class);
        var coordinator = mock(PictureEditLeaseCoordinator.class);
        var checker = mock(WsEditAccessChecker.class);
        var socket = mock(WebSocketSession.class);
        var user = new User();
        user.setId(9L);
        when(socket.getId()).thenReturn("revoked-session");
        when(socket.isOpen()).thenReturn(true);
        when(socket.getAttributes()).thenReturn(Map.of());
        when(coordinator.acquire(eq(1L), eq("revoked-session"), eq(9L), any(), anyString()))
                .thenReturn(PictureEditLeaseManager.LeaseAcquireResult.acquired(9L));
        when(checker.check(any(), eq(1L))).thenReturn(WsEditAccessChecker.AccessCheckResult.denied("revoked"));
        ReflectionTestUtils.setField(handler, "leaseManager", leases);
        ReflectionTestUtils.setField(handler, "leaseCoordinator", coordinator);
        ReflectionTestUtils.setField(handler, "accessChecker", checker);
        ReflectionTestUtils.setField(handler, "monitor", new PictureEditMonitor());
        handler.handleEnterEditMessage(new PictureEditRequestMessage(), socket, user, 1L);
        verify(leases).release(1L, "revoked-session");
        verify(socket, never()).sendMessage(argThat(message -> message.getPayload().toString().contains("leaseToken\":\"")));
    }

    @Test
    void closedConnectionCannotAcquireALeaseAfterAuthorization() throws Exception {
        var handler = new PictureEditHandler(30);
        var leases = mock(PictureEditLeaseManager.class);
        var checker = mock(WsEditAccessChecker.class);
        var coordinator = mock(PictureEditLeaseCoordinator.class);
        var users = mock(UserService.class);
        var socket = mock(WebSocketSession.class);
        var user = new User();
        user.setId(9L);
        when(socket.getId()).thenReturn("closed-session");
        when(socket.isOpen()).thenReturn(true, false);
        when(socket.getAttributes()).thenReturn(Map.of("pictureId", 1L, "user", user));
        when(users.getUserVO(any())).thenReturn(new UserVO());
        ReflectionTestUtils.setField(handler, "leaseManager", leases);
        ReflectionTestUtils.setField(handler, "leaseCoordinator", coordinator);
        ReflectionTestUtils.setField(handler, "userService", users);
        ReflectionTestUtils.setField(handler, "monitor", new PictureEditMonitor());
        ReflectionTestUtils.setField(handler, "broadcastBus", mock(PictureEditBroadcastBus.class));
        ReflectionTestUtils.setField(handler, "accessChecker", checker);
        when(checker.check(any(), eq(1L))).thenAnswer(invocation -> {
            handler.afterConnectionClosed(socket, CloseStatus.NORMAL);
            return WsEditAccessChecker.AccessCheckResult.ok(user);
        });
        var worker = new PictureEditEventWorkHandler();
        ReflectionTestUtils.setField(worker, "pictureEditHandler", handler);
        ReflectionTestUtils.setField(worker, "accessChecker", checker);
        var event = new PictureEditEvent();
        var request = new PictureEditRequestMessage();
        request.setType("ENTER_EDIT");
        event.setPictureEditRequestMessage(request);
        event.setSession(socket);
        event.setPictureId(1L);
        worker.onEvent(event);
        verify(coordinator, never()).acquire(anyLong(), anyString(), anyLong(), any(), anyString());
    }

    @Test
    void fullQueueRejectsWithoutBlockingAndAcceptsAfterDraining() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(2);
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(PictureEditEvent::new, 2,
                Thread.ofPlatform().factory(), ProducerType.MULTI, new BlockingWaitStrategy());
        disruptor.handleEventsWith((event, sequence, end) -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            drained.countDown();
        });
        disruptor.start();
        var monitor = new PictureEditMonitor();
        var producer = new PictureEditEventProducer();
        ReflectionTestUtils.setField(producer, "pictureEditEventDisruptor", disruptor);
        ReflectionTestUtils.setField(producer, "monitor", monitor);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        try {
            assertTrue(producer.publishEvent(null, socket, null, 1L));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(producer.publishEvent(null, socket, null, 1L));
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),
                    () -> assertFalse(producer.publishEvent(null, socket, null, 1L)));
            assertEquals(1L, monitor.snapshot(0).get("rejectedByBackpressure"));
            verify(socket).sendMessage(argThat(message -> message.getPayload().toString().contains("ERROR")));
            release.countDown();
            assertTrue(drained.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (disruptor.getRingBuffer().remainingCapacity() != 2 && System.nanoTime() < deadline) Thread.sleep(5);
            assertTrue(producer.publishEvent(null, socket, null, 1L));
        } finally {
            release.countDown();
            disruptor.shutdown(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void singleProcessingExceptionDoesNotStopTheRealConsumer() throws Exception {
        var handler = mock(PictureEditHandler.class);
        var checker = mock(WsEditAccessChecker.class);
        var socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);
        when(socket.getAttributes()).thenReturn(Map.of());
        when(checker.check(any(), eq(1L))).thenThrow(new IllegalStateException("injected"))
                .thenReturn(WsEditAccessChecker.AccessCheckResult.ok(new com.yuwen.visionspace.model.entity.User()));
        var worker = new PictureEditEventWorkHandler();
        ReflectionTestUtils.setField(worker, "pictureEditHandler", handler);
        ReflectionTestUtils.setField(worker, "accessChecker", checker);
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(PictureEditEvent::new, 4,
                Thread.ofPlatform().factory(), ProducerType.MULTI, new BlockingWaitStrategy());
        disruptor.handleEventsWithWorkerPool(worker);
        disruptor.start();
        try {
            for (int i = 0; i < 2; i++) {
                long sequence = disruptor.getRingBuffer().next();
                var event = disruptor.getRingBuffer().get(sequence);
                var request = new PictureEditRequestMessage();
                request.setType("ENTER_EDIT");
                event.setPictureEditRequestMessage(request);
                event.setSession(socket);
                event.setPictureId(1L);
                disruptor.getRingBuffer().publish(sequence);
            }
            verify(handler, timeout(5000)).handleEnterEditMessage(any(), eq(socket), any(), eq(1L));
            verify(handler).noteProcessingFailure();
        } finally {
            disruptor.shutdown(5, TimeUnit.SECONDS);
        }
    }
}
