package com.yuwen.visionspace.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.storage.FileUploadService;
import com.yuwen.visionspace.model.dto.feedback.FeedbackAddRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackQueryRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackUpdateStatusRequest;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.FeedbackVO;
import com.yuwen.visionspace.service.FeedbackService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private FileUploadService fileUploadService;

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

    @PostMapping("/upload")
    public BaseResponse<String> uploadFeedbackAttachment(@RequestParam("file") MultipartFile file,
                                                             HttpServletRequest request) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        String pathPrefix = "/temp/" + loginUser.getId() + "/";
        String url = fileUploadService.uploadImageFile(file, pathPrefix);

        log.info("反馈附件上传成功: userId={}, url={}", loginUser.getId(), url);
        return ResultUtils.success(url);
    }
}
