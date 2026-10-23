package com.yuwen.visionspace.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
public enum UserRoleEnum {

    USER("用户", "user"),
    VIP("会员", "vip"),
    ADMIN("管理员", "admin"),
    /**
     * 违规处置角色：仅具有隔离与恢复的独立权限。
     *
     * <p>它不继承平台管理员的其他职责，平台管理员也不自动获得处置权限；
     * 处置不授予读取私人内容的权利。
     */
    MODERATOR("内容处置员", "moderator");

    private final String text;

    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的 value
     * @return 枚举值
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
            if (userRoleEnum.value.equals(value)) {
                return userRoleEnum;
            }
        }
        return null;
    }
}
