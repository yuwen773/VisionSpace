package com.yuwen.visionspace.model.enums;

import lombok.Getter;

/**
 * 资源记录状态。
 *
 * <p>TEMP 是尚未绑定图片/会话的临时资源；ACTIVE 表示当前有效；
 * DELETED 表示已请求删除且对象删除结果明确；SUPERSEDED 表示被重新上传替换。
 */
@Getter
public enum PictureFileStatusEnum {

    TEMP("临时", 0),
    ACTIVE("有效", 1),
    DELETED("已删除", 2),
    SUPERSEDED("已替换", 3);

    private final String text;

    private final int value;

    PictureFileStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    public static PictureFileStatusEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (PictureFileStatusEnum item : values()) {
            if (item.value == value) {
                return item;
            }
        }
        return null;
    }
}
