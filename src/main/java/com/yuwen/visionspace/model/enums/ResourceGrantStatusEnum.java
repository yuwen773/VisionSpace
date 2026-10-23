package com.yuwen.visionspace.model.enums;

import lombok.Getter;

/**
 * 单资源限时只读授权状态。
 *
 * <p>ACTIVE 时 activeKey 有值，撤销或到期后置空；唯一约束因此同时表达
 * "同一资源同一被授权人最多一条有效授权"。
 */
@Getter
public enum ResourceGrantStatusEnum {

    ACTIVE("有效", 1),
    REVOKED("已撤销", 2),
    EXPIRED("已到期", 3);

    private final String text;

    private final int value;

    ResourceGrantStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    public static ResourceGrantStatusEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (ResourceGrantStatusEnum item : values()) {
            if (item.value == value) {
                return item;
            }
        }
        return null;
    }
}
