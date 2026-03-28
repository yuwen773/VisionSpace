package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.UserPictureAction;
import com.yuwen.visionspace.model.vo.AdminDashboardStatsVO;
import com.yuwen.visionspace.service.AdminStatsService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.UserService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    @Resource
    private UserPictureActionMapper userPictureActionMapper;

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

        // 今日推荐统计
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 今日曝光量 (source=0 HOME_RECOMMEND, actionType=0 impression)
        QueryWrapper<UserPictureAction> impressionWrapper = new QueryWrapper<>();
        impressionWrapper.eq("source", 0)
                .eq("actionType", 0)
                .between("createdTime", todayStart, todayEnd);
        Long todayImpression = userPictureActionMapper.selectCount(impressionWrapper);
        stats.setTodayImpressionCount(todayImpression);

        // 今日点击量 (source=0 HOME_RECOMMEND, actionType=1 click)
        QueryWrapper<UserPictureAction> clickWrapper = new QueryWrapper<>();
        clickWrapper.eq("source", 0)
                .eq("actionType", 1)
                .between("createdTime", todayStart, todayEnd);
        Long todayClick = userPictureActionMapper.selectCount(clickWrapper);
        stats.setTodayClickCount(todayClick);

        // CTR = click / impression
        if (todayImpression != null && todayImpression > 0 && todayClick != null) {
            double ctr = (double) todayClick / todayImpression * 100;
            stats.setTodayCtr(Math.round(ctr * 100.0) / 100.0);
        } else {
            stats.setTodayCtr(0.0);
        }

        // 今日收藏量 (source=0 HOME_RECOMMEND, actionType=4 collect)
        QueryWrapper<UserPictureAction> collectWrapper = new QueryWrapper<>();
        collectWrapper.eq("source", 0)
                .eq("actionType", 4)
                .between("createdTime", todayStart, todayEnd);
        Long todayCollect = userPictureActionMapper.selectCount(collectWrapper);
        stats.setTodayCollectCount(todayCollect);

        return stats;
    }
}
