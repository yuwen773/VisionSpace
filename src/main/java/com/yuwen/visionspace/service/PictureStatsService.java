package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.entity.PictureStats;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PictureStatsService extends IService<PictureStats> {

    /**
     * 根据图片ID获取统计信息
     */
    PictureStats getByPictureId(Long pictureId);

    /**
     * 按图片关联键批量查询统计。
     *
     * <p>{@code picture_stats.id} 是统计行自己的主键，与 {@code pictureId} 无关；
     * 任何按图片集合取统计的地方都必须走这里，不能使用按主键的 {@code listByIds}。
     *
     * @param pictureIds 图片 ID 集合，允许为空
     * @return 命中的统计行（可能少于入参数量）
     */
    List<PictureStats> listByPictureIds(Collection<Long> pictureIds);

    /**
     * 按图片关联键批量查询统计并建立 pictureId -> 统计 的映射。
     *
     * @param pictureIds 图片 ID 集合，允许为空
     * @return pictureId 到统计行的映射，未命中不包含键
     */
    Map<Long, PictureStats> mapByPictureIds(Collection<Long> pictureIds);
}
