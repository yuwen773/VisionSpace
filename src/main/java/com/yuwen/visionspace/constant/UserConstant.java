package com.yuwen.visionspace.constant;

/**
 * 用户常量
 */
public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String VIP_ROLE = "vip";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    /**
     * 违规处置角色：隔离与恢复资源的独立权限，不等同于平台管理员
     */
    String MODERATOR_ROLE = "moderator";

    // endregion
}
