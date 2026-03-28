package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.vo.AdminDashboardStatsVO;
import com.yuwen.visionspace.service.AdminStatsService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 管理端统计服务实现
 */
@Service
public class AdminStatsServiceImpl implements AdminStatsService {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Override
    public AdminDashboardStatsVO getDashboardStats() {
        AdminDashboardStatsVO stats = new AdminDashboardStatsVO();
        stats.setUserCount(userService.count());
        stats.setSpaceCount(spaceService.count());
        // 一条 SQL 同时获取图片总数和总大小
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("count(*) as cnt", "ifnull(sum(picSize), 0) as totalSize");
        Map<String, Object> result = pictureService.getBaseMapper().selectMaps(queryWrapper).get(0);
        stats.setPictureCount(((Number) result.get("cnt")).longValue());
        stats.setUsedSize(((Number) result.get("totalSize")).longValue());
        return stats;
    }
}
