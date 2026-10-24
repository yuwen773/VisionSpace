package com.yuwen.visionspace.controller;

import com.lmax.disruptor.dsl.Disruptor;
import com.yuwen.visionspace.annotation.AuthCheck;
import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.constant.UserConstant;
import com.yuwen.visionspace.manager.websocket.monitor.PictureEditMonitor;
import com.yuwen.visionspace.model.vo.CollabMonitorVO;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 协作可观察性端点（仅管理员）：队列积压、拒绝、异常与租约状态可见。
 */
@RestController
@RequestMapping("/picture/collab")
public class PictureCollabMonitorController {

    @Resource
    private PictureEditMonitor monitor;

    @Resource
    @Lazy
    private Disruptor<?> pictureEditEventDisruptor;

    /**
     * 协作通道健康快照。
     */
    @GetMapping("/monitor")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<CollabMonitorVO> monitor() {
        long remaining;
        try {
            remaining = pictureEditEventDisruptor.getRingBuffer().remainingCapacity();
        } catch (Exception e) {
            remaining = -1;
        }
        return ResultUtils.success(CollabMonitorVO.from(monitor.snapshot(remaining)));
    }
}
