package com.yuwen.visionspace.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.model.dto.feedback.FeedbackAddRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackQueryRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackUpdateStatusRequest;
import com.yuwen.visionspace.model.entity.UserFeedback;
import com.yuwen.visionspace.model.vo.FeedbackVO;
import java.util.List;

public interface FeedbackService {

    /**
     * 提交反馈
     */
    long addFeedback(FeedbackAddRequest request, Long userId);

    /**
     * 根据ID获取反馈详情
     */
    FeedbackVO getFeedbackById(Long id);

    /**
     * 分页查询反馈列表
     */
    Page<FeedbackVO> listFeedbackByPage(FeedbackQueryRequest queryRequest);

    /**
     * 更新反馈状态
     */
    boolean updateFeedbackStatus(FeedbackUpdateStatusRequest request, Long handlerId);

    /**
     * 将 Entity 转换为 VO
     */
    FeedbackVO getFeedbackVO(UserFeedback feedback);
}
