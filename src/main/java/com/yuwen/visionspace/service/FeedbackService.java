package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.model.dto.feedback.FeedbackAddRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackQueryRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackUpdateStatusRequest;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.entity.UserFeedback;
import com.yuwen.visionspace.model.vo.FeedbackStatsVO;
import com.yuwen.visionspace.model.vo.FeedbackVO;

public interface FeedbackService {

    /**
     * 提交反馈。附件必须由同一用户上传且尚未绑定。
     *
     * @param request 反馈请求
     * @param userId  提交者，来自服务端会话
     */
    long addFeedback(FeedbackAddRequest request, Long userId);

    /**
     * 读取反馈记录，不存在时拒绝。
     */
    UserFeedback getRequiredFeedback(Long id);

    /**
     * 分页查询反馈列表（仅平台管理员）
     */
    Page<FeedbackVO> listFeedbackByPage(FeedbackQueryRequest queryRequest);

    /**
     * 按状态聚合反馈数量（仅平台管理员）。
     *
     * @param type 可选的反馈类型过滤；为 null 时统计全部类型
     */
    FeedbackStatsVO countByStatus(Integer type);

    /**
     * 更新反馈状态（仅平台管理员）
     */
    boolean updateFeedbackStatus(FeedbackUpdateStatusRequest request, Long handlerId);

    /**
     * 将 Entity 转换为最小化视图。
     *
     * @param feedback 反馈记录
     * @param viewer   当前查看者，用于决定哪些附件给出受控入口
     */
    FeedbackVO getFeedbackVO(UserFeedback feedback, User viewer);
}
