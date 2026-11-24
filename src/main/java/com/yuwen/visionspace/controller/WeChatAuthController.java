package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.model.dto.user.WxBindRequest;
import com.yuwen.visionspace.model.dto.user.WxLoginRequest;
import com.yuwen.visionspace.model.vo.WxLoginVO;
import com.yuwen.visionspace.service.MiniProgramAuthService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信小程序登录入口。
 *
 * <p>登录态载体与 Web 不同：小程序拿不到可靠的 Cookie，改为由服务端签发 Sa-Token 令牌，
 * 客户端在后续请求的 {@code satoken} 头里带回（只认请求头，不接受查询参数）。
 * 账号、空间与权限模型与 Web 完全共用。
 */
@RestController
@RequestMapping("/user/wx")
public class WeChatAuthController {

    @Resource
    private MiniProgramAuthService miniProgramAuthService;

    /**
     * 静默登录：已绑定过的微信直接换回令牌。
     * 未绑定返回 {@code 40102}，客户端应转入绑定页。
     */
    @PostMapping("/login")
    public BaseResponse<WxLoginVO> login(@RequestBody WxLoginRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(miniProgramAuthService.login(request.getCode()));
    }

    /**
     * 首次绑定：用现有账号密码证明归属，把当前微信绑定到该账号并签发令牌。
     */
    @PostMapping("/bind")
    public BaseResponse<WxLoginVO> bind(@RequestBody WxBindRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(miniProgramAuthService.bind(
                request.getCode(), request.getUserAccount(), request.getUserPassword()));
    }
}
