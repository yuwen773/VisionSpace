package com.yuwen.visionspace.manager.auth;

import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.SpaceUserService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 图片实际内容（原图、缩略图、预览、下载）的读取授权。
 *
 * <p>每次新请求都按当前数据库状态判定，不缓存判定结果：
 * <ul>
 *   <li>已审核的公共图库图片：允许匿名读取；</li>
 *   <li>未审核的公共图库图片：仅作者与平台管理员；</li>
 *   <li>空间图片：空间所有者、当前团队成员（viewer 及以上）与平台管理员；</li>
 *   <li>团队管理员或平台管理员身份都不扩展到其他用户的私人资源。</li>
 * </ul>
 * 成员被移除或降权后，下一次请求即按新的成员关系判定。
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
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            return canReadPublicPicture(picture, viewer);
        }
        return canReadSpacePicture(picture, spaceId, viewer);
    }

    private boolean canReadPublicPicture(Picture picture, User viewer) {
        if (isApproved(picture)) {
            // 公开图库的已审核内容允许匿名读取
            return true;
        }
        if (viewer == null) {
            return false;
        }
        if (userService.isAdmin(viewer)) {
            return true;
        }
        return picture.getUserId() != null && picture.getUserId().equals(viewer.getId());
    }

    private boolean canReadSpacePicture(Picture picture, Long spaceId, User viewer) {
        if (viewer == null) {
            return false;
        }
        // 平台管理员保留违规处置能力（独立管理入口由后续工单负责），
        // 但普通浏览仍按成员关系判定；这里与既有空间接口保持一致。
        if (userService.isAdmin(viewer)) {
            return true;
        }
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            return false;
        }
        if (space.getUserId() != null && space.getUserId().equals(viewer.getId())) {
            return true;
        }
        if (space.getSpaceType() == null || space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间只对所有者开放
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
