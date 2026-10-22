package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.PictureContentService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

/**
 * 图片实际内容入口。
 *
 * <p>原图、缩略图、预览与下载都经由这里，每次请求按当前角色判定。
 * 公开已审核图片允许匿名；其余请求必须携带登录态。
 * 响应禁止被中间缓存复用，避免撤权后仍能读到内容。
 */
@Slf4j
@RestController
@RequestMapping("/picture/content")
public class PictureContentController {

    @Resource
    private PictureContentService pictureContentService;

    @Resource
    private UserService userService;

    @GetMapping("/{pictureId}/original")
    public ResponseEntity<byte[]> original(@PathVariable Long pictureId,
                                           @RequestParam(value = "download", defaultValue = "false") boolean download,
                                           HttpServletRequest request) {
        return respond(pictureId, PictureContentService.ContentKind.ORIGINAL, download, request);
    }

    @GetMapping("/{pictureId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable Long pictureId,
                                            HttpServletRequest request) {
        return respond(pictureId, PictureContentService.ContentKind.THUMBNAIL, false, request);
    }

    @GetMapping("/{pictureId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long pictureId,
                                          HttpServletRequest request) {
        return respond(pictureId, PictureContentService.ContentKind.PREVIEW, false, request);
    }

    private ResponseEntity<byte[]> respond(Long pictureId, PictureContentService.ContentKind kind,
                                           boolean download, HttpServletRequest request) {
        User viewer = userService.getLoginUserIfPresent(request);
        PictureContentService.Content content;
        try {
            content = pictureContentService.loadContent(pictureId, kind, viewer, download);
        } catch (BusinessException e) {
            return ResponseEntity.status(httpStatus(e.getCode())).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setCacheControl(CacheControl.noStore().mustRevalidate());
        headers.set("X-Content-Type-Options", "nosniff");
        if (download) {
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(content.downloadName(), StandardCharsets.UTF_8)
                    .build());
        }
        return new ResponseEntity<>(content.bytes(), headers, HttpStatus.OK);
    }

    /**
     * 业务错误码到 HTTP 状态码。内容入口返回二进制响应，鉴权失败同样用状态码表达。
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
