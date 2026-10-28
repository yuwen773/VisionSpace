package com.yuwen.visionspace.manager.websocket.disruptor;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.yuwen.visionspace.manager.websocket.model.PictureEditRequestMessage;
import com.yuwen.visionspace.manager.websocket.monitor.PictureEditMonitor;
import com.yuwen.visionspace.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;

/**
 * 图片编辑事件生产者。
 *
 * <p>环形队列大小有界。队列满时不再阻塞 IO 线程（旧实现 ringBuffer.next()
 * 会无限等待），而是拒绝该条消息并向客户端回送错误——明确的背压与拒绝策略：
 * 编辑协作消息允许丢弃重试，不允许把所有连接的 IO 线程拖死。
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    @Resource
    private PictureEditMonitor monitor;

    /**
     * 发布事件；队列满时拒绝并反馈，返回是否入队成功。
     */
    public boolean publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        long next;
        try {
            // tryNext：队列满立即抛出，而不是阻塞等待
            next = ringBuffer.tryNext();
        } catch (InsufficientCapacityException e) {
            monitor.onRejectedByBackpressure();
            reject(session, "协作消息过多，请稍后重试");
            return false;
        }
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        ringBuffer.publish(next);
        return true;
    }

    private void reject(WebSocketSession session, String text) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"" + text + "\"}"));
            }
        } catch (Exception e) {
            log.warn("背压拒绝消息发送失败: sessionId={}", session.getId());
        }
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void destroy() {
        pictureEditEventDisruptor.shutdown();
    }
}
