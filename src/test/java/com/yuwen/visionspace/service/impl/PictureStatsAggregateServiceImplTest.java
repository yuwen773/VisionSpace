package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.mapper.UserPictureActionMapper;
import com.yuwen.visionspace.model.dto.PictureActionStatsDTO;
import com.yuwen.visionspace.model.entity.Picture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PictureStatsAggregateServiceImplTest {

    @Mock
    private UserPictureActionMapper actionMapper;

    @Mock
    private PictureMapper pictureMapper;

    @InjectMocks
    private PictureStatsAggregateServiceImpl service;

    @Test
    void aggregateActionStats_noData_shouldReturnEarly() {
        // given
        when(actionMapper.selectPictureIdsWithActions()).thenReturn(Collections.emptyList());

        // when
        service.aggregateActionStats();

        // then
        verify(actionMapper).selectPictureIdsWithActions();
        verifyNoInteractions(pictureMapper);
    }

    @Test
    void aggregateActionStats_withData_shouldUpdatePictures() {
        // given
        Long pictureId = 1L;
        when(actionMapper.selectPictureIdsWithActions()).thenReturn(Arrays.asList(pictureId));

        PictureActionStatsDTO stats = new PictureActionStatsDTO();
        stats.setPictureId(pictureId);
        stats.setImpressionCount(100L);
        stats.setClickCount(10L);
        stats.setViewCount(50L);
        stats.setLikeCount(5L);
        stats.setCollectCount(2L);
        stats.setDownloadCount(1L);
        stats.setShareCount(1L);
        when(actionMapper.aggregateByPictureId()).thenReturn(Arrays.asList(stats));

        Picture picture = new Picture();
        picture.setId(pictureId);
        picture.setViewCount(0L);
        picture.setLikeCount(0L);
        picture.setCollectCount(0L);
        picture.setDownloadCount(0L);
        picture.setShareCount(0L);
        picture.setImpressionCount(0L);
        picture.setCtr(BigDecimal.ZERO);
        when(pictureMapper.selectList(any())).thenReturn(Arrays.asList(picture));

        // when
        service.aggregateActionStats();

        // then
        verify(pictureMapper).updateById(picture);
        assertEquals(50L, picture.getViewCount());
        assertEquals(5L, picture.getLikeCount());
        assertEquals(2L, picture.getCollectCount());
        assertEquals(1L, picture.getDownloadCount());
        assertEquals(1L, picture.getShareCount());
        assertEquals(100L, picture.getImpressionCount());
        // CTR = 10/100 = 0.1
        assertEquals(new BigDecimal("0.100000"), picture.getCtr());
    }

    @Test
    void aggregateActionStats_zeroImpression_shouldSetCtrToZero() {
        // given
        Long pictureId = 2L;
        when(actionMapper.selectPictureIdsWithActions()).thenReturn(Arrays.asList(pictureId));

        PictureActionStatsDTO stats = new PictureActionStatsDTO();
        stats.setPictureId(pictureId);
        stats.setImpressionCount(0L);
        stats.setClickCount(0L);
        when(actionMapper.aggregateByPictureId()).thenReturn(Arrays.asList(stats));

        Picture picture = new Picture();
        picture.setId(pictureId);
        picture.setImpressionCount(0L);
        picture.setCtr(BigDecimal.ZERO);
        when(pictureMapper.selectList(any())).thenReturn(Arrays.asList(picture));

        // when
        service.aggregateActionStats();

        // then
        verify(pictureMapper).updateById(picture);
        assertEquals(BigDecimal.ZERO, picture.getCtr());
    }
}
