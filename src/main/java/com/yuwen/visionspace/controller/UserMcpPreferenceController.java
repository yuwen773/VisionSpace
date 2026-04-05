package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.agent.service.UserMcpPreferenceService;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/preference/mcp")
@RequiredArgsConstructor
public class UserMcpPreferenceController {

    private final UserMcpPreferenceService userMcpPreferenceService;

    @Resource
    private UserService userService;

    @GetMapping
    public BaseResponse<List<String>> getDefaultEnabledServers(HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        List<String> servers = userMcpPreferenceService.getDefaultEnabledServers(userId);
        return ResultUtils.success(servers);
    }

    @PutMapping
    public BaseResponse<Void> setDefaultEnabledServers(@RequestBody List<String> mcpServerCodes, HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        userMcpPreferenceService.setDefaultEnabledServers(userId, mcpServerCodes);
        return ResultUtils.success(null);
    }

    private Long getLoginUserId(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return loginUser.getId();
    }
}
