package com.yuwen.visionspace.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.mapper.ResourceIsolationMapper;
import com.yuwen.visionspace.model.entity.ResourceIsolation;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.ResourceIsolationStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ResourceIsolationServiceImpl extends ServiceImpl<ResourceIsolationMapper, ResourceIsolation>
        implements ResourceIsolationService {

    @Resource
    private UserService userService;

    @Override
    public Set<Long> quarantinedIds(String resourceType, Collection<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Set.of();
        }
        List<ResourceIsolation> rows = this.lambdaQuery()
                .eq(ResourceIsolation::getResourceType, resourceType)
                .eq(ResourceIsolation::getStatus, ResourceIsolationStatusEnum.QUARANTINED.getValue())
                .in(ResourceIsolation::getResourceId, resourceIds)
                .list();
        Set<Long> result = new LinkedHashSet<>();
        for (ResourceIsolation row : rows) {
            result.add(row.getResourceId());
        }
        return result;
    }

    @Override
    public boolean isQuarantined(String resourceType, Long resourceId) {
        if (resourceType == null || resourceId == null) {
            return false;
        }
        return this.lambdaQuery()
                .eq(ResourceIsolation::getResourceType, resourceType)
                .eq(ResourceIsolation::getResourceId, resourceId)
                .eq(ResourceIsolation::getStatus, ResourceIsolationStatusEnum.QUARANTINED.getValue())
                .count() > 0;
    }

    @Override
    public void excludeQuarantined(QueryWrapper<?> wrapper, String column, String resourceType) {
        if (wrapper == null || StrUtil.isBlank(column) || StrUtil.isBlank(resourceType)) {
            return;
        }
        // 子查询按库实时判定：隔离/恢复立即对所有实例的列表查询生效，且不依赖进程内状态。
        // 必须用 NOT IN：若写成 NOT EXISTS 并把外层列裸写进子查询，
        // 该列会被 resource_isolation.id 遮蔽，条件恒假，隔离行反而全部返回。
        wrapper.apply(column + " NOT IN (SELECT ri.resourceId FROM resource_isolation ri"
                        + " WHERE ri.resourceType = {0} AND ri.status = {1})",
                resourceType, ResourceIsolationStatusEnum.QUARANTINED.getValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceIsolation quarantine(String resourceType, Long resourceId, User operator, String reason) {
        requireModerator(operator);
        ThrowUtils.throwIf(ResourceTypeEnum.getEnumByValue(resourceType) == null, ErrorCode.PARAMS_ERROR, "资源类型不支持");
        ThrowUtils.throwIf(resourceId == null || resourceId <= 0, ErrorCode.PARAMS_ERROR, "缺少资源标识");
        ThrowUtils.throwIf(StrUtil.isBlank(reason), ErrorCode.PARAMS_ERROR, "隔离理由不能为空");
        ThrowUtils.throwIf(reason.length() > 500, ErrorCode.PARAMS_ERROR, "隔离理由过长");
        if (isQuarantined(resourceType, resourceId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "资源已处于隔离状态");
        }
        ResourceIsolation isolation = new ResourceIsolation();
        isolation.setResourceType(resourceType);
        isolation.setResourceId(resourceId);
        isolation.setStatus(ResourceIsolationStatusEnum.QUARANTINED.getValue());
        isolation.setActiveKey(activeKey(resourceType, resourceId));
        isolation.setOperatorId(operator.getId());
        isolation.setReason(reason);
        try {
            ThrowUtils.throwIf(!this.save(isolation), ErrorCode.OPERATION_ERROR, "隔离记录保存失败");
        } catch (DuplicateKeyException e) {
            // 并发隔离由数据库唯一键收敛为一个有效隔离
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "资源已处于隔离状态");
        }
        log.info("资源已隔离: type={}, id={}, operator={}", resourceType, resourceId, operator.getId());
        return isolation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceIsolation release(String resourceType, Long resourceId, User operator, String reason) {
        requireModerator(operator);
        ThrowUtils.throwIf(resourceId == null || resourceId <= 0, ErrorCode.PARAMS_ERROR, "缺少资源标识");
        ThrowUtils.throwIf(StrUtil.isBlank(reason), ErrorCode.PARAMS_ERROR, "恢复理由不能为空");
        ThrowUtils.throwIf(reason.length() > 500, ErrorCode.PARAMS_ERROR, "恢复理由过长");
        ResourceIsolation active = this.lambdaQuery()
                .eq(ResourceIsolation::getResourceType, resourceType)
                .eq(ResourceIsolation::getResourceId, resourceId)
                .eq(ResourceIsolation::getStatus, ResourceIsolationStatusEnum.QUARANTINED.getValue())
                .one();
        ThrowUtils.throwIf(active == null, ErrorCode.NOT_FOUND_ERROR, "资源未处于隔离状态");

        ResourceIsolation update = new ResourceIsolation();
        update.setId(active.getId());
        update.setStatus(ResourceIsolationStatusEnum.RELEASED.getValue());
        // 置空唯一键，允许之后再次隔离；已恢复记录保留操作者与理由
        update.setActiveKey(null);
        update.setReleaseOperatorId(operator.getId());
        update.setReleaseReason(reason);
        update.setReleaseTime(new Date());
        boolean updated = this.lambdaUpdate()
                .eq(ResourceIsolation::getId, active.getId())
                .eq(ResourceIsolation::getStatus, ResourceIsolationStatusEnum.QUARANTINED.getValue())
                .set(ResourceIsolation::getStatus, update.getStatus())
                .set(ResourceIsolation::getActiveKey, null)
                .set(ResourceIsolation::getReleaseOperatorId, operator.getId())
                .set(ResourceIsolation::getReleaseReason, reason)
                .set(ResourceIsolation::getReleaseTime, update.getReleaseTime())
                .update();
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "恢复记录更新失败");
        log.info("资源已恢复: type={}, id={}, operator={}", resourceType, resourceId, operator.getId());
        active.setStatus(update.getStatus());
        active.setActiveKey(null);
        active.setReleaseOperatorId(update.getReleaseOperatorId());
        active.setReleaseReason(update.getReleaseReason());
        active.setReleaseTime(update.getReleaseTime());
        return active;
    }

    @Override
    public List<ResourceIsolation> history(String resourceType, Long resourceId) {
        return this.lambdaQuery()
                .eq(ResourceIsolation::getResourceType, resourceType)
                .eq(ResourceIsolation::getResourceId, resourceId)
                .orderByDesc(ResourceIsolation::getId)
                .list();
    }

    /**
     * 隔离与恢复使用独立的处置权限：平台管理员、资源所有者都不自动获得该能力。
     */
    private void requireModerator(User operator) {
        ThrowUtils.throwIf(operator == null || operator.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(!userService.isModerator(operator), ErrorCode.NO_AUTH_ERROR, "需要违规处置权限");
    }

    static String activeKey(String resourceType, Long resourceId) {
        return resourceType + ":" + resourceId;
    }
}
