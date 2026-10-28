package com.yuwen.visionspace.manager.websocket;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.yuwen.visionspace.manager.websocket.disruptor.PictureEditEvent;
import com.yuwen.visionspace.manager.websocket.disruptor.PictureEditEventProducer;
import com.yuwen.visionspace.manager.websocket.disruptor.PictureEditEventWorkHandler;
import com.yuwen.visionspace.manager.websocket.model.PictureEditRequestMessage;
import com.yuwen.visionspace.manager.websocket.monitor.PictureEditMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollaborationBackpressureTest {
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
