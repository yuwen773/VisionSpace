package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.entity.ResourceIsolation;
import com.yuwen.visionspace.model.entity.User;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 违规资源隔离与恢复。
 *
 * <p>隔离只改变可访问状态，不删除内容；每次内容读取与列表查询都重新判定，
 * 因此隔离与恢复在两个实例上同时生效。所有操作记录操作者、理由、时间与结果。
 */
public interface ResourceIsolationService extends IService<ResourceIsolation> {

    /**
     * 当前处于隔离状态的资源 id 集合。
     *
     * @param resourceType 资源类型
     * @param resourceIds  待判定的资源 id
     * @return 其中已隔离的资源 id
     */
    Set<Long> quarantinedIds(String resourceType, Collection<Long> resourceIds);

    /**
     * 资源当前是否处于隔离状态。每次调用都查库，不缓存。
     */
    boolean isQuarantined(String resourceType, Long resourceId);

    /**
     * 在查询条件里排除隔离中的资源。
     *
     * <p>用 {@code NOT EXISTS} 子查询而不是先查 id 再拼 {@code NOT IN}：
     * 列表查询需要跨实例生效，且不能因为隔离记录变多而让参数无限增长。
     *
     * @param wrapper      目标查询条件
     * @param column       资源 id 列名
     * @param resourceType 资源类型
     */
    void excludeQuarantined(QueryWrapper<?> wrapper, String column, String resourceType);

    /**
     * 隔离资源。要求操作者具备独立的处置权限，且理由必填。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 id
     * @param operator     操作者
     * @param reason       隔离理由
     * @return 新建的隔离记录
     */
    ResourceIsolation quarantine(String resourceType, Long resourceId, User operator, String reason);

    /**
     * 恢复正常资源。要求操作者具备独立的处置权限，且理由必填。
     *
     * @return 更新后的隔离记录
     */
    ResourceIsolation release(String resourceType, Long resourceId, User operator, String reason);

    /**
     * 资源的隔离历史，按时间倒序（含已恢复记录）。
     */
    List<ResourceIsolation> history(String resourceType, Long resourceId);
}
