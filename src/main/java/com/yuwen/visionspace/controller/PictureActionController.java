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

/**
 * 图片行为上报接口
 * 用于收集用户在首页、详情页等场景下对图片的曝光、点击、浏览、点赞、收藏、下载、分享等行为
 * @ApiNote 完整的行为追踪是推荐系统的基础，用于计算 CTR、用户兴趣画像等
 */
@Slf4j
@RestController
@RequestMapping("/picture/action")
public class PictureActionController {

    @Resource
    private PictureActionService pictureActionService;

    @Resource
    private UserService userService;

    /**
     * 上报用户行为
     *
     * @param request 行为上报请求，包含图片ID、行为类型、来源等信息
     * @param httpRequest HTTP请求，用于获取当前登录用户
     * @return 上报结果
     */
    @PostMapping("/report")
    public BaseResponse<Boolean> reportAction(
            @RequestBody PictureActionRequest request,
            HttpServletRequest httpRequest) {
        // 获取当前登录用户ID，未登录用户记为0（匿名用户）
        Long userId = getLoginUserId(httpRequest);
        if (userId == null) {
            userId = 0L;
        }

        // 调用服务层保存行为数据
        pictureActionService.reportAction(
            userId,
            request.getPictureId(),
            request.getActionType(),
            request.getSource()
        );
        return ResultUtils.success(true);
    }

    /**
     * 从请求中获取当前登录用户的ID
     *
     * @param request HTTP请求
     * @return 用户ID，未登录返回null
     */
    private Long getLoginUserId(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        if (user == null) {
            return null;
        }
        return user.getId();
    }

    /**
     * 行为上报请求体
     */
    public static class PictureActionRequest {
        /** 图片ID */
        private Long pictureId;
        /** 行为类型：impression/click/view/like/collect/download/share */
        private String actionType;
        /** 来源：HOME_RECOMMEND/DETAIL/SEARCH 等 */
        private String source;

        public Long getPictureId() { return pictureId; }
        public void setPictureId(Long pictureId) { this.pictureId = pictureId; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
}
