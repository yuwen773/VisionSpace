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
     * <p>键冲突时抛唯一约束异常，调用方据此判定为重复事件，不再写事件载荷。
     *
     * @param eventId 事件幂等键
     * @return 写入条数
     */
    int insertEvidence(@Param("eventId") String eventId);
}
