package com.yuwen.visionspace.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yuwen.visionspace.manager.auth.model.SpaceUserAuthConfig;
import com.yuwen.visionspace.manager.auth.model.SpaceUserRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 空间角色权限配置。
 *
 * <p>只负责“某个空间角色拥有哪些权限码”这一件事。成员资格、平台角色与
 * 资源归属的判定在 {@link SpaceAuthority}，两者通过权限码衔接。
 */
@Component
public class SpaceUserAuthManager {

    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;

    static {
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    /**
     * 根据角色获取权限列表
     *
     * @param spaceUserRole 空间角色
     * @return 权限码列表，角色为空或不存在时为空列表
     */
    public List<String> getPermissionsByRole(String spaceUserRole) {
        if (StrUtil.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles()
                .stream()
                .filter(r -> r.getKey().equals(spaceUserRole))
                .findFirst()
                .orElse(null);
        if (role == null) {
            return new ArrayList<>();
        }
        return role.getPermissions();
    }

    /**
     * 判断某个空间角色是否具备指定权限。
     *
     * @param spaceUserRole 空间角色，可为空
     * @param permission    权限码
     * @return 是否具备该权限
     */
    public boolean hasPermission(String spaceUserRole, String permission) {
        return StrUtil.isNotBlank(permission) && getPermissionsByRole(spaceUserRole).contains(permission);
    }
}
