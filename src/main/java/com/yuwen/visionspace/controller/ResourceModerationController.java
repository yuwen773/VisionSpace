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
import com.yuwen.visionspace.utils.IdentifierUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        long resourceId = IdentifierUtils.parseId(valid.getResourceId(), "资源标识");
        User operator = userService.getLoginUser(httpRequest);
        requireTarget(valid.getResourceType(), resourceId);
        ResourceIsolation isolation = resourceIsolationService.quarantine(
                valid.getResourceType(), resourceId, operator, valid.getReason());
        return ResultUtils.success(toVOs(List.of(isolation)).get(0));
    }

    /**
     * 恢复正常资源。恢复不改变原有归属与成员关系，不扩大任何人的访问范围。
     */
    @PostMapping("/release")
    @AuthCheck(mustRole = UserConstant.MODERATOR_ROLE)
    public BaseResponse<ModerationResourceVO> release(@RequestBody ResourceModerationRequest request,
                                                      HttpServletRequest httpRequest) {
        ResourceModerationRequest valid = validate(request);
        long resourceId = IdentifierUtils.parseId(valid.getResourceId(), "资源标识");
        User operator = userService.getLoginUser(httpRequest);
        requireTarget(valid.getResourceType(), resourceId);
        ResourceIsolation isolation = resourceIsolationService.release(
                valid.getResourceType(), resourceId, operator, valid.getReason());
        return ResultUtils.success(toVOs(List.of(isolation)).get(0));
    }

    /**
     * 隔离中的资源列表。只含元数据与处置记录。
     */
    @GetMapping("/list")
    @AuthCheck(mustRole = UserConstant.MODERATOR_ROLE)
    public BaseResponse<List<ModerationResourceVO>> listQuarantined() {
        return ResultUtils.success(toVOs(resourceIsolationService.listQuarantined()));
    }

    /**
     * 单个资源的处置历史（含已恢复记录）。
     */
    @GetMapping("/history")
    @AuthCheck(mustRole = UserConstant.MODERATOR_ROLE)
    public BaseResponse<List<ModerationResourceVO>> history(@RequestParam("resourceType") String resourceType,
                                                            @RequestParam("resourceId") String resourceId) {
        ThrowUtils.throwIf(ResourceTypeEnum.getEnumByValue(resourceType) == null,
                ErrorCode.PARAMS_ERROR, "资源类型不支持");
        long id = IdentifierUtils.parseId(resourceId, "资源标识");
        return ResultUtils.success(toVOs(resourceIsolationService.history(resourceType, id)));
    }

    private ResourceModerationRequest validate(ResourceModerationRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(ResourceTypeEnum.getEnumByValue(request.getResourceType()) == null,
                ErrorCode.PARAMS_ERROR, "资源类型不支持");
        ThrowUtils.throwIf(request.getResourceId() == null || request.getResourceId().isBlank(),
                ErrorCode.PARAMS_ERROR, "缺少资源标识");
        ThrowUtils.throwIf(request.getReason() == null || request.getReason().isBlank(),
                ErrorCode.PARAMS_ERROR, "处置理由不能为空");
        return request;
    }

    /**
     * 目标必须真实存在，避免为不存在的对象留下可被误用的处置记录。
     */
    private void requireTarget(String resourceType, long resourceId) {
        if (ResourceTypeEnum.PICTURE.getValue().equals(resourceType)) {
            ThrowUtils.throwIf(pictureService.getById(resourceId) == null,
                    ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        } else if (ResourceTypeEnum.FEEDBACK_ATTACHMENT.getValue().equals(resourceType)) {
            ThrowUtils.throwIf(feedbackAttachmentService.getById(resourceId) == null,
                    ErrorCode.NOT_FOUND_ERROR, "附件不存在");
        }
    }

    /**
     * 批量转换：资源名称与操作者名称各查一次，避免逐行查询。
     */
    private List<ModerationResourceVO> toVOs(List<ResourceIsolation> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<Long, String> pictureNames = pictureNames(records);
        Map<Long, String> userNames = userNames(records);
        List<ModerationResourceVO> result = new ArrayList<>(records.size());
        for (ResourceIsolation record : records) {
            result.add(toVO(record, pictureNames, userNames));
        }
        return result;
    }

    /**
     * 只返回处置元数据；资源名称来自元数据字段，不含任何内容地址。
     */
    private ModerationResourceVO toVO(ResourceIsolation isolation,
                                      Map<Long, String> pictureNames,
                                      Map<Long, String> userNames) {
        ModerationResourceVO vo = new ModerationResourceVO();
        vo.setResourceType(isolation.getResourceType());
        vo.setResourceId(String.valueOf(isolation.getResourceId()));
        vo.setResourceName(resourceName(isolation.getResourceType(), isolation.getResourceId(), pictureNames));
        ResourceIsolationStatusEnum status =
                ResourceIsolationStatusEnum.getEnumByValue(isolation.getStatus());
        vo.setStatus(status == null ? "UNKNOWN" : status.name());
        vo.setOperatorId(asId(isolation.getOperatorId()));
        vo.setOperatorUserName(userNames.get(isolation.getOperatorId()));
        vo.setReason(isolation.getReason());
        vo.setCreateTime(isolation.getCreateTime());
        vo.setReleaseOperatorId(asId(isolation.getReleaseOperatorId()));
        vo.setReleaseOperatorUserName(userNames.get(isolation.getReleaseOperatorId()));
        vo.setReleaseReason(isolation.getReleaseReason());
        vo.setReleaseTime(isolation.getReleaseTime());
        return vo;
    }

    private String resourceName(String resourceType, Long resourceId, Map<Long, String> pictureNames) {
        if (ResourceTypeEnum.PICTURE.getValue().equals(resourceType)) {
            return pictureNames.get(resourceId);
        }
        // 附件没有对外名称：只给标识，不暴露对象键或地址
        return "反馈附件";
    }

    /**
     * 一次性取出这批记录涉及的图片名称，只取名称，不取任何内容地址。
     */
    private Map<Long, String> pictureNames(List<ResourceIsolation> records) {
        Set<Long> pictureIds = records.stream()
                .filter(record -> ResourceTypeEnum.PICTURE.getValue().equals(record.getResourceType()))
                .map(ResourceIsolation::getResourceId)
                .collect(Collectors.toSet());
        if (pictureIds.isEmpty()) {
            return Map.of();
        }
        return pictureService.listByIds(pictureIds).stream()
                .filter(picture -> picture.getName() != null)
                .collect(Collectors.toMap(Picture::getId, Picture::getName, (left, right) -> left));
    }

    /**
     * 一次性取出这批记录涉及的操作者与恢复者名称。
     */
    private Map<Long, String> userNames(List<ResourceIsolation> records) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (ResourceIsolation record : records) {
            if (record.getOperatorId() != null) {
                userIds.add(record.getOperatorId());
            }
            if (record.getReleaseOperatorId() != null) {
                userIds.add(record.getReleaseOperatorId());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(userIds).stream()
                .filter(user -> user.getUserName() != null)
                .collect(Collectors.toMap(User::getId, User::getUserName, (left, right) -> left));
    }

    /** 标识按十进制字符串给出，避免客户端丢失精度 */
    private String asId(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
