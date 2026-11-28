package com.yuwen.visionspace.agent.manager;

import com.yuwen.visionspace.agent.mcp.McpCapabilityGuard;
import com.yuwen.visionspace.agent.mcp.McpServerAccess;
import com.yuwen.visionspace.manager.outbound.OutboundPolicy;
import com.yuwen.visionspace.model.dto.mcp.McpTool;
import com.yuwen.visionspace.model.entity.McpServer;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MCPDiscoveryLimitTest {
    @Test
    @Timeout(45)
    void blocksExcessDiscoveriesAndInterruptsTheWorkersOnTimeout() throws Exception {
        int capacity = 16;
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var enteredCache = new CountDownLatch(capacity);
        var interrupted = new CountDownLatch(capacity);
        var release = new CountDownLatch(1);
        when(values.get(anyString())).thenAnswer(call -> {
            enteredCache.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return null;
        });
        var outboundPolicy = mock(OutboundPolicy.class);
        var manager = new MCPManager(redis, mock(McpCapabilityGuard.class), outboundPolicy);
        try {
            var inFlight = new ArrayList<CompletableFuture<List<McpTool>>>();
            for (int index = 0; index < capacity; index++) {
                inFlight.add(manager.getToolsAsync(server(index)));
            }
            assertTrue(enteredCache.await(5, TimeUnit.SECONDS));
            assertEquals(List.of(), manager.getToolsAsync(server(capacity)).get(1, TimeUnit.SECONDS));
            for (var discovery : inFlight) {
                var failure = assertThrows(ExecutionException.class, () -> discovery.get(35, TimeUnit.SECONDS));
                assertInstanceOf(TimeoutException.class, failure.getCause());
            }
            assertTrue(interrupted.await(5, TimeUnit.SECONDS), "timeout must interrupt the running discovery");
            verify(values, times(capacity)).get(anyString());
            verifyNoInteractions(outboundPolicy);
        } finally {
            release.countDown();
            manager.shutdownDiscovery();
        }
    }

    @Test
    @Timeout(10)
    void cancelledDiscoveryClosesItsClientBeforeListingTools() throws Exception {
        var redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        var client = mock(McpSyncClient.class);
        var enteredInitialize = new CountDownLatch(1);
        var clientClosed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.initialize()).thenAnswer(call -> {
            enteredInitialize.countDown();
            try {
                release.await();
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        doAnswer(call -> {
            clientClosed.countDown();
            return null;
        }).when(client).close();
        var server = server(1L);
        var manager = spy(new MCPManager(redis, mock(McpCapabilityGuard.class), mock(OutboundPolicy.class)));
        doReturn(client).when(manager).getMcpSyncClient(server);
        try {
            var discovery = manager.getToolsAsync(server);
            assertTrue(enteredInitialize.await(5, TimeUnit.SECONDS));
            assertTrue(discovery.cancel(true));
            assertTrue(clientClosed.await(3, TimeUnit.SECONDS));
            verify(client, never()).listTools();
            verify(redis.opsForValue(), never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        } finally {
            release.countDown();
            manager.shutdownDiscovery();
        }
    }

    private static McpServer server(long userId) {
        var server = new McpServer();
        server.setUserId(userId);
        server.setMcpServerCode("fixture");
        server.setHost("https://example.invalid");
        server.setStatus(McpServerAccess.STATUS_ENABLED);
        return server;
    }
}
