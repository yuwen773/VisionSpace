package com.yuwen.visionspace.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.mapper.UserFeedbackMapper;
import com.yuwen.visionspace.model.dto.feedback.FeedbackAddRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackQueryRequest;
import com.yuwen.visionspace.model.dto.feedback.FeedbackUpdateStatusRequest;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.entity.UserFeedback;
import com.yuwen.visionspace.model.vo.FeedbackVO;
import com.yuwen.visionspace.service.FeedbackService;
import com.yuwen.visionspace.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;

@Slf4j
@Service
public class FeedbackServiceImpl implements FeedbackService {

    @Resource
    private UserFeedbackMapper feedbackMapper;

    @Resource
    private UserService userService;

    @Override
    public long addFeedback(FeedbackAddRequest request, Long userId) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getType() == null || request.getTitle() == null || request.getContent() == null,
                ErrorCode.PARAMS_ERROR, "参数不完整");
        ThrowUtils.throwIf(request.getTitle().length() > 50, ErrorCode.PARAMS_ERROR, "标题过长");
        ThrowUtils.throwIf(request.getContent().length() > 2000, ErrorCode.PARAMS_ERROR, "内容过长");

        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setType(request.getType());
        feedback.setTitle(request.getTitle());
        feedback.setContent(request.getContent());
        feedback.setStatus(0); // 默认待处理
        feedback.setCreateTime(new Date());
        if (request.getPictureUrls() != null && !request.getPictureUrls().isEmpty()) {
            feedback.setPictureUrls(JSONUtil.toJsonStr(request.getPictureUrls()));
        }

        boolean result = feedbackMapper.insert(feedback) > 0;
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return feedback.getId();
    }

    @Override
    public FeedbackVO getFeedbackById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        UserFeedback feedback = feedbackMapper.selectById(id);
        ThrowUtils.throwIf(feedback == null, ErrorCode.NOT_FOUND_ERROR);
        return getFeedbackVO(feedback);
    }

    @Override
    public Page<FeedbackVO> listFeedbackByPage(FeedbackQueryRequest queryRequest) {
        long current = queryRequest.getCurrent();
        long pageSize = queryRequest.getPageSize();
        Page<UserFeedback> page = new Page<>(current, pageSize);

        QueryWrapper<UserFeedback> queryWrapper = new QueryWrapper<>();
        if (queryRequest.getType() != null) {
            queryWrapper.eq("type", queryRequest.getType());
        }
        if (queryRequest.getStatus() != null) {
            queryWrapper.eq("status", queryRequest.getStatus());
        }
        queryWrapper.orderByDesc("createTime");

        Page<UserFeedback> feedbackPage = feedbackMapper.selectPage(page, queryWrapper);
        Page<FeedbackVO> voPage = new Page<>(current, pageSize, feedbackPage.getTotal());
        voPage.setRecords(feedbackPage.getRecords().stream().map(this::getFeedbackVO).toList());
        return voPage;
    }

    @Override
    public boolean updateFeedbackStatus(FeedbackUpdateStatusRequest request, Long handlerId) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        UserFeedback feedback = feedbackMapper.selectById(request.getId());
        ThrowUtils.throwIf(feedback == null, ErrorCode.NOT_FOUND_ERROR);

        feedback.setStatus(request.getStatus());
        feedback.setHandlerId(handlerId);
        feedback.setHandlerNote(request.getHandlerNote());
        if (request.getStatus() != null && request.getStatus() == 2) {
            feedback.setHandleTime(new Date());
        }
        return feedbackMapper.updateById(feedback) > 0;
    }

    @Override
    public FeedbackVO getFeedbackVO(UserFeedback feedback) {
        FeedbackVO vo = new FeedbackVO();
        BeanUtils.copyProperties(feedback, vo);

        // 转换图片URLs
        if (feedback.getPictureUrls() != null && !feedback.getPictureUrls().isEmpty()) {
            vo.setPictureUrls(JSONUtil.toList(feedback.getPictureUrls(), String.class));
        }

        // 设置用户信息
        if (feedback.getUserId() != null) {
            User user = userService.getById(feedback.getUserId());
            if (user != null) {
                vo.setUserVO(userService.getUserVO(user));
            }
        }

        // 设置处理人信息
        if (feedback.getHandlerId() != null) {
            User handler = userService.getById(feedback.getHandlerId());
            if (handler != null) {
                vo.setHandlerVO(userService.getUserVO(handler));
            }
        }

        return vo;
    }
}
