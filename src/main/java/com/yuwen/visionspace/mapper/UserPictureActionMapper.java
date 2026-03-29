package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.dto.PictureActionStatsDTO;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户图片行为 Mapper
 * 继承 MyBatis Plus 的 BaseMapper，获得基本的 CRUD 能力
 *
 * @see UserPictureAction 用户图片行为实体
 */
@Mapper
public interface UserPictureActionMapper extends BaseMapper<UserPictureAction> {

    /**
     * 按图片ID分组统计各行为数量
     * @param pictureIds 图片ID列表
     * @return 聚合统计列表
     */
    List<PictureActionStatsDTO> aggregateByPictureId(@Param("list") List<Long> pictureIds);

    /**
     * 查询有行为记录的图片ID列表
     * @return 图片ID列表
     */
    List<Long> selectPictureIdsWithActions();

    /**
     * 增量查询待聚合的 action 记录（未处理的）
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 聚合统计列表
     */
    List<PictureActionStatsDTO> selectStatsByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 标记指定时间范围内的 action 为已处理
     * @param startTime 开始时间
     * @param endTime 结束时间
     */
    void markAsProcessed(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 删除指定时间之前的 action 数据（清理）
     * @param threshold 时间阈值
     * @return 删除条数
     */
    int deleteOldActions(@Param("threshold") LocalDateTime threshold);
}
