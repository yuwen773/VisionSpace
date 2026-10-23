package com.yuwen.visionspace.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.mapper.ResourceAccessGrantMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.ResourceAccessGrant;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.SpaceUser;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.ResourceGrantStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceIsolationStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.model.enums.SpaceRoleEnum;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.model.vo.ResourceGrantVO;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.ResourceAccessGrantService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.SpaceUserService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ResourceAccessGrantServiceImpl extends ServiceImpl<ResourceAccessGrantMapper, ResourceAccessGrant>
        implements ResourceAccessGrantService {

    /** 单次授权的最短有效期：避免用极短到期时间制造事实上的永久授权绕路测试。 */
    private static final Duration MIN_VALIDITY = Duration.ofMinutes(1);

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private PictureService pictureService;

    @Resource
    private ResourceIsolationService resourceIsolationService;

    @Override
    public boolean hasActiveGrant(Long pictureId, Long viewerUserId) {
        if (pictureId == null || viewerUserId == null) {
            return false;
        }
        return this.lambdaQuery()
                .eq(ResourceAccessGrant::getResourceType, ResourceTypeEnum.PICTURE.getValue())
                .eq(ResourceAccessGrant::getResourceId, pictureId)
                .eq(ResourceAccessGrant::getGranteeUserId, viewerUserId)
                .eq(ResourceAccessGrant::getStatus, ResourceGrantStatusEnum.ACTIVE.getValue())
                .gt(ResourceAccessGrant::getExpiresTime, new Date())
                .count() > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceAccessGrant grant(Picture picture, User grantor, Long granteeId, Date expiresAt, String reason) {
        ThrowUtils.throwIf(grantor == null || grantor.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(picture == null || picture.getId() == null, ErrorCode.PARAMS_ERROR, "缺少资源标识");
        ThrowUtils.throwIf(granteeId == null || granteeId <= 0, ErrorCode.PARAMS_ERROR, "缺少被授权人");
        ThrowUtils.throwIf(expiresAt == null, ErrorCode.PARAMS_ERROR, "到期时间不能为空");
        ThrowUtils.throwIf(reason != null && reason.length() > 500, ErrorCode.PARAMS_ERROR, "理由过长");
        // 指定查看者必须是真实存在的用户
        User grantee = userService.getById(granteeId);
        ThrowUtils.throwIf(grantee == null, ErrorCode.NOT_FOUND_ERROR, "被授权用户不存在");
        ThrowUtils.throwIf(granteeId.equals(grantor.getId()), ErrorCode.PARAMS_ERROR, "不能给自己授权");
        // 授权主体：个人资源所有者或团队管理员；平台管理员身份本身不构成授权依据，
        // 但平台管理员若独立拥有团队管理员身份，该身份照常生效
        ThrowUtils.throwIf(!canGrant(picture, grantor), ErrorCode.NO_AUTH_ERROR, "无权为该资源授权");
        validateExpiry(expiresAt, new Date());
        // 隔离中的资源不能被重新授权为可读
        ThrowUtils.throwIf(isQuarantined(picture), ErrorCode.OPERATION_ERROR, "资源处于隔离状态，不能授权");
        // 到期即失效：先释放同一资源同一被授权人的过期授权，才能重新授予
        releaseExpiredGrant(picture.getId(), granteeId);

        ResourceAccessGrant entity = new ResourceAccessGrant();
        entity.setResourceType(ResourceTypeEnum.PICTURE.getValue());
        entity.setResourceId(picture.getId());
        entity.setGranteeUserId(granteeId);
        entity.setGrantedByUserId(grantor.getId());
        entity.setReason(StrUtil.trim(reason));
        entity.setExpiresTime(expiresAt);
        entity.setStatus(ResourceGrantStatusEnum.ACTIVE.getValue());
        entity.setActiveKey(activeKey(picture.getId(), granteeId));
        try {
            ThrowUtils.throwIf(!this.save(entity), ErrorCode.OPERATION_ERROR, "授权保存失败");
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该用户已有同一资源的有效授权");
        }
        log.info("资源授权已创建: pictureId={}, grantee={}, grantor={}, expires={}",
                picture.getId(), granteeId, grantor.getId(), expiresAt);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceAccessGrant revoke(Long grantId, User operator, String reason) {
        ThrowUtils.throwIf(operator == null || operator.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(grantId == null || grantId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(reason != null && reason.length() > 500, ErrorCode.PARAMS_ERROR, "理由过长");
        ResourceAccessGrant grant = this.getById(grantId);
        ThrowUtils.throwIf(grant == null, ErrorCode.NOT_FOUND_ERROR, "授权不存在");
        ThrowUtils.throwIf(!Objects.equals(grant.getStatus(), ResourceGrantStatusEnum.ACTIVE.getValue()),
                ErrorCode.OPERATION_ERROR, "授权已失效");
        Picture picture = pictureService.getById(grant.getResourceId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "资源不存在");
        // 撤销同样要求当前授权主体：资源所有权或团队管理员角色变更后不能再撤销
        ThrowUtils.throwIf(!canGrant(picture, operator), ErrorCode.NO_AUTH_ERROR, "无权撤销该授权");

        Date now = new Date();
        boolean updated = this.lambdaUpdate()
                .eq(ResourceAccessGrant::getId, grantId)
                .eq(ResourceAccessGrant::getStatus, ResourceGrantStatusEnum.ACTIVE.getValue())
                .set(ResourceAccessGrant::getStatus, ResourceGrantStatusEnum.REVOKED.getValue())
                .set(ResourceAccessGrant::getActiveKey, null)
                .set(ResourceAccessGrant::getRevokedByUserId, operator.getId())
                .set(ResourceAccessGrant::getRevokeReason, StrUtil.trim(reason))
                .set(ResourceAccessGrant::getRevokeTime, now)
                .update();
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "授权已失效");
        grant.setStatus(ResourceGrantStatusEnum.REVOKED.getValue());
        grant.setActiveKey(null);
        grant.setRevokedByUserId(operator.getId());
        grant.setRevokeReason(reason);
        grant.setRevokeTime(now);
        log.info("资源授权已撤销: grantId={}, pictureId={}, operator={}",
                grantId, grant.getResourceId(), operator.getId());
        return grant;
    }

    @Override
    public List<ResourceGrantVO> listByPicture(Picture picture, User viewer) {
        ThrowUtils.throwIf(picture == null || picture.getId() == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(viewer == null || viewer.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        boolean platformAdminAudit = userService.isAdmin(viewer);
        ThrowUtils.throwIf(!platformAdminAudit && !canGrant(picture, viewer),
                ErrorCode.NO_AUTH_ERROR, "无权查看该资源的授权");
        List<ResourceAccessGrant> grants = this.lambdaQuery()
                .eq(ResourceAccessGrant::getResourceType, ResourceTypeEnum.PICTURE.getValue())
                .eq(ResourceAccessGrant::getResourceId, picture.getId())
                .orderByDesc(ResourceAccessGrant::getId)
                .list();
        return toVOs(grants, Map.of(picture.getId(), picture.getName()));
    }

    @Override
    public List<ResourceGrantVO> listActiveByGrantee(Long granteeUserId) {
        if (granteeUserId == null) {
            return List.of();
        }
        List<ResourceAccessGrant> grants = this.lambdaQuery()
                .eq(ResourceAccessGrant::getResourceType, ResourceTypeEnum.PICTURE.getValue())
                .eq(ResourceAccessGrant::getGranteeUserId, granteeUserId)
                .eq(ResourceAccessGrant::getStatus, ResourceGrantStatusEnum.ACTIVE.getValue())
                .gt(ResourceAccessGrant::getExpiresTime, new Date())
                .orderByAsc(ResourceAccessGrant::getExpiresTime)
                .list();
        if (grants.isEmpty()) {
            return List.of();
        }
        Set<Long> pictureIds = grants.stream().map(ResourceAccessGrant::getResourceId).collect(Collectors.toSet());
        Map<Long, Picture> pictures = pictureService.listByIds(pictureIds).stream()
                .collect(Collectors.toMap(Picture::getId, Function.identity()));
        Map<Long, String> names = pictures.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));
        return toVOs(grants, names);
    }

    @Override
    public boolean canGrant(Picture picture, User user) {
        if (picture == null || user == null || user.getId() == null) {
            return false;
        }
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库中的个人资源：所有者本人；平台管理员不因平台角色获得授权资格
            return Objects.equals(picture.getUserId(), user.getId());
        }
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            return false;
        }
        if (Objects.equals(space.getUserId(), user.getId())) {
            return true;
        }
        if (space.getSpaceType() == null || space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间仅所有者；平台管理员不能代为授权
            return false;
        }
        // 团队空间按成员关系判定：平台管理员若独立持有团队管理员身份，
        // 该身份照常生效；平台角色本身不额外扩大授权范围
        SpaceUser spaceUser = spaceUserService.lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, user.getId())
                .one();
        return spaceUser != null
                && SpaceRoleEnum.ADMIN.getValue().equals(spaceUser.getSpaceRole());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireOutdatedGrants() {
        Date now = new Date();
        List<Long> expiredIds = this.lambdaQuery()
                .select(ResourceAccessGrant::getId)
                .eq(ResourceAccessGrant::getStatus, ResourceGrantStatusEnum.ACTIVE.getValue())
                .le(ResourceAccessGrant::getExpiresTime, now)
                .list().stream()
                .map(ResourceAccessGrant::getId)
                .toList();
        if (expiredIds.isEmpty()) {
            return 0;
        }
        this.lambdaUpdate()
                .in(ResourceAccessGrant::getId, expiredIds)
                .eq(ResourceAccessGrant::getStatus, ResourceGrantStatusEnum.ACTIVE.getValue())
                .set(ResourceAccessGrant::getStatus, ResourceGrantStatusEnum.EXPIRED.getValue())
                .set(ResourceAccessGrant::getActiveKey, null)
                .update();
        log.info("到期授权已迁移为过期并释放唯一键: count={}, now={}", expiredIds.size(), now);
        return expiredIds.size();
    }

    /**
     * 释放同一资源同一被授权人的过期授权。
     *
     * <p>唯一约束表达"同一资源同一对象最多一条有效授权"，到期后必须真正释放，
     * 否则再次授权会被判为重复授权。
     */
    private void releaseExpiredGrant(Long pictureId, Long granteeId) {
        this.lambdaUpdate()
                .eq(ResourceAccessGrant::getResourceType, ResourceTypeEnum.PICTURE.getValue())
                .eq(ResourceAccessGrant::getResourceId, pictureId)
                .eq(ResourceAccessGrant::getGranteeUserId, granteeId)
                .eq(ResourceAccessGrant::getStatus, ResourceGrantStatusEnum.ACTIVE.getValue())
                .le(ResourceAccessGrant::getExpiresTime, new Date())
                .set(ResourceAccessGrant::getStatus, ResourceGrantStatusEnum.EXPIRED.getValue())
                .set(ResourceAccessGrant::getActiveKey, null)
                .update();
    }

    private void validateExpiry(Date expiresAt, Date now) {
        Instant nowInstant = now.toInstant();
        Instant expiresInstant = expiresAt.toInstant();
        ThrowUtils.throwIf(!expiresInstant.isAfter(nowInstant.plus(MIN_VALIDITY)),
                ErrorCode.PARAMS_ERROR, "到期时间必须晚于当前时间");
        ThrowUtils.throwIf(expiresInstant.isAfter(nowInstant.plus(MAX_VALIDITY)),
                ErrorCode.PARAMS_ERROR, "单次授权不能超过 " + MAX_VALIDITY.toDays() + " 天");
    }

    private boolean isQuarantined(Picture picture) {
        return resourceIsolationService.isQuarantined(ResourceTypeEnum.PICTURE.getValue(), picture.getId());
    }

    private List<ResourceGrantVO> toVOs(Collection<ResourceAccessGrant> grants, Map<Long, String> resourceNames) {
        if (grants == null || grants.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new java.util.HashSet<>();
        for (ResourceAccessGrant grant : grants) {
            userIds.add(grant.getGranteeUserId());
            userIds.add(grant.getGrantedByUserId());
        }
        userIds.remove(null);
        Map<Long, User> users = userIds.isEmpty() ? Map.of() : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Date now = new Date();
        List<ResourceGrantVO> result = new ArrayList<>();
        for (ResourceAccessGrant grant : grants) {
            ResourceGrantVO vo = new ResourceGrantVO();
            vo.setId(asId(grant.getId()));
            vo.setResourceType(grant.getResourceType());
            vo.setResourceId(asId(grant.getResourceId()));
            vo.setResourceName(resourceNames.get(grant.getResourceId()));
            vo.setGranteeUserId(asId(grant.getGranteeUserId()));
            vo.setGranteeUserName(displayName(users.get(grant.getGranteeUserId())));
            vo.setGrantedByUserId(asId(grant.getGrantedByUserId()));
            vo.setGrantedByUserName(displayName(users.get(grant.getGrantedByUserId())));
            vo.setReason(grant.getReason());
            vo.setExpiresTime(grant.getExpiresTime());
            vo.setStatus(currentStatus(grant, now));
            vo.setRevokedByUserId(asId(grant.getRevokedByUserId()));
            vo.setRevokeReason(grant.getRevokeReason());
            vo.setRevokeTime(grant.getRevokeTime());
            vo.setCreateTime(grant.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    /** 标识按十进制字符串给出，避免客户端丢失精度 */
    private String asId(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    /**
     * 展示状态：数据库记录 ACTIVE 但已过期时按 EXPIRED 展示，不修改记录本身。
     */
    private String currentStatus(ResourceAccessGrant grant, Date now) {
        if (!Objects.equals(grant.getStatus(), ResourceGrantStatusEnum.ACTIVE.getValue())) {
            return ResourceGrantStatusEnum.getEnumByValue(grant.getStatus()) == null
                    ? "UNKNOWN"
                    : ResourceGrantStatusEnum.getEnumByValue(grant.getStatus()).name();
        }
        if (grant.getExpiresTime() != null && !grant.getExpiresTime().after(now)) {
            return ResourceGrantStatusEnum.EXPIRED.name();
        }
        return ResourceGrantStatusEnum.ACTIVE.name();
    }

    private String displayName(User user) {
        return user == null ? null : user.getUserName();
    }

    static String activeKey(Long pictureId, Long granteeId) {
        return ResourceTypeEnum.PICTURE.getValue() + ":" + pictureId + ":" + granteeId;
    }
}
