package com.yuwen.visionspace.manager.websocket;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yuwen.visionspace.manager.websocket.disruptor.PictureEditEventProducer;
import com.yuwen.visionspace.manager.websocket.lease.PictureEditLeaseManager;
import com.yuwen.visionspace.manager.websocket.model.PictureEditActionEnum;
import com.yuwen.visionspace.manager.websocket.model.PictureEditRequestMessage;
import com.yuwen.visionspace.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yuwen.visionspace.manager.websocket.model.PictureEditResponseMessage;
import com.yuwen.visionspace.manager.websocket.monitor.PictureEditMonitor;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片编辑 WebSocket 处理器。
 *
 * <p>编辑权由跨实例共享的租约决定（{@link PictureEditLeaseManager}），
 * 租约绑定到具体连接：同一用户的另一个标签页关闭不会误释放；
 * 过期接管后，旧连接的迟到消息与迟到断开事件既不能继续写入，
 * 也不能释放新持有者的租约。每条编辑消息经过
 * {@link WsEditAccessChecker} 复核会话与当前权限，授权不再只看握手。
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    /** 默认租约期限：客户端按此节奏续期，服务端超时未续即视为失效。 */
    private static final Duration DEFAULT_LEASE_TTL = Duration.ofSeconds(30);

    private final Duration leaseTtl;

    public PictureEditHandler(@org.springframework.beans.factory.annotation.Value("${vision-space.websocket.lease-ttl-seconds:30}") long leaseTtlSeconds) {
        this.leaseTtl = leaseTtlSeconds > 0 ? Duration.ofSeconds(leaseTtlSeconds) : DEFAULT_LEASE_TTL;
    }

    /**
     * 续期任务读取的期限（静态暴露给续期 Job）。
     */
    public Duration leaseTtl() {
        return leaseTtl;
    }

    /**
     * 遍历本实例上仍打开并持有租约的连接，供续期兜底任务使用。
     */
    public void forEachActiveLease(java.util.function.BiConsumer<Long, String> action) {
        pictureSessions.forEach((pictureId, sessions) -> {
            String holder = leaseManager.holderSessionId(pictureId).orElse(null);
            if (holder == null) {
                return;
            }
            for (WebSocketSession session : sessions) {
                if (session.isOpen() && session.getId().equals(holder)) {
                    action.accept(pictureId, holder);
                    break;
                }
            }
        });
    }

    private final ObjectMapper objectMapper;

    {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
    }

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    @Resource
    private PictureEditLeaseManager leaseManager;

    @Resource
    private PictureEditMonitor monitor;

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    /**
     * 连接建立成功
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);
        // 构造响应，发送加入编辑的消息通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 广播给所有用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 收到前端发送的消息，转发到 Disruptor 队列（背压由生产者控制）。
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 获取消息内容，将 JSON 转换为 PictureEditRequestMessage
        PictureEditRequestMessage pictureEditRequestMessage;
        try {
            pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        } catch (Exception e) {
            // 损坏消息只影响当前连接的错误反馈，不进队列、不中断其他消息
            monitor.onInvalidMessage();
            sendError(session, "消息格式错误");
            return;
        }
        // 从 Session 属性中获取到公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 生产消息到 Disruptor 环形队列（满时拒绝并反馈，不阻塞 IO 线程）
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    /**
     * 进入编辑状态（Disruptor 消费线程调用，已经过访问复核）
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        PictureEditLeaseManager.LeaseAcquireResult result =
                leaseManager.tryAcquire(pictureId, session.getId(), user.getId(), leaseTtl);
        if (!result.acquired()) {
            monitor.onLeaseConflict();
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            pictureEditResponseMessage.setMessage("其他用户正在编辑");
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pictureEditResponseMessage)));
            return;
        }
        // 构造响应，发送进入编辑的消息通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
        String message = String.format("用户 %s 开始编辑图片", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 广播给所有用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 处理编辑操作（Disruptor 消费线程调用，已经过访问复核）
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            log.warn("无效的编辑动作: {}", editAction);
            monitor.onInvalidMessage();
            sendError(session, "无效的编辑动作");
            return;
        }
        // 租约归属验证：只有当前持有者（同一条连接）能执行操作
        var holder = leaseManager.holderSessionId(pictureId);
        if (holder.isEmpty() || !holder.get().equals(session.getId())) {
            monitor.onLeaseConflict();
            sendError(session, "当前不持有编辑权");
            return;
        }
        // 构造响应，发送具体操作的通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
        String message = String.format("%s 执行 %s", user.getUserName(), actionEnum.getText());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setEditAction(editAction);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
        broadcastToPicture(pictureId, pictureEditResponseMessage, session);
    }


    /**
     * 退出编辑状态（Disruptor 消费线程调用，已经过访问复核）
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 归属验证：只有租约持有连接能退出，旧连接的迟到 EXIT 不影响新租约
        if (!leaseManager.release(pictureId, session.getId())) {
            return;
        }
        // 构造响应，发送退出编辑的消息通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
        String message = String.format("用户 %s 退出编辑图片", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 关闭连接：只移除当前连接、只释放当前连接持有的租约。
     * 同用户其他标签页不受影响。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 从 Session 属性中获取到公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 只按连接释放租约（归属验证在租约管理器内）
        try {
            if (leaseManager.release(pictureId, session.getId())) {
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
                String message = String.format("用户 %s 退出编辑图片", user.getUserName());
                pictureEditResponseMessage.setMessage(message);
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                broadcastToPicture(pictureId, pictureEditResponseMessage);
            }
        } catch (Exception e) {
            // 关闭路径上的广播失败不阻断会话清理
            monitor.onBroadcastFailure();
            log.warn("关闭连接时广播退出编辑失败: pictureId={}", pictureId, e);
        }
        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }
        // 通知其他用户，该用户已经离开编辑
        try {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
            String message = String.format("用户 %s 离开编辑", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        } catch (Exception e) {
            monitor.onBroadcastFailure();
            log.warn("关闭连接时广播离开信息失败: pictureId={}", pictureId, e);
        }
    }

    /**
     * 广播给该图片的所有用户（支持排除掉某个 Session）
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (cn.hutool.core.collection.CollUtil.isEmpty(sessionSet)) {
            return;
        }
        // 序列化为 JSON 字符串
        String message;
        try {
            message = objectMapper.writeValueAsString(pictureEditResponseMessage);
        } catch (Exception e) {
            monitor.onProcessingFailure();
            log.error("广播消息序列化失败", e);
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        int sent = 0;
        for (WebSocketSession session : sessionSet) {
            // 排除掉的 session 不发送
            if (excludeSession != null && session.equals(excludeSession)) {
                continue;
            }
            if (!session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(textMessage);
                }
                sent++;
            } catch (Exception e) {
                // 单个接收端失败只影响它自己，不中断其他接收端
                monitor.onBroadcastFailure();
                log.warn("广播到单个会话失败: sessionId={}", session.getId(), e);
            }
        }
        if (sent > 0) {
            monitor.onBroadcastSent(sent);
        }
    }

    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    private void sendError(WebSocketSession session, String text) {
        if (!session.isOpen()) {
            return;
        }
        try {
            PictureEditResponseMessage error = new PictureEditResponseMessage();
            error.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            error.setMessage(text);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
            }
        } catch (Exception e) {
            log.warn("发送错误消息失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 监控桥接：消费端把非法消息、复核拒绝、处理失败计入监控。
     */
    public void noteInvalidMessage() {
        monitor.onInvalidMessage();
    }

    /**
     * 收回指定连接持有的租约（复核拒绝时调用，防止续期任务给无权连接续租）。
     */
    public void revokeLeaseOf(Long pictureId, String sessionId) {
        if (pictureId != null) {
            leaseManager.forceRevoke(pictureId, sessionId);
        }
    }

    public void noteAuthRejection() {
        monitor.onAuthRejection();
    }

    public void noteProcessingFailure() {
        monitor.onProcessingFailure();
    }
}
