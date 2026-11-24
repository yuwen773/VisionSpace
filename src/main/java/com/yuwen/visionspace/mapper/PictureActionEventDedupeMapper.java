package com.yuwen.visionspace.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 行为事件幂等台账 Mapper。
 *
 * <p>事件载荷按保留期清理之后，这里仍然留着事件幂等键，
 * 同一个事件重放仍被台账挡住，不会重新变成一条待处理事件。
 */
@Mapper
public interface PictureActionEventDedupeMapper {

    /**
     * 登记一次事件受理。
     *
     * <p>键冲突时 {@code ON CONFLICT DO NOTHING}：影响行数 1 表示首次受理，0 表示
     * 同一事件已经受理过。不抛唯一约束异常——PostgreSQL 中抛错会让当前事务
     * 进入 aborted 状态，调用方无法再在同一事务里继续写。
     *
     * @param eventId 事件幂等键
     * @return 写入条数（1 首次，0 重复）
     */
    int insertEvidence(@Param("eventId") String eventId);
}
