package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.mapper.PictureIndexTaskMapper;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureIndexTask;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.VlCaptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 索引同步：以数据库当前状态为准，覆盖写而不是先删后写，失败留下可重试的待办。
 */
@ExtendWith(MockitoExtension.class)
class PictureEmbeddingServiceImplTest {

    private static final long PICTURE_ID = 7L;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private PictureService pictureService;

    @Mock
    private PictureMapper pictureMapper;

    @Mock
    private VlCaptionService vlCaptionService;

    @Mock
    private ResourceIsolationService resourceIsolationService;

    @Mock
    private PictureIndexTaskMapper taskMapper;

    @InjectMocks
    private PictureEmbeddingServiceImpl service;

    @Test
    void approvedPublicPictureIsUpsertedWithoutDeletingFirst() {
        givenPicture(approved("科技感蓝色背景"));
        when(vlCaptionService.generateRichCaption(anyString())).thenReturn("蓝色渐变");

        service.syncPictureIndexNow(PICTURE_ID);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertEquals("picture_7", captor.getValue().get(0).getId());
        // 覆盖写：先删后写在写失败时会留下空洞——旧内容没了，新内容也没进去
        verify(vectorStore, never()).delete(org.mockito.ArgumentMatchers.<String>anyList());
    }

    @Test
    void rejectedPictureIsRemovedFromTheIndex() {
        givenPicture(rejected());

        service.syncPictureIndexNow(PICTURE_ID);

        verify(vectorStore).delete(List.of("picture_7"));
        verify(vectorStore, never()).add(any());
    }

    @Test
    void deletedPictureIsRemovedFromTheIndex() {
        when(pictureService.getById(PICTURE_ID)).thenReturn(null);

        service.syncPictureIndexNow(PICTURE_ID);

        verify(vectorStore).delete(List.of("picture_7"));
        verify(vectorStore, never()).add(any());
    }

    @Test
    void quarantinedPictureIsRemovedFromTheIndex() {
        givenPicture(approved("科技感蓝色背景"));
        when(resourceIsolationService.isQuarantined(ResourceTypeEnum.PICTURE.getValue(), PICTURE_ID))
                .thenReturn(true);

        service.syncPictureIndexNow(PICTURE_ID);

        verify(vectorStore).delete(List.of("picture_7"));
        verify(vectorStore, never()).add(any());
    }

    @Test
    void pictureWithoutIndexableTextIsRemovedFromTheIndex() {
        Picture picture = approved(null);
        picture.setIntroduction(null);
        picture.setCategory(null);
        picture.setTags(null);
        givenPicture(picture);
        when(vlCaptionService.generateRichCaption(anyString())).thenReturn(null);

        service.syncPictureIndexNow(PICTURE_ID);

        // 没有可索引内容就不落库；可能残留的旧文档要移出去
        verify(vectorStore).delete(List.of("picture_7"));
        verify(vectorStore, never()).add(any());
    }

    @Test
    void failedWriteKeepsThePreviousDocumentAndRecordsRetry() {
        givenPicture(approved("科技感蓝色背景"));
        when(vlCaptionService.generateRichCaption(anyString())).thenReturn("蓝色渐变");
        org.mockito.Mockito.doThrow(new IllegalStateException("向量库不可用"))
                .when(vectorStore).add(any());

        service.syncPictureIndexNow(PICTURE_ID);

        // 写失败时不能先删：删了就等于把还能用的旧内容也弄丢了
        verify(vectorStore, never()).delete(org.mockito.ArgumentMatchers.<String>anyList());
        verify(taskMapper).recordFailure(eq(PICTURE_ID), eq("UPSERT"),
                eq(PictureIndexTask.STATUS_FAILED), eq(1), anyString(), any(Date.class));
    }

    @Test
    void successfulSyncClearsThePendingRetry() {
        givenPicture(approved("科技感蓝色背景"));
        when(vlCaptionService.generateRichCaption(anyString())).thenReturn("蓝色渐变");

        service.syncPictureIndexNow(PICTURE_ID);

        verify(taskMapper).deleteByPictureId(PICTURE_ID);
    }

    @Test
    void outOfOrderSyncStillWritesTheCurrentDatabaseState() {
        // 第一次同步时数据库里的标题是旧的
        Picture stale = approved("旧标题");
        givenPicture(stale);
        when(vlCaptionService.generateRichCaption(anyString())).thenReturn("蓝色渐变");

        service.syncPictureIndexNow(PICTURE_ID);

        // 之后图片被编辑，数据库里的标题变了；迟到的同步任务重新读库，写入的必须是新标题
        Picture current = approved("新标题");
        givenPicture(current);
        service.syncPictureIndexNow(PICTURE_ID);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, org.mockito.Mockito.times(2)).add(captor.capture());
        List<Document> written = captor.getAllValues().stream().map(List::getFirst).toList();
        assertTrue(written.get(0).getText().contains("旧标题"), "第一次写入的是当时数据库里的内容");
        assertTrue(written.get(1).getText().contains("新标题"), "迟到的同步必须按当前状态写，而不是按旧快照写");
    }

    @Test
    void retryFailureKeepsCountingAttempts() {
        givenPicture(approved("科技感蓝色背景"));
        when(vlCaptionService.generateRichCaption(anyString())).thenReturn("蓝色渐变");
        org.mockito.Mockito.doThrow(new IllegalStateException("向量库不可用"))
                .when(vectorStore).add(any());
        when(taskMapper.selectOne(any())).thenReturn(taskWithAttempts(2));

        service.syncPictureIndexNow(PICTURE_ID);

        verify(taskMapper).recordFailure(eq(PICTURE_ID), eq("UPSERT"),
                eq(PictureIndexTask.STATUS_FAILED), eq(3), anyString(), any(Date.class));
    }

    private void givenPicture(Picture picture) {
        when(pictureService.getById(PICTURE_ID)).thenReturn(picture);
    }

    private Picture approved(String name) {
        Picture picture = new Picture();
        picture.setId(PICTURE_ID);
        picture.setName(name);
        picture.setSpaceId(null);
        picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        picture.setUrl("https://example.com/7.png");
        return picture;
    }

    private Picture rejected() {
        Picture picture = approved("被拒绝");
        picture.setReviewStatus(PictureReviewStatusEnum.REJECT.getValue());
        return picture;
    }

    private PictureIndexTask taskWithAttempts(int attempts) {
        PictureIndexTask task = new PictureIndexTask();
        task.setPictureId(PICTURE_ID);
        task.setAttempts(attempts);
        return task;
    }
}
