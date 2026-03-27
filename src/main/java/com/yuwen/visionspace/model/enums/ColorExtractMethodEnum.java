package com.yuwen.visionspace.model.enums;

/**
 * 颜色提取方法枚举
 */
public enum ColorExtractMethodEnum {

    MEDIAN_CUT("median-cut"),

    KMEANS("kmeans");

    private final String value;

    ColorExtractMethodEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ColorExtractMethodEnum fromValue(String value) {
        if (value == null) {
            return MEDIAN_CUT;
        }
        for (ColorExtractMethodEnum method : values()) {
            if (method.value.equalsIgnoreCase(value)) {
                return method;
            }
        }
        return MEDIAN_CUT;
    }
}
