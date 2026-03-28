package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.model.vo.AdminDashboardStatsVO;
import com.yuwen.visionspace.service.AdminStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 管理端统计接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/stats")
public class AdminStatsController {

    @Resource
    private AdminStatsService adminStatsService;

    /**
     * 获取管理端仪表盘聚合统计数据（仅管理员）
     */
    @PostMapping("/dashboard")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AdminDashboardStatsVO> getDashboardStats() {
        AdminDashboardStatsVO stats = adminStatsService.getDashboardStats();
        return ResultUtils.success(stats);
    }
}
