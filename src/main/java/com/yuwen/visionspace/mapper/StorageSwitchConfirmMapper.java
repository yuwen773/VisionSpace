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
     * 记录本实例对候选版本的确认（幂等：重复确认影响 0 行）。
     *
     * @return 1 表示本实例首次确认，0 表示已确认过
     */
    int insertIgnore(@Param("version") long version,
                     @Param("instanceId") String instanceId,
                     @Param("configId") Long configId);

    /**
     * 候选版本的确认实例数。
     */
    long countByVersion(@Param("version") long version);

    /**
     * 清空某版本的全部确认行（占位赢家在同事务内清流产尝试的残留行）。
     */
    int deleteByVersion(@Param("version") long version);
}
