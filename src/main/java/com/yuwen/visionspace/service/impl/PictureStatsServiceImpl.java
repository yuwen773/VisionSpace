package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.mapper.PictureStatsMapper;
import com.yuwen.visionspace.model.entity.PictureStats;
import com.yuwen.visionspace.service.PictureStatsService;
import org.springframework.stereotype.Service;

@Service
public class PictureStatsServiceImpl extends ServiceImpl<PictureStatsMapper, PictureStats>
        implements PictureStatsService {

    @Override
    public PictureStats getByPictureId(Long pictureId) {
        return this.getOne(new QueryWrapper<PictureStats>().eq("pictureId", pictureId));
    }
}