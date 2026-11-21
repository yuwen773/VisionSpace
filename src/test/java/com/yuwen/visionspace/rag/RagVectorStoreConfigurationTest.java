package com.yuwen.visionspace.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 开启站内检索、但向量库不可达时，装配层必须照常完成，且不在启动期连库。
 *
 * <p>这条用例守的是一个真实发生过的回归：{@code ChromaVectorStore} 一旦被注册成
 * 普通 Bean，它的初始化会真的连库建集合，连不上就让整个上下文启动失败——可选功能
 * 变成启动期的硬依赖。基线 / 验收默认 {@code vectorstore.type=none}，覆盖不到这条
 * 路径，所以当时 723 个测试全绿也没拦住。</p>
 *
 * <p>这里只加载 RAG 这一层的装配，不起完整应用。用完整上下文（{@code @IsolatedSpringBootTest}）
 * 试过：同一个 surefire 运行里多出来的那份上下文会把后面的 WebSocket 用例拖垮——不加
 * {@code @DirtiesContext} 时洪泛用例撞上 60 秒预算超时，加了之后紧接着的用例拿到的是
 * 已关闭的上下文（Tomcat 还在收请求，Redis 已经销毁），18 个用例一起挂。完整应用的启动
 * 由容器门禁用真实 compose 栈验证，这里只保证「装配不连库」这条因果关系。</p>
 */
class RagVectorStoreConfigurationTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

    private ApplicationContextRunner runner(String vectorStoreType) {
        return new ApplicationContextRunner()
                .withUserConfiguration(RagVectorStoreConfiguration.class)
                .withBean(EmbeddingModel.class, () -> embeddingModel)
                .withPropertyValues(
                        "spring.ai.vectorstore.type=" + vectorStoreType,
                        // 必定拒绝连接的地址：只要装配期去连，就会立刻失败
                        "spring.ai.vectorstore.chroma.client.host=http://127.0.0.1",
                        "spring.ai.vectorstore.chroma.client.port=1",
                        "spring.ai.vectorstore.chroma.initialize-schema=true");
    }

    @Test
    void contextStartsWithoutConnectingToChroma() {
        runner("chroma").run(context -> {
            assertNull(context.getStartupFailure(), () -> String.valueOf(context.getStartupFailure()));
            ResilientVectorStore store = context.getBean(ResilientVectorStore.class);
            assertFalse(store.wasAttempted(), "启动期不得连接向量库");
            assertFalse(store.isAvailable());
        });
    }

    @Test
    void searchDegradesToEmptyWhenChromaIsUnreachable() {
        runner("chroma").run(context -> {
            ResilientVectorStore store = context.getBean(ResilientVectorStore.class);
            SearchRequest request = SearchRequest.builder().query("科技感蓝色背景").topK(5).build();

            // 真正用到时才连；连不上就降级为空结果，而不是把异常抛给调用方
            assertTrue(store.similaritySearch(request).isEmpty());
            assertTrue(store.wasAttempted());
            assertFalse(store.isAvailable());
        });
    }

    @Test
    void noVectorStoreBeanWhenRagIsDisabled() {
        runner("none").run(context -> {
            assertNull(context.getStartupFailure(), () -> String.valueOf(context.getStartupFailure()));
            assertFalse(context.containsBean("vectorStore"));
        });
    }
}
