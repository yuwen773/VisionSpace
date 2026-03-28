package com.yuwen.visionspace.enums;

/**
 * 图片行为来源枚举
 */
public enum ActionSourceEnum {
    HOME_RECOMMEND(0, "首页推荐"),
    DETAIL(1, "详情页"),
    SEARCH(2, "搜索页"),
    OTHER(3, "其他来源");

    private final int value;
    private final String label;

    ActionSourceEnum(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 根据value获取枚举
     */
    public static ActionSourceEnum fromValue(int value) {
        for (ActionSourceEnum source : values()) {
            if (source.value == value) {
                return source;
            }
        }
        return null;
    }
}
