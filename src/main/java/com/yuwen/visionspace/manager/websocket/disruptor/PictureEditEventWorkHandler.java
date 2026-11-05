package com.yuwen.visionspace.manager.websocket.disruptor;

import com.lmax.disruptor.WorkHandler;
import com.yuwen.visionspace.manager.websocket.PictureEditHandler;
import com.yuwen.visionspace.manager.websocket.WsEditAccessChecker;
import com.yuwen.visionspace.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yuwen.visionspace.manager.websocket.model.PictureEditRequestMessage;
import com.yuwen.visionspace.manager.websocket.model.PictureEditResponseMessage;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.Resource;
import java.io.IOException;

/**
 * 图片编辑事件处理器（消费者）。
 *
 * <p>处理前逐条复核访问（会话存活 + 当前权限），撤权、注销、降权
 * 后的连接即使仍在线也不能继续写入。单条消息的任何异常只影响该条：
 * 记入监控并回送错误反馈，消费线程继续处理后续消息。
 */
@Component
@Slf4j
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private WsEditAccessChecker accessChecker;

    @Resource
    private UserService userService;

    @Override
    public void onEvent(PictureEditEvent pictureEditEvent) {
        try {
            PictureEditRequestMessage pictureEditRequestMessage = pictureEditEvent.getPictureEditRequestMessage();
            WebSocketSession session = pictureEditEvent.getSession();
            Long pictureId = pictureEditEvent.getPictureId();
            // 队列中滞留的事件可能对应已关闭的连接：直接丢弃
            if (session == null || !session.isOpen()) {
                return;
            }
            // 消息类型缺失或未知：隔离为非法消息，回送错误，不中断消费
            String type = pictureEditRequestMessage == null ? null : pictureEditRequestMessage.getType();
            PictureEditMessageTypeEnum messageType = PictureEditMessageTypeEnum.getEnumByValue(type);
            if (messageType == null) {
                pictureEditHandler.noteInvalidMessage();
                sendError(session, "消息类型错误");
                return;
            }
            // INFO/ERROR 是服务端下行的类型，客户端不应上行：按非法消息隔离
            if (messageType == PictureEditMessageTypeEnum.INFO || messageType == PictureEditMessageTypeEnum.ERROR) {
                pictureEditHandler.noteInvalidMessage();
                sendError(session, "消息类型错误");
                return;
            }
            // 逐条复核访问：注销（HTTP 会话失效）、降权、移除成员立即生效
            var check = accessChecker.check(session.getAttributes()::get, pictureId);
            if (!check.allowed()) {
                pictureEditHandler.noteAuthRejection();
                // 复核拒绝时收回该连接持有的租约：否则续期任务会给
                // 已无权限的连接一直续租，其他用户永远接管不了
                pictureEditHandler.revokeLeaseOf(pictureId, session.getId());
                sendError(session, check.reason() == null ? "没有操作权限" : check.reason(), true);
                return;
            }
            switch (messageType) {
                case ENTER_EDIT -> pictureEditHandler.handleEnterEditMessage(pictureEditRequestMessage, session, check.user(), pictureId);
                case EXIT_EDIT -> pictureEditHandler.handleExitEditMessage(pictureEditRequestMessage, session, check.user(), pictureId);
                case EDIT_ACTION -> pictureEditHandler.handleEditActionMessage(pictureEditRequestMessage, session, check.user(), pictureId);
                default -> sendError(session, "消息类型错误");
            }
        } catch (Exception e) {
            // 单次处理异常被隔离：记入监控，消费线程继续处理后续合法消息
            pictureEditHandler.noteProcessingFailure();
            log.error("处理协作消息失败", e);
        } finally {
            // 环形槽位长期复用，处理后不保留已关闭连接和消息正文。
            pictureEditEvent.setPictureEditRequestMessage(null);
            pictureEditEvent.setSession(null);
            pictureEditEvent.setUser(null);
            pictureEditEvent.setPictureId(null);
        }
    }

    private void sendError(WebSocketSession session, String text) {
        sendError(session, text, false);
    }

    private void sendError(WebSocketSession session, String text, boolean leaseLost) {
        if (!session.isOpen()) {
            return;
        }
        try {
            PictureEditResponseMessage response = new PictureEditResponseMessage();
            response.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            response.setMessage(text);
            response.setLeaseLost(leaseLost);
            response.setUser(userService.getUserVO((User) session.getAttributes().get("user")));
            synchronized (session) {
                session.sendMessage(new TextMessage(cn.hutool.json.JSONUtil.toJsonStr(response)));
            }
        } catch (IOException e) {
            log.warn("错误反馈发送失败: sessionId={}", session.getId());
        }
    }
}
