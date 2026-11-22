package com.yuwen.visionspace.manager.websocket;

import com.yuwen.visionspace.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yuwen.visionspace.manager.websocket.model.PictureEditResponseMessage;
import com.yuwen.visionspace.model.vo.UserVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协作消息的措辞与字段只有这一份。
 */
class PictureEditMessagesTest {

    @Test
    void welcomeCarriesTheSelfSession() {
        PictureEditResponseMessage message = PictureEditMessages.welcome("session-1");

        assertEquals(PictureEditMessageTypeEnum.INFO.getValue(), message.getType());
        assertEquals(PictureEditMessages.CONNECTED, message.getMessage());
        assertEquals("session-1", message.getSelfSessionId());
    }

    @Test
    void joinAndLeaveShareTheUserNameSentence() {
        assertEquals("用户 小明 加入编辑", PictureEditMessages.joined("小明"));
        assertEquals("用户 小明 离开编辑", PictureEditMessages.left("小明"));
    }

    @Test
    void enteredAndExitedEditingCarrySessionAndOwner() {
        UserVO user = new UserVO();
        user.setUserName("小明");

        PictureEditResponseMessage entered = PictureEditMessages.enteredEditing("s-1", "小明", user);
        assertEquals(PictureEditMessageTypeEnum.ENTER_EDIT.getValue(), entered.getType());
        assertEquals("s-1", entered.getSessionId());
        assertEquals("用户 小明 开始编辑图片", entered.getMessage());
        assertEquals(user, entered.getUser());

        PictureEditResponseMessage exited = PictureEditMessages.exitedEditing("s-1", "小明", user);
        assertEquals(PictureEditMessageTypeEnum.EXIT_EDIT.getValue(), exited.getType());
        assertEquals("用户 小明 退出编辑图片", exited.getMessage());
    }

    @Test
    void editActionCarriesTheActionCode() {
        PictureEditResponseMessage action = PictureEditMessages.editAction(
                "s-1", "小明", "放大", "ZOOM_IN", null);

        assertEquals(PictureEditMessageTypeEnum.EDIT_ACTION.getValue(), action.getType());
        assertEquals("小明 执行 放大", action.getMessage());
        assertEquals("ZOOM_IN", action.getEditAction());
    }

    @Test
    void errorsAndRevocationAreDistinct() {
        PictureEditResponseMessage error = PictureEditMessages.error("其他用户正在编辑", true);
        assertEquals(PictureEditMessageTypeEnum.ERROR.getValue(), error.getType());
        assertTrue(error.isLeaseLost());

        PictureEditResponseMessage revoked = PictureEditMessages.leaseRevoked("s-1");
        assertEquals(PictureEditMessageTypeEnum.EXIT_EDIT.getValue(), revoked.getType());
        assertEquals(PictureEditMessages.LEASE_REVOKED, revoked.getMessage());
        assertFalse(revoked.isLeaseLost());
    }
}
