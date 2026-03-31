package com.yuwen.visionspace.agent.model;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum ActionType {

    RETURN("return"),
    REGENERATE("regenerate"),
    RESEARCH("research");

    private final String value;

    ActionType(String value) {
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static ActionType getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ActionType anEnum : ActionType.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
