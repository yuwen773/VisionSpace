package com.yuwen.visionspace.manager.auth;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.mapper.SpaceMapper;
import com.yuwen.visionspace.mapper.SpaceUserMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.SpaceRoleEnum;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.service.UserService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * 团队协作、平台管理与私人归属的授权判定。
 *
 * <p>ADR-0001 要求三种身份分别判定，不能在各个调用点各写一份：
 * <ul>
 *   <li>团队空间：按当前数据库中的成员资格与角色判定，成员被移除或降权后下一次请求立即失效；</li>
 *   <li>平台管理员：保留平台管理与公共图库审核职责，但不因平台角色读取或操作私人内容；</li>
 *   <li>私人归属：个人资源与私有空间只属于所有者，授权只能由所有者或团队管理员作出。</li>
 * </ul>
 * 成员查询与平台角色判断只在这里出现；调用方只需提供已经加载的资源与查看者。
 */
@Component
public class SpaceAuthority {

    @Resource
    private UserService userService;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private SpaceUserMapper spaceUserMapper;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 查看者可以对这张图片执行哪些空间权限。
     *
     * <p>公开图库的个人资源只有作者获得写权限；其他登录用户只能查看。
     * 空间图片复用空间成员规则，平台管理员不因平台角色获得私人图片权限。
     *
     * @param picture 图片记录
     * @param viewer  当前查看者，匿名请求为 null
     * @return 权限码列表，无权限时为空列表
     */
    public List<String> picturePermissions(Picture picture, User viewer) {
        if (picture == null || viewer == null || viewer.getId() == null) {
            return List.of();
        }
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公开图库个人资源：作者可写；其他登录用户只读。
            // 平台管理员的审核处置走独立管理入口，不在这里获得写权限。
            return Objects.equals(picture.getUserId(), viewer.getId())
                    ? adminPermissions()
                    : List.of(SpaceUserPermissionConstant.PICTURE_VIEW);
        }
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) {
            return List.of();
        }
        return spacePermissions(space, viewer);
    }

    /**
     * 查看者在空间内的权限列表。
     *
     * <p>公共图库（space 为 null）是平台级资源：平台管理员拥有完整权限，
     * 其他登录用户可查看与上传。私人空间与团队空间按归属和成员关系判定。
     *
     * @param space  目标空间，公共图库为 null
     * @param viewer 当前查看者
     * @return 权限码列表
     */
    public List<String> spacePermissions(Space space, User viewer) {
        if (viewer == null || viewer.getId() == null) {
            return List.of();
        }
        if (space == null) {
            return userService.isAdmin(viewer)
                    ? adminPermissions()
                    : List.of(SpaceUserPermissionConstant.PICTURE_VIEW,
                              SpaceUserPermissionConstant.PICTURE_UPLOAD);
        }
        if (isOwner(space, viewer)) {
            return adminPermissions();
        }
        if (isPrivate(space)) {
            // 私有空间仅所有者；平台管理员不因平台角色读取私人内容
            return List.of();
        }
        SpaceUser membership = membership(space.getId(), viewer.getId());
        return membership == null
                ? List.of()
                : spaceUserAuthManager.getPermissionsByRole(membership.getSpaceRole());
    }

    /**
     * 查看者能否对这张图片执行指定操作。
     *
     * <p>这是图片写操作（编辑、删除、替换、扩图）的唯一判定入口。
     * 返回 false 时调用方按无权限拒绝，不在这里抛业务异常。
     *
     * @param picture    图片记录
     * @param viewer     当前查看者
     * @param permission 需要的权限码
     * @return 是否允许
     */
    public boolean canOperatePicture(Picture picture, User viewer, String permission) {
        if (picture == null || viewer == null || viewer.getId() == null || StrUtil.isBlank(permission)) {
            return false;
        }
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公开图库个人资源：只有作者可修改；平台管理员通过独立管理入口处置
            return Objects.equals(picture.getUserId(), viewer.getId());
        }
        Space space = spaceMapper.selectById(spaceId);
        return space != null && canOperateSpace(space, viewer, permission);
    }

    /**
     * 查看者能否在空间内执行指定操作。
     *
     * @param space      目标空间
     * @param viewer     当前查看者
     * @param permission 需要的权限码
     * @return 是否允许
     */
    public boolean canOperateSpace(Space space, User viewer, String permission) {
        if (space == null || viewer == null || viewer.getId() == null || StrUtil.isBlank(permission)) {
            return false;
        }
        if (isOwner(space, viewer)) {
            return true;
        }
        if (isPrivate(space)) {
            return false;
        }
        SpaceUser membership = membership(space.getId(), viewer.getId());
        return membership != null
                && spaceUserAuthManager.hasPermission(membership.getSpaceRole(), permission);
    }

    /**
     * 查看者能否读取某个空间内的图片（列表、检索与协作入口共用）。
     *
     * @param spaceId 空间标识
     * @param viewer  当前查看者
     * @return 是否允许
     */
    public boolean canViewSpacePictures(Long spaceId, User viewer) {
        if (spaceId == null) {
            return false;
        }
        Space space = spaceMapper.selectById(spaceId);
        return space != null
                && canOperateSpace(space, viewer, SpaceUserPermissionConstant.PICTURE_VIEW);
    }

    /**
     * 查看者能否管理空间成员：所有者，或该空间的团队管理员。
     *
     * <p>平台管理员不因平台角色自动获得他人空间的成员管理权限。
     */
    public boolean canManageMembers(Space space, User operator) {
        if (space == null || operator == null || operator.getId() == null) {
            return false;
        }
        if (isOwner(space, operator)) {
            return true;
        }
        if (isPrivate(space)) {
            return false;
        }
        SpaceUser membership = membership(space.getId(), operator.getId());
        return membership != null
                && SpaceRoleEnum.ADMIN.getValue().equals(membership.getSpaceRole());
    }

    /**
     * 查看者能否为这张图片授予或撤销资源查看授权。
     *
     * <p>授权主体是资源所有者或团队管理员；平台管理员身份本身不构成授权依据。
     */
    public boolean canGrantResource(Picture picture, User viewer) {
        if (picture == null || viewer == null || viewer.getId() == null) {
            return false;
        }
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            return Objects.equals(picture.getUserId(), viewer.getId());
        }
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) {
            return false;
        }
        if (isOwner(space, viewer)) {
            return true;
        }
        if (isPrivate(space)) {
            return false;
        }
        SpaceUser membership = membership(spaceId, viewer.getId());
        return membership != null
                && SpaceRoleEnum.ADMIN.getValue().equals(membership.getSpaceRole());
    }

    /**
     * 查看者能否编辑空间元数据。
     *
     * <p>空间元数据属于平台管理范围：所有者与平台管理员都可以编辑；
     * 这与私人内容的读取和写操作分开判定。
     */
    public boolean canEditSpaceMetadata(Space space, User viewer) {
        return space != null && viewer != null && viewer.getId() != null
                && (isOwner(space, viewer) || userService.isAdmin(viewer));
    }

    private boolean isOwner(Space space, User viewer) {
        return space.getUserId() != null && space.getUserId().equals(viewer.getId());
    }

    private boolean isPrivate(Space space) {
        return space.getSpaceType() == null
                || space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue();
    }

    private SpaceUser membership(Long spaceId, Long userId) {
        return spaceUserMapper.selectOne(new LambdaQueryWrapper<SpaceUser>()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, userId));
    }

    private List<String> adminPermissions() {
        return spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
    }
}
