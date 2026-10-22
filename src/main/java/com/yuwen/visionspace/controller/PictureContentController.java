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
import java.util.concurrent.TimeUnit;

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
        User viewer = getLoginUserIfPresent(request);
        PictureContentService.Content content;
        try {
            content = pictureContentService.loadContent(pictureId, kind, viewer, download);
        } catch (BusinessException e) {
            // 内容入口返回二进制响应，鉴权失败同样用 HTTP 状态码表达
            HttpStatus status = switch (e.getCode()) {
                case 40100 -> HttpStatus.UNAUTHORIZED;
                case 40101 -> HttpStatus.FORBIDDEN;
                case 40400 -> HttpStatus.NOT_FOUND;
                case 40000 -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            return ResponseEntity.status(status).build();
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
     * 匿名入口需要区分"未登录"与"登录但无权限"，这里只取登录态，不抛异常。
     */
    private User getLoginUserIfPresent(HttpServletRequest request) {
        try {
            return userService.getLoginUser(request);
        } catch (BusinessException e) {
            if (ErrorCode.NOT_LOGIN_ERROR.getCode() == e.getCode()) {
                return null;
            }
            throw e;
        }
    }
}
