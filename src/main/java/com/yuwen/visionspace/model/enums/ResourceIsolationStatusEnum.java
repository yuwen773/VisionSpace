package com.yuwen.visionspace.model.enums;

import lombok.Getter;

/**
 * 违规资源隔离状态。
 *
 * <p>隔离只改变资源的可访问状态，不删除内容；解除后资源按原有归属与成员关系恢复，
 * 不额外扩大任何人的访问范围。
 */
@Getter
public enum ResourceIsolationStatusEnum {

    QUARANTINED("隔离中", 1),
    RELEASED("已恢复", 2);

    private final String text;

    private final int value;

    ResourceIsolationStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    public static ResourceIsolationStatusEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (ResourceIsolationStatusEnum item : values()) {
            if (item.value == value) {
                return item;
            }
        }
        return null;
    }
}
