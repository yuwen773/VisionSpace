package com.yuwen.visionspace.agent.controller;

import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.agent.service.AgentImageUploadService;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.AgentImageVO;
import com.yuwen.visionspace.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Agent 临时图片上传与删除。
 *
 * <p>上传返回资源标识，客户端后续用它完成删除；按地址删除仅为旧客户端兼容，
 * 且只在地址精确匹配本人已有记录时生效。
 */
@RestController
@RequestMapping("/agent/image")
public class AgentImageUploadController {

    @Resource
    private AgentImageUploadService agentImageUploadService;

    @Resource
    private UserService userService;

    @PostMapping("/upload")
    public BaseResponse<AgentImageVO> upload(@RequestPart("file") MultipartFile file,
                                             HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureFile record = agentImageUploadService.uploadImage(file, loginUser);
        return ResultUtils.success(AgentImageVO.from(record));
    }

    /**
     * 按资源标识删除。
     */
    @PostMapping("/upload/delete")
    public BaseResponse<Boolean> delete(@RequestBody Map<String, Object> body,
                                        HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Object resourceId = body == null ? null : body.get("resourceId");
        Object url = body == null ? null : body.get("url");
        if (resourceId != null) {
            return ResultUtils.success(agentImageUploadService.deleteImage(toLong(resourceId), loginUser));
        }
        // 旧客户端只提交 url：必须与本人已有记录精确匹配
        ThrowUtils.throwIf(url == null || StrUtil.isBlank(String.valueOf(url)),
                ErrorCode.PARAMS_ERROR, "缺少资源标识");
        return ResultUtils.success(
                agentImageUploadService.deleteImageByExactUrl(String.valueOf(url), loginUser));
    }

    private Long toLong(Object value) {
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
