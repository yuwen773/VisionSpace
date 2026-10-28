package com.yuwen.visionspace.manager.websocket.model;

import com.yuwen.visionspace.model.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片编辑响应消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditResponseMessage {

    /**
     * 消息类型，例如 "INFO", "ERROR", "ENTER_EDIT", "EXIT_EDIT", "EDIT_ACTION"
     */
    private String type;

    /**
     * 信息
     */
    private String message;

    /**
     * 执行的编辑动作
     */
    private String editAction;

    /**
     * 用户信息
     */
    private UserVO user;

    /** 事件所属连接，编辑权不得只按用户判断。 */
    private String sessionId;

    /** 仅握手完成后的定向 INFO 携带本连接标识，不经总线广播。 */
    private String selfSessionId;

    /** 仅确认租约已失效时为 true；可重试的消息错误不改变编辑权。 */
    private boolean leaseLost;
}
