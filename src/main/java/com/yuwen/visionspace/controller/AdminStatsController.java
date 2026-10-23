package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.model.vo.AdminDashboardStatsVO;
import com.yuwen.visionspace.model.vo.PictureActionReportMetricsVO;
import com.yuwen.visionspace.model.vo.PictureStatsBacklogVO;
import com.yuwen.visionspace.service.AdminStatsService;
import com.yuwen.visionspace.service.PictureActionService;
import com.yuwen.visionspace.service.PictureStatsAggregateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 管理端统计接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/stats")
public class AdminStatsController {

    @Resource
    private AdminStatsService adminStatsService;

    @Resource
    private PictureStatsAggregateService pictureStatsAggregateService;

    @Resource
    private PictureActionService pictureActionService;

    /**
     * 获取管理端仪表盘聚合统计数据（仅管理员）
     */
    @PostMapping("/dashboard")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AdminDashboardStatsVO> getDashboardStats() {
        AdminDashboardStatsVO stats = adminStatsService.getDashboardStats();
        return ResultUtils.success(stats);
    }

    /**
     * 行为事件的待处理积压（仅管理员）。
     *
     * <p>只返回数量：待处理、已计入未完成、租约中的、反复失败的与目标缺失的，
     * 不含任何用户、图片或事件内容。
     */
    @PostMapping("/picture-stats/backlog")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureStatsBacklogVO> getPictureStatsBacklog() {
        return ResultUtils.success(pictureStatsAggregateService.getBacklog());
    }

    /**
     * 行为上报的受理与拒绝计数（仅管理员）。
     *
     * <p>只返回数量：新受理、幂等重复、被拒绝与被限流，不含任何用户或事件内容。
     */
    @PostMapping("/picture-action/metrics")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureActionReportMetricsVO> getPictureActionMetrics() {
        return ResultUtils.success(pictureActionService.getReportMetrics());
    }
}
