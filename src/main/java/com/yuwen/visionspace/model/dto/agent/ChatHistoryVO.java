package com.yuwen.visionspace.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

/**
 * 聊天历史 VO
 */
@Data
@AllArgsConstructor
public class ChatHistoryVO {
    /** 消息列表 */
    private List<MessageVO> messages;
    /** 是否有更多 */
    private boolean hasMore;
    /** 下次查询的游标 */
    private Long nextCursor;
}