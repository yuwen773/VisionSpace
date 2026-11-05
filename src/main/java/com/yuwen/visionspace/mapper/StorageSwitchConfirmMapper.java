package com.yuwen.visionspace.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 存储切换确认实例集合数据库操作。
 *
 * <p>确认集合取代次数计数器：INSERT IGNORE 的主键幂等保证同一实例
 * 对同一版本只计一次，晋升条件是集合大小达到期望实例数。
 */
@Mapper
public interface StorageSwitchConfirmMapper {

    /**
     * 条件确认插入（单条原子语句，插入与校验合一）：仅当状态行的
     * pendingVersion 与 pendingToken 都与本次尝试一致时插入。
     *
     * <p>版本号会被回滚后的重新发起复用，只校验版本号挡不住跨
     * abort/再占位存活的旧确认线程；pendingToken 由每次占位语句生成、
     * 必不相同，旧线程持旧令牌无法落行。
     *
     * @return 1 表示本实例首次确认，0 表示已确认过或占位已变化（非本次尝试）
     */
    int insertIfCurrent(@Param("version") long version,
                        @Param("instanceId") String instanceId,
                        @Param("configId") Long configId,
                        @Param("pendingToken") String pendingToken);

    /**
     * 候选版本的确认实例数。
     */
    long countByVersion(@Param("version") long version);

    /**
     * 清空某版本的全部确认行（占位赢家在同事务内清流产尝试的残留行）。
     */
    int deleteByVersion(@Param("version") long version);
}
