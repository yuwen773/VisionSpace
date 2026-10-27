package com.yuwen.visionspace.model.enums;

import lombok.Getter;

/**
 * 可被单资源授权或隔离的资源类型。
 *
 * <p>只登记当前确实支持的资源；新增类型必须同时补齐读取入口的判定，
 * 不能只写入记录就当作已生效。
 */
@Getter
public enum ResourceTypeEnum {

    PICTURE("picture", "图片"),
    FEEDBACK_ATTACHMENT("feedback-attachment", "反馈附件");

    private final String value;

    private final String text;

    ResourceTypeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    public static ResourceTypeEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (ResourceTypeEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }
}
