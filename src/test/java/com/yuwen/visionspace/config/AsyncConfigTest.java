package com.yuwen.visionspace.config;

import com.yuwen.visionspace.service.impl.PicturePreviewServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncConfigTest {

    @Test
    void picturePreviewExecutorShouldUseExplicitThreadPoolSettings() {
        AsyncConfig asyncConfig = new AsyncConfig();

        Executor executor = asyncConfig.picturePreviewExecutor();

        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor threadPoolTaskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(4, threadPoolTaskExecutor.getCorePoolSize());
        assertEquals(8, threadPoolTaskExecutor.getMaxPoolSize());
        assertEquals(200, threadPoolTaskExecutor.getThreadPoolExecutor().getQueue().remainingCapacity());
        assertEquals("picture-preview-", threadPoolTaskExecutor.getThreadNamePrefix());
    }

    @Test
    void generateAndUpdatePreviewShouldBindToPicturePreviewExecutor() throws NoSuchMethodException {
        Method method = PicturePreviewServiceImpl.class
                .getMethod("generateAndUpdatePreview", Long.class, String.class);

        Async async = method.getAnnotation(Async.class);

        assertNotNull(async);
        assertEquals("picturePreviewExecutor", async.value());
    }
}
