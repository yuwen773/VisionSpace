package com.yuwen.visionspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuwen.visionspace.model.dto.PictureActionStatsDTO;
import com.yuwen.visionspace.model.entity.PictureStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 图片统计 Mapper
 *
 * <p>统计行以 {@code pictureId} 为唯一关联键：{@code id} 只是统计行自己的主键，
 * 与图片标识无关。
 */
@Mapper
public interface PictureStatsMapper extends BaseMapper<PictureStats> {

    /**
     * 按图片关联键原子累加统计。
     *
     * <p>已存在的统计行在原值上累加，不会被当成新增行重置，也不会撞唯一约束；
     * 尚不存在的统计行才插入一行初始值。
     *
     * @param stats 本次要累加的增量
     * @return 影响行数
     */
    int upsertIncrement(@Param("stats") PictureActionStatsDTO stats);
}
