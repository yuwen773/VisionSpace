package com.yuwen.visionspace.utils.strategy;

/**
 * 颜色提取方法枚举
 */
public enum ColorExtractMethod {

    MEDIAN_CUT("median-cut"),

    KMEANS("kmeans");

    private final String value;

    ColorExtractMethod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ColorExtractMethod fromValue(String value) {
        if (value == null) {
            return MEDIAN_CUT;
        }
        for (ColorExtractMethod method : values()) {
            if (method.value.equalsIgnoreCase(value)) {
                return method;
            }
        }
        return MEDIAN_CUT;
    }
}
