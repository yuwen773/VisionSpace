package com.yuwen.visionspace.model.enums;

import lombok.Getter;

/**
 * 会话附件绑定的状态。
 *
 * <p>BOUND 是「这个引用有效」：图片保留，附件内容可读。
 * RELEASED 是「这个引用已解除」：会话被删除或图片被明确删除后，绑定不再授予
 * 任何访问权；记录本身保留，用于说明引用曾经存在。
 */
@Getter
public enum AgentAttachmentStatusEnum {

    BOUND("已绑定", 0),
    RELEASED("已解除", 1);

    private final String text;

    private final int value;

    AgentAttachmentStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    public static AgentAttachmentStatusEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (AgentAttachmentStatusEnum item : values()) {
            if (item.value == value) {
                return item;
            }
        }
        return null;
    }
}
