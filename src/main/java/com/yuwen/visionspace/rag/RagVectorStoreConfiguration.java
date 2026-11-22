package com.yuwen.visionspace.rag;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaApiProperties;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 站内检索的向量库装配。
 *
 * <p>刻意不使用 Spring AI 的 {@code ChromaVectorStoreAutoConfiguration}
 * （已在 {@code application.yml} 里排除）：它把 {@code ChromaVectorStore} 直接注册成
 * Spring Bean，而该 Bean 的初始化会真的连库建集合，连不上就让整个应用启动失败。
 * 这里改为返回 {@link ResilientVectorStore}，把连接推迟到第一次真正使用时。</p>
 */
@Configuration
@ConditionalOnProperty(name = RagVectorStore.PROPERTY, havingValue = RagVectorStore.CHROMA)
@EnableConfigurationProperties({ChromaApiProperties.class, ChromaVectorStoreProperties.class})
public class RagVectorStoreConfiguration {

    /**
     * 初始化失败后的重试间隔。取值要大于一次连接超时，避免每个请求都撞一遍超时。
     */
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

    @Bean
    public ResilientVectorStore vectorStore(ChromaApiProperties apiProperties,
                                            ChromaVectorStoreProperties storeProperties,
                                            EmbeddingModel embeddingModel) {
        ChromaApi chromaApi = ChromaApi.builder()
                .baseUrl(apiProperties.getHost() + ":" + apiProperties.getPort())
                .build();

        return new ResilientVectorStore(() -> {
            // initializeImmediately=false 让构造期不连库；建集合这一步由这里显式触发，
            // 失败时异常落在 ResilientVectorStore 的退避重试里，而不是启动期。
            ChromaVectorStore store = ChromaVectorStore.builder(chromaApi, embeddingModel)
                    .tenantName(storeProperties.getTenantName())
                    .databaseName(storeProperties.getDatabaseName())
                    .collectionName(storeProperties.getCollectionName())
                    .initializeSchema(storeProperties.isInitializeSchema())
                    .initializeImmediately(false)
                    .build();
            try {
                store.afterPropertiesSet();
            } catch (Exception e) {
                throw new IllegalStateException("Chroma 初始化失败: " + e.getMessage(), e);
            }
            return store;
        }, RETRY_INTERVAL);
    }
}
