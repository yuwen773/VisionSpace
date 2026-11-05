package com.yuwen.visionspace.manager.websocket.disruptor;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.yuwen.visionspace.manager.websocket.monitor.PictureEditMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

/**
 * 图片编辑事件 Disruptor 配置。
 *
 * <p>显式配置异常处理器：默认的 FatalExceptionHandler 会在第一次未捕获
 * 异常后终止消费线程，单条坏消息不能再拖垮整个协作通道。
 */
@Slf4j
@Configuration
public class PictureEditEventDisruptorConfig {

    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;

    @Resource
    private PictureEditMonitor pictureEditMonitor;

    @Bean("pictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> messageModelRingBuffer() {
        // 定义 ringBuffer 的大小（有界背压：满时生产端拒绝而不是阻塞）
        int bufferSize = 1024 * 256;
        // 创建 disruptor
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                PictureEditEvent::new,
                bufferSize,
                ThreadFactoryBuilder.create().setNamePrefix("pictureEditEventDisruptor").build()
        );
        // 单条事件异常兜底：WorkHandler 内部已捕获，这里防御未知路径
        disruptor.setDefaultExceptionHandler(new ExceptionHandler<>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, PictureEditEvent event) {
                pictureEditMonitor.onProcessingFailure();
                log.error("协作事件处理异常 sequence={}", sequence, ex);
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                log.error("协作事件消费者启动失败", ex);
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                log.error("协作事件消费者停机异常", ex);
            }
        });
        // 设置消费者
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);
        // 启动 disruptor
        disruptor.start();
        return disruptor;
    }
}
