package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.config.AuthTokenProperties;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.auth.StpKit;
import com.yuwen.visionspace.manager.wechat.WeChatSessionProvider;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.WxLoginVO;
import com.yuwen.visionspace.service.MiniProgramAuthService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * @see MiniProgramAuthService
 */
@Slf4j
@Service
public class MiniProgramAuthServiceImpl implements MiniProgramAuthService {

    private final UserService userService;

    private final WeChatSessionProvider weChatSessionProvider;

    private final AuthTokenProperties authTokenProperties;

    public MiniProgramAuthServiceImpl(UserService userService,
                                      WeChatSessionProvider weChatSessionProvider,
                                      AuthTokenProperties authTokenProperties) {
        this.userService = userService;
        this.weChatSessionProvider = weChatSessionProvider;
        this.authTokenProperties = authTokenProperties;
    }

    @Override
    public WxLoginVO login(String code) {
        String openId = weChatSessionProvider.exchangeOpenId(code);
        User bound = findByOpenId(openId);
        if (bound == null) {
            // 让客户端能明确区分「微信未绑定」与「凭据错误」：前者应展示绑定表单
            throw new BusinessException(ErrorCode.WX_NOT_BOUND_ERROR);
        }
        return issueToken(bound);
    }

    @Override
    public WxLoginVO bind(String code, String userAccount, String userPassword) {
        String openId = weChatSessionProvider.exchangeOpenId(code);

        User alreadyBound = findByOpenId(openId);
        if (alreadyBound != null) {
            // 同一微信重复绑定同一账号：幂等登录，不再改库
            if (alreadyBound.getUserAccount() != null && alreadyBound.getUserAccount().equals(userAccount)) {
                return issueToken(alreadyBound);
            }
            throw new BusinessException(ErrorCode.WX_ALREADY_BOUND_ERROR);
        }

        // 校验账号密码；失败路径与 Web 登录一致（含时序均衡与摘要升级）
        User user = userService.authenticateForPassword(userAccount, userPassword);
        if (user.getWxOpenId() != null && !user.getWxOpenId().equals(openId)) {
            throw new BusinessException(ErrorCode.WX_ALREADY_BOUND_ERROR, "该账号已绑定其他微信");
        }

        // 一对一约束的落库形态：只有当前仍为空时才写入，并发下由唯一索引兜底
        boolean bound;
        try {
            bound = userService.lambdaUpdate()
                    .set(User::getWxOpenId, openId)
                    .eq(User::getId, user.getId())
                    .isNull(User::getWxOpenId)
                    .update();
        } catch (DataIntegrityViolationException e) {
            // 唯一索引拒绝了这次绑定：另一个账号刚刚抢到了同一个 openid
            throw new BusinessException(ErrorCode.WX_ALREADY_BOUND_ERROR);
        }
        if (!bound) {
            // 没有写入：要么本账号刚被绑定，要么 openid 刚被别的账号占用。
            // 只在前者是本人时幂等成功，其余一律拒绝，不覆盖任何已有绑定。
            User latest = userService.getById(user.getId());
            if (latest != null && openId.equals(latest.getWxOpenId())) {
                return issueToken(latest);
            }
            throw new BusinessException(ErrorCode.WX_ALREADY_BOUND_ERROR);
        }
        user.setWxOpenId(openId);
        log.info("小程序微信绑定成功: userId={}", user.getId());
        return issueToken(user);
    }

    private User findByOpenId(String openId) {
        return userService.lambdaQuery()
                .eq(User::getWxOpenId, openId)
                .one();
    }

    private WxLoginVO issueToken(User user) {
        // 与 Web 登录共用同一个空间令牌存储；Web 会话与小程序令牌互不依赖
        StpKit.SPACE.login(user.getId());
        WxLoginVO vo = new WxLoginVO();
        vo.setTokenName(authTokenProperties.getHeaderName());
        vo.setToken(StpKit.SPACE.getTokenValue());
        vo.setUser(userService.getLoginUserVO(user));
        return vo;
    }
}
