package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.entity.StorageSwitchState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 存储切换版本状态数据库操作。
 *
 * <p>全部状态迁移使用单语句原子更新（WHERE 条件保证跨实例只成功一次），
 * 与 #39 兑换码核销同一套并发纪律。
 */
@Mapper
public interface StorageSwitchStateMapper extends BaseMapper<StorageSwitchState> {

    /**
     * 保证单行存在（幂等）。
     *
     * @return 是否新插入
     */
    int ensureRow();

    /**
     * 原子占位一次切换：把候选版本记为 activeVersion + 1。
     *
     * <p>WHERE pendingVersion IS NULL 保证两个实例同时发起切换时
     * 只有一个成功；落败方影响 0 行。
     *
     * @return 1 表示本实例占位成功，0 表示已有切换进行中
     */
    int claimSwitch(@Param("targetConfigId") Long targetConfigId,
                    @Param("targetPlatform") String targetPlatform,
                    @Param("expectedInstances") int expectedInstances);

    /**
     * 本实例确认候选版本生效，确认数原子加一。
     *
     * @return 影响行数；0 表示版本已被完成或回滚
     */
    int incrementConfirm(@Param("version") long version);

    /**
     * 确认数达到期望后晋升版本：pending 提升为 active。
     *
     * <p>调用方必须在同一事务里迁移 storage_config.isActive，
     * 保证「持久激活标记」与「生效版本」一起变更。
     *
     * @return 1 表示本实例完成晋升，0 表示已被其他实例完成或已回滚
     */
    int completeSwitch(@Param("version") long version);

    /**
     * 回滚进行中的切换：清空 pending，activeVersion 保持不变。
     *
     * @return 1 表示本实例回滚成功，0 表示没有进行中的切换或已被并发处理
     */
    int abortSwitch();
}
