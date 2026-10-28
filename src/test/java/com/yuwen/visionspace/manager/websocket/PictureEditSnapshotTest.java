package com.yuwen.visionspace.manager.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.manager.websocket.lease.PictureEditLeaseManager;
import com.yuwen.visionspace.manager.websocket.monitor.PictureEditMonitor;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.UserVO;
import com.yuwen.visionspace.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PictureEditSnapshotTest {
    @Test
    void joiningSnapshotCannotOverwriteAConcurrentExit() throws Exception {
        var handler = new PictureEditHandler(30);
        var leases = mock(PictureEditLeaseManager.class);
        var users = mock(UserService.class);
        when(users.getUserVO(any())).thenReturn(new UserVO());
        ReflectionTestUtils.setField(handler, "leaseManager", leases);
        ReflectionTestUtils.setField(handler, "userService", users);
        ReflectionTestUtils.setField(handler, "monitor", new PictureEditMonitor());
        ReflectionTestUtils.setField(handler, "broadcastBus", mock(PictureEditBroadcastBus.class));
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("joining");
        when(socket.isOpen()).thenReturn(true);
        when(socket.getAttributes()).thenReturn(Map.of("pictureId", 1L, "user", new User()));
        var messages = Collections.synchronizedList(new ArrayList<String>());
        var exitSent = new CountDownLatch(1);
        var json = new ObjectMapper();
        doAnswer(invocation -> {
            String payload = invocation.getArgument(0, org.springframework.web.socket.TextMessage.class).getPayload();
            String type = json.readTree(payload).path("type").asText();
            messages.add(type);
            if (type.equals("EXIT_EDIT")) exitSent.countDown();
            return null;
        }).when(socket).sendMessage(any());
        var snapshotRead = new CountDownLatch(1);
        var continueSnapshot = new CountDownLatch(1);
        when(leases.currentLease(1L)).thenAnswer(invocation -> {
            snapshotRead.countDown();
            assertTrue(continueSnapshot.await(5, TimeUnit.SECONDS));
            return Optional.of(new PictureEditLeaseManager.LeaseHolder("old-holder", 101L));
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var joining = executor.submit(() -> { handler.afterConnectionEstablished(socket); return null; });
            assertTrue(snapshotRead.await(5, TimeUnit.SECONDS));
            var relayStarted = new CountDownLatch(1);
            var exit = executor.submit(() -> {
                relayStarted.countDown();
                return handler.relayBusMessage(1L, new PictureEditBroadcastBus.BusMessage("remote", 1L, null,
                        "{\"type\":\"EXIT_EDIT\",\"sessionId\":\"old-holder\"}"));
            });
            assertTrue(relayStarted.await(5, TimeUnit.SECONDS));
            // 锁外读快照的旧实现会先发送 EXIT，再发送旧 ENTER；锁内读则实时 EXIT 等待。
            exitSent.await(300, TimeUnit.MILLISECONDS);
            continueSnapshot.countDown();
            joining.get(5, TimeUnit.SECONDS);
            exit.get(5, TimeUnit.SECONDS);
            assertTrue(messages.contains("ENTER_EDIT"), messages.toString());
            assertTrue(messages.contains("EXIT_EDIT"), messages.toString());
            assertTrue(messages.indexOf("ENTER_EDIT") < messages.indexOf("EXIT_EDIT"), messages.toString());
        } finally {
            continueSnapshot.countDown();
        }
    }
}
