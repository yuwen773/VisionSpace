package com.yuwen.visionspace.manager.websocket;

import com.yuwen.visionspace.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yuwen.visionspace.manager.websocket.model.PictureEditResponseMessage;
import com.yuwen.visionspace.model.vo.UserVO;

/**
 * 协作编辑消息的唯一构造点。
 *
 * <p>连接、进入编辑、执行动作、退出、断开五条路径此前各自 `new` 一个消息对象、
 * 各自拼同一句中文、各自决定要不要带会话 id 与所有者。措辞与会话标识要一起改
 * 的时候，得记得改五个地方；漏一处就是同一件事在不同路径上说法不同。
 *
 * <p>生命周期与租约粘合仍留在 {@link PictureEditHandler}：这里只管「这条消息长什么样」。
 */
final class PictureEditMessages {

    static final String CONNECTED = "协作连接已建立";
    static final String EDITING_TOO = "其他用户正在编辑";
    static final String HOLDER_EXISTS = "已有连接正在编辑";
    static final String LEASE_REVOKED = "编辑权已失效";

    private PictureEditMessages() {
    }

    /** 连接建立后的自我介绍。 */
    static PictureEditResponseMessage welcome(String selfSessionId) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.INFO.getValue());
        message.setMessage(CONNECTED);
        message.setSelfSessionId(selfSessionId);
        return message;
    }

    /** 加入 / 离开一类的提示。 */
    static PictureEditResponseMessage info(String text, UserVO user) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.INFO.getValue());
        message.setMessage(text);
        message.setUser(user);
        return message;
    }

    static String joined(String userName) {
        return String.format("用户 %s 加入编辑", userName);
    }

    static String left(String userName) {
        return String.format("用户 %s 离开编辑", userName);
    }

    /** 新连接需要知道已有编辑者，否则界面会误显示可进入编辑。 */
    static PictureEditResponseMessage holder(String holderSessionId, UserVO holderUser) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
        message.setSessionId(holderSessionId);
        message.setMessage(HOLDER_EXISTS);
        message.setUser(holderUser);
        return message;
    }

    /** 抢编辑权失败。 */
    static PictureEditResponseMessage editingToo(UserVO user) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.ERROR.getValue());
        message.setMessage(EDITING_TOO);
        message.setUser(user);
        return message;
    }

    /** 编辑权凭据：只发给认领成功的那条连接。 */
    static PictureEditResponseMessage leaseCredential(String leaseToken) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.INFO.getValue());
        message.setLeaseToken(leaseToken);
        return message;
    }

    static PictureEditResponseMessage enteredEditing(String sessionId, String userName, UserVO user) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
        message.setSessionId(sessionId);
        message.setMessage(String.format("用户 %s 开始编辑图片", userName));
        message.setUser(user);
        return message;
    }

    static PictureEditResponseMessage editAction(String sessionId, String userName,
                                                 String actionText, String editAction, UserVO user) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
        message.setSessionId(sessionId);
        message.setMessage(String.format("%s 执行 %s", userName, actionText));
        message.setEditAction(editAction);
        message.setUser(user);
        return message;
    }

    static PictureEditResponseMessage exitedEditing(String sessionId, String userName, UserVO user) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
        message.setSessionId(sessionId);
        message.setMessage(String.format("用户 %s 退出编辑图片", userName));
        message.setUser(user);
        return message;
    }

    /** 复核拒绝后收回编辑权。 */
    static PictureEditResponseMessage leaseRevoked(String sessionId) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
        message.setSessionId(sessionId);
        message.setMessage(LEASE_REVOKED);
        return message;
    }

    static PictureEditResponseMessage error(String text, boolean leaseLost) {
        PictureEditResponseMessage message = new PictureEditResponseMessage();
        message.setType(PictureEditMessageTypeEnum.ERROR.getValue());
        message.setMessage(text);
        message.setLeaseLost(leaseLost);
        return message;
    }
}
