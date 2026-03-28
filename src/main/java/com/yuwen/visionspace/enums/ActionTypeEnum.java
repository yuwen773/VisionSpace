package com.yuwen.visionspace.enums;

/**
 * 图片行为类型枚举
 */
public enum ActionTypeEnum {
    IMPRESSION(0, "曝光"),
    CLICK(1, "点击"),
    VIEW(2, "浏览"),
    LIKE(3, "点赞"),
    COLLECT(4, "收藏"),
    DOWNLOAD(5, "下载"),
    SHARE(6, "分享");

    private final int value;
    private final String label;

    ActionTypeEnum(int value, String label) {
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
    public static ActionTypeEnum fromValue(int value) {
        for (ActionTypeEnum type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }
}
