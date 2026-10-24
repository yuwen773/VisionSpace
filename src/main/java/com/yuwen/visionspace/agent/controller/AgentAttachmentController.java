package com.yuwen.visionspace.agent.controller;

import com.yuwen.visionspace.agent.service.AgentAttachmentService;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 会话附件的内容入口与删除入口。
 *
 * <p>浏览器只通过这里读附件：会话归属与资源归属每次请求都重新判定，
 * 源站地址不进入浏览器。
 */
@RestController
@RequestMapping("/agent/attachment")
public class AgentAttachmentController {

    @Resource
    private AgentAttachmentService agentAttachmentService;

    @Resource
    private UserService userService;

    /**
     * 读取附件内容。
     *
     * <p>会话与资源双重授权：绑定必须存在于该会话且有效，会话与资源都必须属于
     * 当前用户。
     */
    @GetMapping("/{resourceId}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long resourceId,
                                          @RequestParam String sessionId,
                                          HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        AgentAttachmentService.AttachmentContent content =
                agentAttachmentService.readContent(resourceId, sessionId, loginUser.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                // 附件内容每次都重新授权，不允许缓存复用
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }

    /**
     * 明确删除图片。
     *
     * <p>先停止访问（解除全部有效绑定），再清理对象；存储删除未确认时保留
     * 可重试状态。
     */
    @PostMapping("/{resourceId}/delete")
    public BaseResponse<Boolean> deleteAttachment(@PathVariable Long resourceId,
                                                  HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(agentAttachmentService.deleteImage(resourceId, loginUser.getId()));
    }
}
