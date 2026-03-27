package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.PictureActionService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("/api/picture/action")
public class PictureActionController {

    @Resource
    private PictureActionService pictureActionService;

    @Resource
    private UserService userService;

    @PostMapping("/report")
    public BaseResponse<Boolean> reportAction(
            @RequestBody PictureActionRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getLoginUserId(httpRequest);
        if (userId == null) {
            userId = 0L;
        }

        pictureActionService.reportAction(
            userId,
            request.getPictureId(),
            request.getActionType(),
            request.getSource()
        );
        return ResultUtils.success(true);
    }

    private Long getLoginUserId(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        if (user == null) {
            return 0L;
        }
        return user.getId();
    }

    public static class PictureActionRequest {
        private Long pictureId;
        private String actionType;
        private String source;

        public Long getPictureId() { return pictureId; }
        public void setPictureId(Long pictureId) { this.pictureId = pictureId; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
}
