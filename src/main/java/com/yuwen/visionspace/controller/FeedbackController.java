package com.yuwen.visionspace.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.auth.FeedbackAccessPolicy;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.model.dto.feedback.FeedbackAddRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackQueryRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackUpdateStatusRequest;
import com.yuwen.visionspace.model.dto.file.UploadPictureResult;
import com.yuwen.visionspace.model.entity.FeedbackAttachment;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.entity.UserFeedback;
import com.yuwen.visionspace.model.vo.FeedbackAttachmentVO;
import com.yuwen.visionspace.model.vo.FeedbackVO;
import com.yuwen.visionspace.service.FeedbackAttachmentService;
import com.yuwen.visionspace.service.FeedbackService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

/**
 * 用户反馈。
 *
 * <p>反馈正文与附件都属于私人内容：详情只对创建者与具有反馈管理职责的平台管理员可见，
 * 附件内容每次请求按当前反馈权限判定。平台管理员身份不扩展到其他私人资源。
 */
@Slf4j
@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    @Resource
    private FeedbackService feedbackService;

    @Resource
    private FeedbackAttachmentService feedbackAttachmentService;

    @Resource
    private UserService userService;

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private FeedbackAccessPolicy feedbackAccessPolicy;

    /**
     * 提交反馈
     */
    @PostMapping("/add")
    public BaseResponse<Long> addFeedback(@RequestBody FeedbackAddRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        long result = feedbackService.addFeedback(request, loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 根据ID获取反馈详情。仅创建者或有反馈管理职责的平台管理员可读。
     */
    @GetMapping("/get/{id}")
    public BaseResponse<FeedbackVO> getFeedbackById(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        UserFeedback feedback = feedbackService.getRequiredFeedback(id);
        // 先判定可见性，再构造响应：拒绝时不会读取附件或返回任何正文
        ThrowUtils.throwIf(!canReadFeedback(feedback, loginUser), ErrorCode.NO_AUTH_ERROR);
        return ResultUtils.success(feedbackService.getFeedbackVO(feedback, loginUser));
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
        boolean result = feedbackService.updateFeedbackStatus(request, loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 上传反馈附件。
     *
     * <p>返回附件标识与受控入口，不返回源站地址：附件内容只能通过
     * {@code /feedback/attachment/{id}} 在权限校验之后读取。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<FeedbackAttachmentVO> uploadFeedbackAttachment(@RequestParam("file") MultipartFile file,
                                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        User loginUser = userService.getLoginUser(request);
        UploadPictureResult upload = pictureStorageService.uploadFeedbackAttachment(file, loginUser.getId());
        FeedbackAttachment attachment = feedbackAttachmentService.registerUpload(loginUser,
                upload.getPlatform(), upload.getStoragePath(), upload.getUrl(), upload.getPicSize());
        log.info("反馈附件上传成功: userId={}, attachmentId={}", loginUser.getId(), attachment.getId());
        FeedbackAttachmentVO vo = new FeedbackAttachmentVO();
        // 标识按十进制字符串给出：雪花标识超出 JavaScript 的安全整数范围
        vo.setId(String.valueOf(attachment.getId()));
        vo.setStatus("UPLOADED");
        vo.setUrl(feedbackAttachmentService.contentUrl(attachment, loginUser));
        return ResultUtils.success(vo);
    }

    /**
     * 读取反馈附件内容。每次请求按当前反馈权限与隔离状态判定。
     */
    @GetMapping("/attachment/{id}")
    public ResponseEntity<byte[]> getAttachment(@PathVariable("id") Long id, HttpServletRequest request) {
        User viewer = userService.getLoginUserIfPresent(request);
        FeedbackAttachmentService.AttachmentContent content;
        try {
            content = feedbackAttachmentService.loadContent(id, viewer);
        } catch (BusinessException e) {
            return ResponseEntity.status(httpStatus(e.getCode())).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        // 撤权后不得复用缓存中的附件内容
        headers.setCacheControl(CacheControl.noStore().mustRevalidate());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.downloadName(), StandardCharsets.UTF_8)
                .build());
        return new ResponseEntity<>(content.bytes(), headers, HttpStatus.OK);
    }

    private boolean canReadFeedback(UserFeedback feedback, User viewer) {
        return feedbackAccessPolicy.canReadFeedback(feedback, viewer);
    }

    /**
     * 业务错误码到 HTTP 状态码。附件入口返回二进制响应，鉴权失败同样用状态码表达。
     */
    private HttpStatus httpStatus(int code) {
        if (code == ErrorCode.NOT_LOGIN_ERROR.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.NO_AUTH_ERROR.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == ErrorCode.NOT_FOUND_ERROR.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        if (code == ErrorCode.PARAMS_ERROR.getCode()) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
