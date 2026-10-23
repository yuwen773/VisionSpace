package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.model.dto.picture.ResourceModerationRequest;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.ResourceIsolation;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.ResourceIsolationStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.model.vo.ModerationResourceVO;
import com.yuwen.visionspace.service.FeedbackAttachmentService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 违规资源隔离与恢复。
 *
 * <p>使用独立于平台管理员的处置角色：目标、操作者与理由都必须可核对。
 * 后台只展示处置所需元数据，不返回原图、缩略图、预览或存储定位信息；
 * 查看内容仍须资源方授权。隔离默认保留内容与引用，可恢复。
 */
@Slf4j
@RestController
@RequestMapping("/moderation")
public class ResourceModerationController {

    @Resource
    private ResourceIsolationService resourceIsolationService;

    @Resource
    private PictureService pictureService;

    @Resource
    private FeedbackAttachmentService feedbackAttachmentService;

    @Resource
    private UserService userService;

    /**
     * 隔离资源。
     */
    @PostMapping("/quarantine")
    @AuthCheck(mustRole = UserConstant.MODERATOR_ROLE)
    public BaseResponse<ModerationResourceVO> quarantine(@RequestBody ResourceModerationRequest request,
                                                         HttpServletRequest httpRequest) {
        ResourceModerationRequest valid = validate(request);
        User operator = userService.getLoginUser(httpRequest);
        requireTarget(valid);
        ResourceIsolation isolation = resourceIsolationService.quarantine(
                valid.getResourceType(), valid.getResourceId(), operator, valid.getReason());
        return ResultUtils.success(toVO(isolation, valid.getResourceType()));
    }

    /**
     * 恢复正常资源。恢复不改变原有归属与成员关系，不扩大任何人的访问范围。
     */
    @PostMapping("/release")
    @AuthCheck(mustRole = UserConstant.MODERATOR_ROLE)
    public BaseResponse<ModerationResourceVO> release(@RequestBody ResourceModerationRequest request,
                                                      HttpServletRequest httpRequest) {
        ResourceModerationRequest valid = validate(request);
        User operator = userService.getLoginUser(httpRequest);
        requireTarget(valid);
        ResourceIsolation isolation = resourceIsolationService.release(
                valid.getResourceType(), valid.getResourceId(), operator, valid.getReason());
        return ResultUtils.success(toVO(isolation, valid.getResourceType()));
    }

    /**
     * 隔离中的资源列表。只含元数据与处置记录。
     */
    @GetMapping("/list")
    @AuthCheck(mustRole = UserConstant.MODERATOR_ROLE)
    public BaseResponse<List<ModerationResourceVO>> listQuarantined() {
        List<ResourceIsolation> records = resourceIsolationService.lambdaQuery()
                .eq(ResourceIsolation::getStatus, ResourceIsolationStatusEnum.QUARANTINED.getValue())
                .orderByDesc(ResourceIsolation::getId)
                .list();
        List<ModerationResourceVO> result = new ArrayList<>();
        for (ResourceIsolation record : records) {
            result.add(toVO(record, record.getResourceType()));
        }
        return ResultUtils.success(result);
    }

    /**
     * 单个资源的处置历史（含已恢复记录）。
     */
    @GetMapping("/history")
    @AuthCheck(mustRole = UserConstant.MODERATOR_ROLE)
    public BaseResponse<List<ModerationResourceVO>> history(@RequestParam("resourceType") String resourceType,
                                                            @RequestParam("resourceId") Long resourceId) {
        ThrowUtils.throwIf(ResourceTypeEnum.getEnumByValue(resourceType) == null,
                ErrorCode.PARAMS_ERROR, "资源类型不支持");
        ThrowUtils.throwIf(resourceId == null || resourceId <= 0, ErrorCode.PARAMS_ERROR, "缺少资源标识");
        List<ModerationResourceVO> result = new ArrayList<>();
        for (ResourceIsolation record : resourceIsolationService.history(resourceType, resourceId)) {
            result.add(toVO(record, resourceType));
        }
        return ResultUtils.success(result);
    }

    private ResourceModerationRequest validate(ResourceModerationRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(ResourceTypeEnum.getEnumByValue(request.getResourceType()) == null,
                ErrorCode.PARAMS_ERROR, "资源类型不支持");
        ThrowUtils.throwIf(request.getResourceId() == null || request.getResourceId() <= 0,
                ErrorCode.PARAMS_ERROR, "缺少资源标识");
        ThrowUtils.throwIf(request.getReason() == null || request.getReason().isBlank(),
                ErrorCode.PARAMS_ERROR, "处置理由不能为空");
        return request;
    }

    /**
     * 目标必须真实存在，避免为不存在的对象留下可被误用的处置记录。
     */
    private void requireTarget(ResourceModerationRequest request) {
        if (ResourceTypeEnum.PICTURE.getValue().equals(request.getResourceType())) {
            Picture picture = pictureService.getById(request.getResourceId());
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        } else if (ResourceTypeEnum.FEEDBACK_ATTACHMENT.getValue().equals(request.getResourceType())) {
            ThrowUtils.throwIf(feedbackAttachmentService.getById(request.getResourceId()) == null,
                    ErrorCode.NOT_FOUND_ERROR, "附件不存在");
        }
    }

    /**
     * 只返回处置元数据；资源名称来自元数据字段，不含任何内容地址。
     */
    private ModerationResourceVO toVO(ResourceIsolation isolation, String resourceType) {
        ModerationResourceVO vo = new ModerationResourceVO();
        vo.setResourceType(isolation.getResourceType());
        vo.setResourceId(isolation.getResourceId());
        vo.setResourceName(resourceName(isolation.getResourceType(), isolation.getResourceId()));
        ResourceIsolationStatusEnum status =
                ResourceIsolationStatusEnum.getEnumByValue(isolation.getStatus());
        vo.setStatus(status == null ? "UNKNOWN" : status.name());
        vo.setOperatorId(isolation.getOperatorId());
        vo.setOperatorUserName(displayName(isolation.getOperatorId()));
        vo.setReason(isolation.getReason());
        vo.setCreateTime(isolation.getCreateTime());
        vo.setReleaseOperatorId(isolation.getReleaseOperatorId());
        vo.setReleaseOperatorUserName(displayName(isolation.getReleaseOperatorId()));
        vo.setReleaseReason(isolation.getReleaseReason());
        vo.setReleaseTime(isolation.getReleaseTime());
        return vo;
    }

    private String resourceName(String resourceType, Long resourceId) {
        if (ResourceTypeEnum.PICTURE.getValue().equals(resourceType)) {
            Picture picture = pictureService.getById(resourceId);
            return picture == null ? null : picture.getName();
        }
        // 附件没有对外名称：只给标识，不暴露对象键或地址
        return "反馈附件";
    }

    private String displayName(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userService.getById(userId);
        return user == null ? null : user.getUserName();
    }
}
