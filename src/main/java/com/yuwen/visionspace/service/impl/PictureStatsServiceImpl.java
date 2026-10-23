package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.mapper.PictureStatsMapper;
import com.yuwen.visionspace.model.entity.PictureStats;
import com.yuwen.visionspace.service.PictureStatsService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PictureStatsServiceImpl extends ServiceImpl<PictureStatsMapper, PictureStats>
        implements PictureStatsService {

    @Override
    public PictureStats getByPictureId(Long pictureId) {
        return this.getOne(new QueryWrapper<PictureStats>().eq("pictureId", pictureId));
    }

    @Override
    public List<PictureStats> listByPictureIds(Collection<Long> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return Collections.emptyList();
        }
        return this.list(new QueryWrapper<PictureStats>().in("pictureId", pictureIds));
    }

    @Override
    public Map<Long, PictureStats> mapByPictureIds(Collection<Long> pictureIds) {
        return listByPictureIds(pictureIds).stream()
                .collect(Collectors.toMap(PictureStats::getPictureId, stats -> stats, (first, second) -> first));
    }
}
