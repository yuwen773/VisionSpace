package com.yuwen.visionspace.manager.websocket;

import cn.hutool.core.util.ObjUtil;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.manager.auth.SpaceUserAuthManager;
import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * WebSocket 编辑访问复核器。
 *
 * <p>握手时的校验只代表「建立连接那一刻」的授权；降权、移除成员、注销
 * 都发生在连接存活期间，所以每条编辑相关消息都必须重新确认：
 * HTTP 会话仍然存活（注销即失效）、用户仍然存在、当前权限仍包含编辑。
 * 判定一律走服务端数据（Redis 会话 + 数据库），不信任握手时缓存的快照。
 */
@Slf4j
@Component
public class WsEditAccessChecker {

    /** 复核结论：通过时携带当前数据库用户，拒绝时携带原因。 */
    public record AccessCheckResult(boolean allowed, User user, String reason) {
        public static AccessCheckResult ok(User user) {
            return new AccessCheckResult(true, user, null);
        }

        public static AccessCheckResult denied(String reason) {
            return new AccessCheckResult(false, null, reason);
        }
    }

    @Resource
    private SessionRepository<? extends Session> sessionRepository;

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 复核一条连接当前是否仍有编辑权限。
     *
     * @param session   WebSocket 会话（握手时已写入 httpSessionId / pictureId）
     * @param pictureId 目标图片
     */
    @SuppressWarnings("unchecked")
    public AccessCheckResult check(WebSocketSessionLike session, Long pictureId) {
        Object httpSessionId = session.getAttribute("httpSessionId");
        if (httpSessionId == null || pictureId == null) {
            return AccessCheckResult.denied("缺少会话标识");
        }
        Session httpSession = sessionRepository.findById(httpSessionId.toString());
        if (httpSession == null) {
            return AccessCheckResult.denied("登录会话已失效");
        }
        Object userObj = httpSession.getAttribute(UserConstant.USER_LOGIN_STATE);
        if (!(userObj instanceof User sessionUser) || sessionUser.getId() == null) {
            return AccessCheckResult.denied("登录会话已失效");
        }
        // 以数据库当前状态为准：注销、封禁、改角色都体现在这里
        User user = userService.getById(sessionUser.getId());
        if (user == null) {
            return AccessCheckResult.denied("用户不存在");
        }
        Picture picture = pictureService.getById(pictureId);
        if (ObjUtil.isEmpty(picture)) {
            return AccessCheckResult.denied("图片不存在");
        }
        Space space = null;
        if (picture.getSpaceId() != null) {
            space = spaceService.getById(picture.getSpaceId());
            if (space == null) {
                return AccessCheckResult.denied("图片所在空间不存在");
            }
        }
        var permissionList = spaceUserAuthManager.getPermissionList(space, user);
        if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
            return AccessCheckResult.denied("没有编辑图片的权限");
        }
        return AccessCheckResult.ok(user);
    }

    /**
     * 与 {@link org.springframework.web.socket.WebSocketSession} 解耦的最小接口，便于单测。
     */
    public interface WebSocketSessionLike {
        Object getAttribute(String name);
    }
}
