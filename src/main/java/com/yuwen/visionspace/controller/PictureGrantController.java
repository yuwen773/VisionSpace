package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.model.dto.picture.PictureGrantCreateRequest;
import com.yuwen.visionspace.model.dto.picture.PictureGrantRevokeRequest;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.ResourceAccessGrant;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.ResourceGrantStatusEnum;
import com.yuwen.visionspace.model.vo.ResourceGrantVO;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.ResourceAccessGrantService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 单资源限时只读授权。
 *
 * <p>个人资源由所有者授权，团队资源由团队管理员授权；平台管理员身份既不是授权主体，
 * 也不会因为管理角色获得读取权。授权与撤销都记录操作者、范围、理由与结果。
 */
@Slf4j
@RestController
@RequestMapping("/picture/grant")
public class PictureGrantController {

    @Resource
    private ResourceAccessGrantService resourceAccessGrantService;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    /**
     * 授予限时只读访问。
     */
    @PostMapping("/add")
    public BaseResponse<ResourceGrantVO> createGrant(@RequestBody PictureGrantCreateRequest request,
                                                    HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        Picture picture = requirePicture(request.getPictureId());
        ResourceAccessGrant grant = resourceAccessGrantService.grant(picture, loginUser,
                request.getGranteeUserId(), request.getExpiresTime(), request.getReason());
        // 响应只返回授权元数据，不返回图片内容或存储地址
        return ResultUtils.success(toVO(grant, picture));
    }

    /**
     * 撤销授权。
     */
    @PostMapping("/revoke")
    public BaseResponse<ResourceGrantVO> revokeGrant(@RequestBody PictureGrantRevokeRequest request,
                                                    HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null || request.getGrantId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        ResourceAccessGrant grant = resourceAccessGrantService.revoke(
                request.getGrantId(), loginUser, request.getReason());
        Picture picture = requirePicture(grant.getResourceId());
        return ResultUtils.success(toVO(grant, picture));
    }

    /**
     * 某资源的授权列表（含已撤销与到期）。资源所有者与团队管理员可见；
     * 平台管理员只用于只读审计。
     */
    @GetMapping("/list")
    public BaseResponse<List<ResourceGrantVO>> listGrants(@RequestParam("pictureId") Long pictureId,
                                                         HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Picture picture = requirePicture(pictureId);
        return ResultUtils.success(resourceAccessGrantService.listByPicture(picture, loginUser));
    }

    /**
     * 当前用户收到的有效授权。
     */
    @GetMapping("/mine")
    public BaseResponse<List<ResourceGrantVO>> listMyGrants(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(resourceAccessGrantService.listActiveByGrantee(loginUser.getId()));
    }

    private Picture requirePicture(Long pictureId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "缺少资源标识");
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        return picture;
    }

    private ResourceGrantVO toVO(ResourceAccessGrant grant, Picture picture) {
        ResourceGrantVO vo = new ResourceGrantVO();
        vo.setId(grant.getId());
        vo.setResourceType(grant.getResourceType());
        vo.setResourceId(grant.getResourceId());
        vo.setResourceName(picture == null ? null : picture.getName());
        vo.setGranteeUserId(grant.getGranteeUserId());
        vo.setGranteeUserName(displayName(grant.getGranteeUserId()));
        vo.setGrantedByUserId(grant.getGrantedByUserId());
        vo.setGrantedByUserName(displayName(grant.getGrantedByUserId()));
        vo.setReason(grant.getReason());
        vo.setExpiresTime(grant.getExpiresTime());
        ResourceGrantStatusEnum status = ResourceGrantStatusEnum.getEnumByValue(grant.getStatus());
        vo.setStatus(status == null ? null : status.name());
        vo.setRevokedByUserId(grant.getRevokedByUserId());
        vo.setRevokeReason(grant.getRevokeReason());
        vo.setRevokeTime(grant.getRevokeTime());
        vo.setCreateTime(grant.getCreateTime());
        return vo;
    }

    /** 只返回名称，不返回账号、头像、角色等资料 */
    private String displayName(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userService.getById(userId);
        return user == null ? null : user.getUserName();
    }
}
