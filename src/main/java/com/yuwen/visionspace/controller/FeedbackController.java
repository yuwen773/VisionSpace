package com.yuwen.visionspace.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.model.dto.feedback.FeedbackAddRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackQueryRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackUpdateStatusRequest;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.FeedbackVO;
import com.yuwen.visionspace.service.FeedbackService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    @Resource
    private FeedbackService feedbackService;

    @Resource
    private UserService userService;

    @Resource
    private FileStorageService fileStorageService;

    /**
     * 提交反馈
     */
    @PostMapping("/add")
    public BaseResponse<Long> addFeedback(@RequestBody FeedbackAddRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        long result = feedbackService.addFeedback(request, loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 根据ID获取反馈详情
     */
    @GetMapping("/get/{id}")
    public BaseResponse<FeedbackVO> getFeedbackById(@PathVariable("id") Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        FeedbackVO vo = feedbackService.getFeedbackById(id);
        return ResultUtils.success(vo);
    }

    /**
     * 分页查询反馈列表（仅管理员）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<FeedbackVO>> listFeedbackByPage(@RequestBody FeedbackQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<FeedbackVO> result = feedbackService.listFeedbackByPage(queryRequest);
        return ResultUtils.success(result);
    }

    /**
     * 更新反馈状态（仅管理员）
     */
    @PostMapping("/update/status")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateFeedbackStatus(@RequestBody FeedbackUpdateStatusRequest request,
                                                       HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        boolean result = feedbackService.updateFeedbackStatus(request, loginUser.getId());
        return ResultUtils.success(result);
    }

    /** 文件大小限制 2MB */
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    /** 允许的图片格式 */
    private static final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "png", "jpg", "webp");

    /** 反馈附件存储路径前缀 */
    private static final String FEEDBACK_PATH_PREFIX = "/temp/";

    @PostMapping("/upload")
    public BaseResponse<String> uploadFeedbackAttachment(@RequestParam("file") MultipartFile file,
                                                             HttpServletRequest request) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 文件大小校验
        ThrowUtils.throwIf(file.getSize() > MAX_FILE_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过2MB");

        // 文件格式校验
        String extension = FileUtil.getSuffix(file.getOriginalFilename());
        ThrowUtils.throwIf(extension == null, ErrorCode.PARAMS_ERROR, "文件格式错误");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(extension), ErrorCode.PARAMS_ERROR, "仅支持 jpeg、png、jpg、webp 格式");

        // 生成文件名和路径
        String filename = UUID.randomUUID().toString() + "." + extension;
        String pathPrefix = FEEDBACK_PATH_PREFIX + loginUser.getId() + "/";

        // 上传到存储
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath(pathPrefix)
                .setSaveFilename(filename)
                .upload();

        log.info("反馈附件上传成功: userId={}, url={}", loginUser.getId(), fileInfo.getUrl());

        return ResultUtils.success(fileInfo.getUrl());
    }
}
