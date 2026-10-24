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
     * 锁定读判定隔离状态：读到的是最新已提交状态，并锁住相关行。
     *
     * <p>替换与删除在事务里用它复核隔离状态：锁定读不会读到事务开始时的旧快照，
     * 因此隔离在事务开始之后生效的，也能被看到——这是"隔离生效后原内容不被销毁"
     * 得以成立的另一半保证（另一半是双方共用同一把资源行锁）。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 id
     * @return 是否处于隔离状态
     */
    boolean isQuarantinedForUpdate(String resourceType, Long resourceId);

    /**
     * 在查询条件里排除隔离中的资源。
     *
     * <p>用子查询按库实时判定，隔离与恢复立即对所有实例的列表查询生效，
     * 不依赖进程内状态，也不会因为隔离记录变多而让参数无限增长。
     *
     * <p>实现必须用 {@code NOT IN} 而不是 {@code NOT EXISTS}：写成 NOT EXISTS 时
     * 外层列会被子查询里的 {@code resource_isolation.resourceId} 遮蔽，条件恒假，
     * 隔离中的资源反而全部返回。
     *
     * @param wrapper      目标查询条件
     * @param column       资源 id 列名，调用方只能传常量列名
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

    /**
     * 当前处于隔离状态的资源，按处置时间倒序。
     */
    List<ResourceIsolation> listQuarantined();
}
