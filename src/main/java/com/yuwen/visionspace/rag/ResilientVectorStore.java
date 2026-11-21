package com.yuwen.visionspace.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * 容错的向量库门面。
 *
 * <p>{@code ChromaVectorStore} 的初始化会真的连库建集合，失败即抛异常。把它直接注册成
 * Spring Bean，一次连不上就会让整个应用启动失败——可选功能变成启动期的硬依赖。
 * 这里把它包成懒初始化：启动期不连库，第一次真正用到时才连；连不上就降级并在退避
 * 窗口后重试，向量库恢复后自动恢复。</p>
 *
 * <p>降级语义刻意分成两类：写入（{@code add}/{@code delete}）抛异常，让调用方看到
 * 「没写进去」，避免全量同步谎报成功；检索返回空列表，站内检索本来就是尽力而为。</p>
 */
@Slf4j
public class ResilientVectorStore implements VectorStore {

    private final Supplier<VectorStore> factory;
    private final long retryIntervalMs;

    private volatile VectorStore delegate;
    private volatile long nextAttemptAt;
    private volatile String lastError;
    private volatile boolean attempted;

    public ResilientVectorStore(Supplier<VectorStore> factory, Duration retryInterval) {
        this.factory = factory;
        this.retryIntervalMs = retryInterval.toMillis();
    }

    /**
     * 当前是否已成功连上向量库。只读状态，不会触发初始化，供健康检查使用。
     */
    public boolean isAvailable() {
        return delegate != null;
    }

    /**
     * 是否尝试过初始化。没尝试过时健康状态是「未知」而不是「故障」。
     */
    public boolean wasAttempted() {
        return attempted;
    }

    public String lastError() {
        return lastError;
    }

    /**
     * 取当前可用的委托；不可用时返回 null。初始化失败后按退避间隔重试，
     * 不在每次调用上都去撞一次连接超时。
     */
    private VectorStore delegate() {
        VectorStore current = delegate;
        if (current != null) {
            return current;
        }
        if (System.currentTimeMillis() < nextAttemptAt) {
            return null;
        }
        synchronized (this) {
            if (delegate != null) {
                return delegate;
            }
            if (System.currentTimeMillis() < nextAttemptAt) {
                return null;
            }
            attempted = true;
            try {
                VectorStore created = factory.get();
                delegate = created;
                lastError = null;
                log.info("向量库已就绪");
                return created;
            } catch (Exception e) {
                lastError = e.getMessage();
                nextAttemptAt = System.currentTimeMillis() + retryIntervalMs;
                log.warn("向量库不可用，{} 毫秒后重试: {}", retryIntervalMs, e.toString());
                return null;
            }
        }
    }

    /**
     * 运行期调用失败说明向量库已经不可用（进程重启、网络中断等），
     * 丢弃当前委托并重新进入退避，等下一次调用再重建。
     */
    private void invalidate(Throwable cause) {
        synchronized (this) {
            delegate = null;
            lastError = cause.getMessage();
            nextAttemptAt = System.currentTimeMillis() + retryIntervalMs;
        }
        log.warn("向量库调用失败，已降级，{} 毫秒后重试: {}", retryIntervalMs, cause.toString());
    }

    private VectorStore requireAvailable() {
        VectorStore store = delegate();
        if (store == null) {
            throw new IllegalStateException("向量库不可用: " + lastError);
        }
        return store;
    }

    @Override
    public void add(List<Document> documents) {
        VectorStore store = requireAvailable();
        try {
            store.add(documents);
        } catch (RuntimeException e) {
            invalidate(e);
            throw e;
        }
    }

    @Override
    public void delete(List<String> idList) {
        VectorStore store = requireAvailable();
        try {
            store.delete(idList);
        } catch (RuntimeException e) {
            invalidate(e);
            throw e;
        }
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        VectorStore store = requireAvailable();
        try {
            store.delete(filterExpression);
        } catch (RuntimeException e) {
            invalidate(e);
            throw e;
        }
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        VectorStore store = delegate();
        if (store == null) {
            return List.of();
        }
        try {
            return store.similaritySearch(request);
        } catch (RuntimeException e) {
            invalidate(e);
            return List.of();
        }
    }
}
