package com.yuwen.visionspace.agent.model;

import cn.hutool.core.util.ObjUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
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
     * 反序列化同时接受 {@code regenerate} 与 {@code REGENERATE}。
     *
     * <p>默认的 Jackson 绑定只认枚举名，而对外文档和 {@link #getValue()} 用的是小写，
     * 手写客户端按文档传小写会直接 400。两种都收下来，序列化仍按枚举名，
     * 已发布的接口契约不变。
     */
    @JsonCreator
    public static ActionType fromJson(String raw) {
        if (ObjUtil.isEmpty(raw)) {
            return null;
        }
        for (ActionType anEnum : ActionType.values()) {
            if (anEnum.value.equalsIgnoreCase(raw) || anEnum.name().equalsIgnoreCase(raw)) {
                return anEnum;
            }
        }
        throw new IllegalArgumentException("未知的反馈动作: " + raw);
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
