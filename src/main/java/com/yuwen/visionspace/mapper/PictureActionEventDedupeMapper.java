package com.yuwen.visionspace.mapper;

import com.yuwen.visionspace.model.entity.PictureActionEventDedupe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 行为事件幂等台账 Mapper。
 *
 * <p>台账是可靠去重依据：事件载荷按保留期清理之后，这里仍然留着事件幂等键，
 * 同一个事件重放不会重新变成一条待处理事件。
 *
 * @see PictureActionEventDedupe 幂等台账实体
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

    /**
     * 查询某个事件的受理时间。
     *
     * @param eventId 事件幂等键
     * @return 受理时间，不存在时为 null
     */
    LocalDateTime selectCreatedTime(@Param("eventId") String eventId);

    /**
     * 统计台账里的事件数。
     *
     * @return 事件数
     */
    long countEvidence();

    /**
     * 统计受理时间早于给定时间的台账条数。
     *
     * <p>只用于维护与核对：日常流程不会按保留期清理台账。
     *
     * @param threshold 时间阈值
     * @return 条数
     */
    long countCreatedBefore(@Param("threshold") LocalDateTime threshold);
}
