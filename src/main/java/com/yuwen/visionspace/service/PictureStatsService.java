package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuwen.visionspace.model.entity.PictureStats;

public interface PictureStatsService extends IService<PictureStats> {

    /**
     * 根据图片ID获取统计信息
     */
    PictureStats getByPictureId(Long pictureId);
}