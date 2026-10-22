package com.yuwen.visionspace.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yuwen.visionspace.common.JsonContentType;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.SpaceRoleEnum;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.SpaceUserService;
import com.yuwen.visionspace.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

import static com.yuwen.visionspace.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
@Slf4j
public class StpInterfaceImpl implements StpInterface {

    // 默认是 /api
    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 返回一个账号所拥有的权限码集合。
     *
     * <p>授权事实只来自服务端会话中的登录用户与数据库里的成员关系：
     * 请求体中的 spaceUser / space / picture 对象一律不参与判定。
     * 上下文缺失或无法解析时按拒绝处理，不能用空上下文换取管理员权限。
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 判断 loginType，仅对类型为 "space" 进行权限校验
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }
        User sessionUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (sessionUser == null || sessionUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        Long userId = sessionUser.getId();
        // 重新读取当前用户，角色变更（如撤销平台管理员）立即生效，不使用登录时的快照
        User loginUser = userService.getById(userId);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户不存在");
        }
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        // 公共图库：没有空间与图片标识时只按公开规则放行
        Long pictureId = authContext.getPictureId();
        Long spaceId = authContext.getSpaceId();
        Long spaceUserId = authContext.getSpaceUserId();
        if (pictureId == null && spaceId == null && spaceUserId != null) {
            // 成员标识只用于定位所属空间，角色仍按当前数据库中的成员关系判定
            SpaceUser target = spaceUserService.getById(spaceUserId);
            if (target == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            spaceId = target.getSpaceId();
        }
        if (pictureId == null && spaceId == null) {
            return publicLibraryPermissions(loginUser);
        }
        if (pictureId != null) {
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            spaceId = picture.getSpaceId();
            if (spaceId == null) {
                // 公共图库中的图片：仅作者与管理员可修改，其余只读
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
                }
                return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
            }
        }
        if (spaceId == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "缺少空间标识");
        }
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
            }
            return new ArrayList<>();
        }
        // 团队空间，按当前数据库中的成员关系取角色；管理员不因平台角色自动获得成员权限
        SpaceUser spaceUser = spaceUserService.lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, userId)
                .one();
        if (spaceUser == null) {
            return new ArrayList<>();
        }
        return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
    }

    /**
     * 本项目中不使用。返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    /**
     * 公开图库的权限：管理员拥有完整权限，其他登录用户可查看与上传，
     * 单张图片的修改与删除仍由图片归属决定。
     */
    private List<String> publicLibraryPermissions(User loginUser) {
        if (userService.isAdmin(loginUser)) {
            return spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        }
        return List.of(SpaceUserPermissionConstant.PICTURE_VIEW,
                SpaceUserPermissionConstant.PICTURE_UPLOAD);
    }

    /**
     * 从请求中解析资源标识。
     *
     * <p>只读取 id / spaceId / pictureId / spaceUserId 这类标识字段；
     * 请求体里的 spaceUser、space、picture 对象即使被伪造也不会被采纳。
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;
        if (JsonContentType.isJson(contentType)) {
            authRequest = parseIdentifiers(getRequestBody(request));
        } else {
            authRequest = BeanUtil.toBean(getParamMap(request), SpaceUserAuthContext.class);
        }
        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            // 获取到请求路径的业务前缀，/api/picture/aaa?a=1
            String requestURI = request.getRequestURI();
            // 先替换掉上下文，剩下的就是前缀
            String partURI = requestURI.replace(contextPath + "/", "");
            // 获取前缀的第一个斜杠前的字符串
            String moduleName = StrUtil.subBefore(partURI, "/", false);
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }

    /**
     * 从 JSON 请求体中只提取标识字段。
     */
    private SpaceUserAuthContext parseIdentifiers(String body) {
        SpaceUserAuthContext context = new SpaceUserAuthContext();
        if (StrUtil.isBlank(body)) {
            return context;
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(body);
        } catch (Exception e) {
            // 解析失败时不授予任何上下文，交由调用方按缺失标识拒绝
            log.warn("Failed to parse request body for space auth context");
            return context;
        }
        context.setId(json.getLong("id"));
        context.setPictureId(json.getLong("pictureId"));
        context.setSpaceId(json.getLong("spaceId"));
        context.setSpaceUserId(json.getLong("spaceUserId"));
        return context;
    }

    /**
     * 获取请求体
     */
    private String getRequestBody(HttpServletRequest request) {
        try (java.io.BufferedReader reader = request.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to read request body", e);
            return "";
        }
    }

    /**
     * 获取请求参数Map
     */
    private Map<String, String> getParamMap(HttpServletRequest request) {
        Map<String, String> paramMap = new java.util.HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                paramMap.put(key, values[0]);
            }
        });
        return paramMap;
    }
}
