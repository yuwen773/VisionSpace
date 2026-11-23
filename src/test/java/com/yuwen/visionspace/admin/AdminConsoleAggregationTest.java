package com.yuwen.visionspace.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yuwen.visionspace.mapper.UserFeedbackMapper;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.UserFeedback;
import com.yuwen.visionspace.model.vo.FeedbackStatsVO;
import com.yuwen.visionspace.model.vo.SpaceVO;
import com.yuwen.visionspace.service.impl.FeedbackServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端聚合口径的单元测试。
 *
 * <p>不启动 Spring 上下文，也不依赖 MySQL / Redis：这里验证的是「页头数字来自全量聚合，
 * 而不是当前页」这一条业务规则本身。
 */
@ExtendWith(MockitoExtension.class)
class AdminConsoleAggregationTest {

    @Mock
    private UserFeedbackMapper feedbackMapper;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    @Test
    void statusCountsAreAggregatedAndSummed() {
        when(feedbackMapper.selectCount(any())).thenReturn(3L, 2L, 7L);

        FeedbackStatsVO stats = feedbackService.countByStatus(null);

        assertEquals(3L, stats.getPending());
        assertEquals(2L, stats.getProcessing());
        assertEquals(7L, stats.getResolved());
        assertEquals(12L, stats.getTotal());
        verify(feedbackMapper, times(3)).selectCount(any());
    }

    @Test
    void typeFilterIsAppliedToEveryStatusQuery() {
        when(feedbackMapper.selectCount(any())).thenReturn(1L);

        feedbackService.countByStatus(2);

        ArgumentCaptor<QueryWrapper<UserFeedback>> captor = wrapperCaptor();
        verify(feedbackMapper, times(3)).selectCount(captor.capture());
        List<QueryWrapper<UserFeedback>> wrappers = captor.getAllValues();
        assertEquals(3, wrappers.size());
        wrappers.forEach(wrapper -> {
            assertTrue(wrapper.getSqlSegment().contains("status"), "每次聚合都应带上状态条件");
            assertTrue(wrapper.getSqlSegment().contains("type"), "类型筛选应作用到每个状态");
        });
    }

    @Test
    void noTypeFilterLeavesTypeOutOfTheQueries() {
        when(feedbackMapper.selectCount(any())).thenReturn(0L);

        feedbackService.countByStatus(null);

        ArgumentCaptor<QueryWrapper<UserFeedback>> captor = wrapperCaptor();
        verify(feedbackMapper, times(3)).selectCount(captor.capture());
        captor.getAllValues().forEach(wrapper ->
                assertFalse(wrapper.getSqlSegment().contains("type"), "未筛选类型时不应拼上 type 条件"));
    }

    @Test
    void spaceStatusSurvivesTheEntityToViewConversion() {
        Space space = new Space();
        space.setId(1L);
        space.setSpaceName("禁用空间");
        space.setStatus(0);

        SpaceVO view = SpaceVO.objToVo(space);

        assertEquals(0, view.getStatus(), "列表状态列依赖 VO 携带真实 status");
        assertEquals("禁用空间", view.getSpaceName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<QueryWrapper<UserFeedback>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(QueryWrapper.class);
    }
}
