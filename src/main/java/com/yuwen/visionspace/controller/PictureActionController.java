package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.model.dto.picture.PictureActionReportCommand;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.PictureActionService;
import com.yuwen.visionspace.service.UserService;
import com.yuwen.visionspace.utils.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 图片行为上报接口
 * 用于收集用户在首页、详情页等场景下对图片的曝光、点击、浏览、点赞、收藏、下载、分享等行为
 *
 * <p>公开入口：匿名与登录用户都可以上报，但边界不同。
 * <ul>
 *   <li>匿名只允许曝光、点击、浏览；点赞、收藏等账户动作要求登录；</li>
 *   <li>目标必须是公开图库里已审核通过的图片；</li>
 *   <li>客户端声明的用户 id 一律拒绝，身份只以服务端解析结果为准；</li>
 *   <li>事件幂等键保证同一事件重放不会重复入账。</li>
 * </ul>
 *
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
     * @param request 行为上报请求，包含图片ID、行为类型、来源、事件ID等信息
     * @param httpRequest HTTP请求，用于获取当前登录用户
     * @return 上报结果
     */
    @PostMapping("/report")
    public BaseResponse<Boolean> reportAction(
            @RequestBody PictureActionRequest request,
            HttpServletRequest httpRequest) {
        // 未登录不是错误：匿名行为同样要能上报，强制登录的查询不能挡住匿名分支
        User loginUser = userService.getLoginUserIfPresent(httpRequest);

        PictureActionReportCommand command = new PictureActionReportCommand();
        command.setPictureId(request.getPictureId());
        command.setActionType(request.getActionType());
        command.setSource(request.getSource());
        command.setEventId(request.getEventId());
        // 客户端声明的用户 id 原样带下去，只为让服务层「明确拒绝」
        command.setDeclaredUserId(request.getUserId());
        command.setActorUserId(loginUser == null ? null : loginUser.getId());
        command.setClientKey(clientKey(httpRequest, loginUser));

        pictureActionService.reportAction(command);
        return ResultUtils.success(true);
    }

    /**
     * 限流与去重使用的客户端标识，必须脱敏：登录用户按用户 id，
     * 匿名用户按来源地址的哈希，不落原始地址。
     */
    private String clientKey(HttpServletRequest httpRequest, User loginUser) {
        if (loginUser != null) {
            return "u:" + loginUser.getId();
        }
        String remoteAddress = httpRequest.getRemoteAddr();
        return "a:" + HashUtils.shortHash(remoteAddress == null ? "" : remoteAddress);
    }

    /**
     * 行为上报请求体
     */
    public static class PictureActionRequest {
        /** 图片ID */
        private Long pictureId;
        /** 行为类型：0=impression 1=click 2=view 3=like 4=collect 5=download 6=share */
        private Integer actionType;
        /** 来源：0=HOME_RECOMMEND 1=DETAIL 2=SEARCH 3=OTHER */
        private Integer source;
        /** 事件ID：客户端为一次逻辑事件生成的幂等键，重放同一个事件不会重复入账 */
        private String eventId;
        /** 用户ID：客户端不得声明，出现在请求里会被明确拒绝 */
        private Long userId;

        public Long getPictureId() { return pictureId; }
        public void setPictureId(Long pictureId) { this.pictureId = pictureId; }
        public Integer getActionType() { return actionType; }
        public void setActionType(Integer actionType) { this.actionType = actionType; }
        public Integer getSource() { return source; }
        public void setSource(Integer source) { this.source = source; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }
}
