package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.vo.AdminDashboardStatsVO;

/**
 * 管理端统计服务
 */
public interface AdminStatsService {

    /**
     * 获取管理端仪表盘聚合统计数据（仅管理员）
     *
     * @return
     */
    AdminDashboardStatsVO getDashboardStats();
}
