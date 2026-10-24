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
import com.yuwen.visionspace.model.vo.ResourceGrantListVO;
import com.yuwen.visionspace.model.vo.ResourceGrantVO;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.ResourceAccessGrantService;
import com.yuwen.visionspace.service.UserService;
import com.yuwen.visionspace.utils.IdentifierUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 单资源限时只读授权。
 *
 * <p>个人资源由所有者授权，团队资源由团队管理员授权；平台管理员身份本身既不是授权主体，
 * 也不会因为管理角色获得读取权，但平台管理员独立持有的团队管理员身份照常生效。
 * 授权与撤销都记录操作者、范围、理由与结果。
 *
 * <p>标识按十进制字符串传输：雪花标识超出 JavaScript 的安全整数范围，
 * 客户端转成 number 会丢精度。
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
        Picture picture = requirePicture(IdentifierUtils.parseId(request.getPictureId(), "资源标识"));
        ResourceAccessGrant grant = resourceAccessGrantService.grant(picture, loginUser,
                IdentifierUtils.parseId(request.getGranteeUserId(), "被授权用户标识"),
                request.getExpiresTime(), request.getReason());
        // 响应只返回授权元数据，不返回图片内容或存储地址
        return ResultUtils.success(resourceAccessGrantService.toVO(grant, picture));
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
                IdentifierUtils.parseId(request.getGrantId(), "授权标识"), loginUser, request.getReason());
        Picture picture = requirePicture(grant.getResourceId());
        return ResultUtils.success(resourceAccessGrantService.toVO(grant, picture));
    }

    /**
     * 某资源的授权列表（含已撤销与到期）。资源所有者与团队管理员可见；
     * 平台管理员只用于只读审计。
     *
     * <p>响应同时给出当前查看者是否具备管理资格：授予与撤销的资格由服务端判定，
     * 客户端据此显示入口。
     */
    @GetMapping("/list")
    public BaseResponse<ResourceGrantListVO> listGrants(@RequestParam("pictureId") String pictureId,
                                                         HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Picture picture = requirePicture(IdentifierUtils.parseId(pictureId, "资源标识"));
        ResourceGrantListVO vo = new ResourceGrantListVO();
        vo.setGrants(resourceAccessGrantService.listByPicture(picture, loginUser));
        vo.setCanManage(resourceAccessGrantService.canGrant(picture, loginUser));
        return ResultUtils.success(vo);
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

}
