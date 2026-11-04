package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.agent.service.UserPreferenceService;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.dto.agent.PreferenceAutoLearnRequest;
import com.yuwen.visionspace.model.dto.agent.PreferenceUpdateRequest;
import com.yuwen.visionspace.model.dto.agent.PreferenceViewVO;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户长期偏好控制接口。
 *
 * <p>查看、显式修改、清除与自动学习开关。用户身份只来自服务端登录态，
 * 偏好归属条件全部在 SQL 里——不存在「传别人的 userId」的入口，
 * 管理员也没有任何跨用户读写偏好的接口。
 */
@Slf4j
@RestController
@RequestMapping("/agent/preference")
public class AgentPreferenceController {

    @Resource
    private UserPreferenceService userPreferenceService;

    @Resource
    private UserService userService;

    /**
     * 查看当前用户的长期偏好。
     */
    @GetMapping
    public BaseResponse<PreferenceViewVO> getPreference(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userPreferenceService.getView(loginUser.getId()));
    }

    /**
     * 显式修改偏好设置（用户直接编辑）。
     *
     * <p><b>整节替换语义</b>：请求里的 explicit 完整覆盖已有的显式设置——
     * 未携带的白名单键会被清掉（不影响学习产物与反馈历史）；
     * 白名单外的键被忽略。客户端应先 GET 视图、编辑后再整体提交。
     */
    @PostMapping("/update")
    public BaseResponse<PreferenceViewVO> updatePreference(@RequestBody PreferenceUpdateRequest requestBody,
                                                            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (requestBody == null || requestBody.getExplicit() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "偏好内容不能为空");
        }
        return ResultUtils.success(
                userPreferenceService.updateExplicit(loginUser.getId(), requestBody.getExplicit()));
    }

    /**
     * 清除偏好数据。行保留为墓碑，在途学习任务不能写回已清除的内容。
     */
    @PostMapping("/clear")
    public BaseResponse<PreferenceViewVO> clearPreference(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        log.info("用户清除长期偏好, userId={}", loginUser.getId());
        return ResultUtils.success(userPreferenceService.clear(loginUser.getId()));
    }

    /**
     * 设置自动学习开关。默认关闭：自动学习只在用户明确开启后发生。
     */
    @PostMapping("/auto-learn")
    public BaseResponse<PreferenceViewVO> setAutoLearn(@RequestBody PreferenceAutoLearnRequest requestBody,
                                                        HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (requestBody == null || requestBody.getEnabled() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "开关状态不能为空");
        }
        log.info("用户设置自动学习开关, userId={}, enabled={}", loginUser.getId(), requestBody.getEnabled());
        return ResultUtils.success(
                userPreferenceService.setAutoLearn(loginUser.getId(), requestBody.getEnabled()));
    }
}
