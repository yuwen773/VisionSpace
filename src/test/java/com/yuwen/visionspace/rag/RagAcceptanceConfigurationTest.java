package com.yuwen.visionspace.rag;

import com.yuwen.visionspace.acceptance.StubEmbeddingConfiguration;
import com.yuwen.visionspace.agent.tools.InternalImageSearchTool;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.service.ResourceIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * RAG 验收配置：启用后装配真实 Bean、健康信号如实反映、检索路径降级而不是报错。
 *
 * <p>配置来自 {@code src/test/resources/rag-acceptance.yml}，与开发默认的
 * {@code vectorstore.type=none} 分开：只有显式加载这一份时才装配站内检索。
 *
 * <p>这里只加载 RAG 这一层，不起完整应用。完整上下文（{@code @IsolatedSpringBootTest}）
 * 试过：同一个 surefire 运行里多出来的那份上下文会把后面的 WebSocket 用例拖垮——
 * 不加 {@code @DirtiesContext} 时洪泛用例撞上 60 秒预算超时，加了之后紧接着的用例
 * 拿到的是已关闭的上下文。完整应用的启动由容器门禁用真实 compose 栈验证。
 */
class RagAcceptanceConfigurationTest {

    /**
     * RAG 验收配置下的装配：真实 Bean 存在，启动期不连库。
     */
    @Test
    void ragAcceptanceConfigAssemblesTheRealBeansWithoutConnectingAtStartup() {
        ragRunner().run(context -> {
            assertNull(context.getStartupFailure(), () -> String.valueOf(context.getStartupFailure()));
            assertTrue(context.containsBean("vectorStore"), "向量库 Bean 应当装配");
            assertTrue(context.getBean("vectorStore") instanceof ResilientVectorStore,
                    "装配的是项目自己的懒连接门面，不是 Spring AI 的默认实现");
            ResilientVectorStore store = context.getBean(ResilientVectorStore.class);
            assertFalse(store.wasAttempted(), "启动期不得连接向量库");
            assertFalse(store.isAvailable());
            assertEquals(1, context.getBeanNamesForType(InternalImageSearchTool.class).length,
                    "检索工具应当装配");
            assertEquals(1, context.getBeanNamesForType(RagHealthIndicator.class).length,
                    "健康信号应当装配");
        });
    }

    /**
     * 检索路径：向量库不可达时降级为空结果，不抛异常，也不谎报成功。
     */
    @Test
    void searchDegradesToEmptyResultWhenTheVectorStoreIsUnreachable() {
        ragRunner().run(context -> {
            InternalImageSearchTool tool = context.getBean(InternalImageSearchTool.class);

            List<com.yuwen.visionspace.agent.model.ImageResource> results =
                    tool.searchInternalImages("科技感蓝色背景图", 5);

            assertTrue(results.isEmpty(), "向量库不可达时返回空结果");
            ResilientVectorStore store = context.getBean(ResilientVectorStore.class);
            assertTrue(store.wasAttempted(), "真正用到时才尝试连接");
            assertFalse(store.isAvailable());
        });
    }

    /**
     * 健康信号：没连过时是「未知」，连过并失败后是「故障」，两种情况都要如实报告。
     */
    @Test
    void healthReportsUnknownBeforeUseAndDownAfterAFailedAttempt() {
        ragRunner().run(context -> {
            RagHealthIndicator indicator = context.getBean(RagHealthIndicator.class);

            Health before = indicator.health();
            assertEquals("UNKNOWN", before.getStatus().getCode(), "还没连过不等于坏了");

            context.getBean(InternalImageSearchTool.class).searchInternalImages("蓝色", 3);

            Health after = indicator.health();
            assertEquals("DOWN", after.getStatus().getCode(), "连过并失败后必须报告故障");
            assertNotNull(after.getDetails().get("reason"), "故障原因要能查");
        });
    }

    /**
     * 门控本身：默认配置下不装配站内检索，RAG 验收配置才装配。
     */
    @Test
    void ragIsNotAssembledWithoutTheAcceptanceConfig() {
        runner("spring.ai.vectorstore.type=none").run(context -> {
            assertFalse(context.containsBean("vectorStore"), "默认不装配向量库");
            assertEquals(0, context.getBeanNamesForType(InternalImageSearchTool.class).length,
                    "默认不装配检索工具");
            assertEquals(0, context.getBeanNamesForType(RagHealthIndicator.class).length,
                    "默认不装配健康信号");
        });
    }

    private ApplicationContextRunner ragRunner() {
        return runner("spring.config.location=classpath:/rag-acceptance.yml");
    }

    private ApplicationContextRunner runner(String... properties) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(properties)
                .withUserConfiguration(RagVectorStoreConfiguration.class, PublicGalleryIndexReader.class,
                        InternalImageSearchTool.class, RagHealthIndicator.class, StubEmbeddingConfiguration.class)
                .withBean(PictureMapper.class, () -> {
                    PictureMapper mapper = mock(PictureMapper.class);
                    doReturn(List.of()).when(mapper).selectBatchIds(anyCollection());
                    return mapper;
                })
                .withBean(ResourceIsolationService.class, () -> {
                    ResourceIsolationService service = mock(ResourceIsolationService.class);
                    doReturn(Set.of()).when(service).quarantinedIds(anyString(), anyCollection());
                    return service;
                });
    }
}
