package com.yuwen.visionspace.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResilientVectorStoreTest {

    /**
     * 只记录调用、返回固定结果的桩：这些用例关心的是门面的降级与重试，不是向量库本身。
     */
    private static class StubVectorStore implements VectorStore {

        final List<Document> added = new ArrayList<>();
        RuntimeException failure;

        @Override
        public void add(List<Document> documents) {
            if (failure != null) {
                throw failure;
            }
            added.addAll(documents);
        }

        @Override
        public void delete(List<String> idList) {
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            if (failure != null) {
                throw failure;
            }
            return List.of(new Document("hit"));
        }
    }

    private static SearchRequest anyRequest() {
        return SearchRequest.builder().query("test").topK(1).build();
    }

    @Test
    void constructionDoesNotConnect() {
        AtomicInteger calls = new AtomicInteger();
        ResilientVectorStore store = new ResilientVectorStore(() -> {
            calls.incrementAndGet();
            return new StubVectorStore();
        }, Duration.ofMillis(50));

        assertEquals(0, calls.get(), "构造期不得触发连接");
        assertFalse(store.isAvailable());
        assertFalse(store.wasAttempted(), "没被用过就不算尝试过");
    }

    @Test
    void searchReturnsEmptyWhenVectorStoreUnavailable() {
        ResilientVectorStore store = new ResilientVectorStore(
                () -> {
                    throw new IllegalStateException("connection refused");
                }, Duration.ofSeconds(30));

        assertTrue(store.similaritySearch(anyRequest()).isEmpty());
        assertFalse(store.isAvailable());
        assertTrue(store.wasAttempted());
        assertEquals("connection refused", store.lastError());
    }

    @Test
    void writesFailLoudlyWhenVectorStoreUnavailable() {
        ResilientVectorStore store = new ResilientVectorStore(
                () -> {
                    throw new IllegalStateException("connection refused");
                }, Duration.ofSeconds(30));

        // 写入不能静默丢弃：全量同步必须能看出「一条都没写进去」
        assertThrows(IllegalStateException.class, () -> store.add(List.of(new Document("doc"))));
        assertThrows(IllegalStateException.class, () -> store.delete(List.of("picture_1")));
    }

    @Test
    void retriesAfterBackoffAndRecovers() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        ResilientVectorStore store = new ResilientVectorStore(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("connection refused");
            }
            return new StubVectorStore();
        }, Duration.ofMillis(50));

        assertTrue(store.similaritySearch(anyRequest()).isEmpty(), "第一次连接失败，降级");
        assertFalse(store.isAvailable());

        Thread.sleep(120);
        assertEquals(1, store.similaritySearch(anyRequest()).size(), "退避窗口后重连成功");
        assertTrue(store.isAvailable());
        assertNull(store.lastError());
    }

    @Test
    void runtimeFailureDegradesAndReconnectsLater() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        StubVectorStore stub = new StubVectorStore();
        ResilientVectorStore store = new ResilientVectorStore(() -> {
            calls.incrementAndGet();
            return stub;
        }, Duration.ofMillis(50));

        assertEquals(1, store.similaritySearch(anyRequest()).size());
        assertTrue(store.isAvailable());

        // 运行期向量库挂掉：降级返回空，并标记不可用
        stub.failure = new IllegalStateException("chroma down");
        assertTrue(store.similaritySearch(anyRequest()).isEmpty());
        assertFalse(store.isAvailable());

        // 退避窗口后重新建连
        stub.failure = null;
        Thread.sleep(120);
        assertEquals(1, store.similaritySearch(anyRequest()).size());
        assertEquals(2, calls.get(), "恢复时必须重新走一次初始化");
    }

    @Test
    void addReachesDelegateWhenAvailable() {
        StubVectorStore stub = new StubVectorStore();
        ResilientVectorStore store = new ResilientVectorStore(() -> stub, Duration.ofMillis(50));

        store.add(List.of(new Document("doc")));

        assertEquals(1, stub.added.size());
        assertTrue(store.isAvailable());
    }
}
