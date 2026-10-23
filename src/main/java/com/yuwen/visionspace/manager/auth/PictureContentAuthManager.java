package com.yuwen.visionspace.manager.auth;

import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.service.ResourceAccessGrantService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.SpaceUserService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 图片实际内容（原图、缩略图、预览、下载）的读取授权。
 *
 * <p>每次新请求都按当前数据库状态判定，不缓存判定结果：
 * <ul>
 *   <li>隔离中的资源：除隔离操作者与违规处置人员外一律拒绝，不因公开状态或所有权放行；</li>
 *   <li>已审核的公共图库图片：允许匿名读取；</li>
 *   <li>未审核的公共图库图片：作者与具有审核职责的平台管理员；</li>
 *   <li>私有空间：空间所有者，以及资源所有者授予的限时只读访问；</li>
 *   <li>团队空间：空间所有者与当前团队成员（viewer 及以上），以及团队管理员授予的限时只读访问；</li>
 *   <li>平台管理员身份不扩展到私人内容，也不能作为授权主体。</li>
 * </ul>
 * 成员被移除、降权、授权到期或撤销后，下一次请求即按新的状态判定。
 */
@Slf4j
@Component
public class PictureContentAuthManager {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private ResourceIsolationService resourceIsolationService;

    @Lazy
    @Resource
    private ResourceAccessGrantService resourceAccessGrantService;

    /**
     * 判断当前查看者是否可以读取该图片的内容。
     *
     * @param picture 图片记录
     * @param viewer  当前登录用户，匿名请求为 null
     * @return 是否允许读取
     */
    public boolean canReadContent(Picture picture, User viewer) {
        if (picture == null) {
            return false;
        }
        // 隔离优先于一切：隔离后公开与私人访问都按隔离规则拒绝
        if (isQuarantined(picture)) {
            return canAccessQuarantinedContent(viewer);
        }
        if (canReadByOwnership(picture, viewer)) {
            return true;
        }
        // 资源方授予的限时只读访问：每次读取都查当前状态，到期或撤销立即失效
        return viewer != null && viewer.getId() != null
                && picture.getId() != null
                && resourceAccessGrantService.hasActiveGrant(picture.getId(), viewer.getId());
    }

    /**
     * 图片当前是否处于隔离状态。
     */
    public boolean isQuarantined(Picture picture) {
        return picture != null
                && resourceIsolationService.isQuarantined(ResourceTypeEnum.PICTURE.getValue(), picture.getId());
    }

    /**
     * 按归属、公开状态与成员关系判定，不考虑临时授权。
     */
    private boolean canReadByOwnership(Picture picture, User viewer) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            return canReadPublicPicture(picture, viewer);
        }
        return canReadSpacePicture(picture, spaceId, viewer);
    }

    /**
     * 隔离中的内容只对具有独立处置权限的人员开放，用于复核隔离是否恰当；
     * 平台管理员、公开状态与所有权都不构成例外。
     */
    private boolean canAccessQuarantinedContent(User viewer) {
        return viewer != null && userService.isModerator(viewer);
    }

    private boolean canReadPublicPicture(Picture picture, User viewer) {
        if (isApproved(picture)) {
            // 公开图库的已审核内容允许匿名读取
            return true;
        }
        if (viewer == null) {
            return false;
        }
        if (picture.getUserId() != null && picture.getUserId().equals(viewer.getId())) {
            return true;
        }
        // 审核职责需要查看待审内容：平台管理员保留这一职责，但仅限公共图库的审核场景
        return userService.isAdmin(viewer);
    }

    private boolean canReadSpacePicture(Picture picture, Long spaceId, User viewer) {
        if (viewer == null) {
            return false;
        }
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            return false;
        }
        if (space.getUserId() != null && space.getUserId().equals(viewer.getId())) {
            return true;
        }
        if (space.getSpaceType() == null || space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间仅所有者；平台管理员不因平台角色读取私人内容
            return false;
        }
        // 团队空间按成员关系判定：平台管理员不因平台角色自动获得成员权限，
        // 与 StpInterfaceImpl 的团队空间分支保持一致
        if (userService.isAdmin(viewer)) {
            return false;
        }
        SpaceUser spaceUser = spaceUserService.lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, viewer.getId())
                .one();
        if (spaceUser == null) {
            return false;
        }
        return spaceUserAuthManager.hasPermission(spaceUser.getSpaceRole(),
                SpaceUserPermissionConstant.PICTURE_VIEW);
    }

    private boolean isApproved(Picture picture) {
        return picture.getReviewStatus() != null
                && picture.getReviewStatus() == PictureReviewStatusEnum.PASS.getValue();
    }
}
