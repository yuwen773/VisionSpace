package com.yuwen.visionspace.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.manager.auth.SpaceAuthority;
import com.yuwen.visionspace.manager.lock.ResourceLockManager;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.mapper.SpaceMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import com.yuwen.visionspace.service.PictureFileService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.StorageCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant.PICTURE_DELETE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 删除生命周期：授权拒绝零副作用；记录清理失败时登记可重试任务。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PictureDeletionServiceImplTest {

    private static final long PICTURE_ID = 7L;
    private static final long SPACE_ID = 900L;
    private static final long OWNER = 1L;

    @Mock
    private PictureService pictureService;

    @Mock
    private PictureStorageService pictureStorageService;

    @Mock
    private PictureFileService pictureFileService;

    @Mock
    private ObjectProvider<PictureEmbeddingService> embeddingProvider;

    @Mock
    private SpaceAuthority spaceAuthority;

    @Mock
    private ResourceIsolationService resourceIsolationService;

    @Mock
    private ResourceLockManager resourceLockManager;

    @Mock
    private SpaceMapper spaceMapper;

    @Mock
    private PictureMapper pictureMapper;

    @Mock
    private StorageCleanupService storageCleanupService;

    @InjectMocks
    private PictureDeletionServiceImpl deletionService;

    private final User owner = user(OWNER);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deletionService, "transactionTemplate", new TransactionTemplate(NO_OP_TRANSACTIONS));
        when(embeddingProvider.getIfAvailable()).thenReturn(null);
    }

    @Test
    void deletesRecordedFilesAndReleasesSpaceSlot() {
        Picture picture = picture(SPACE_ID, 100L);
        PictureFile file = file();
        givenAuthorizedDeletion(picture);
        when(spaceMapper.releasePictureSlot(SPACE_ID, 100L)).thenReturn(1);
        when(pictureFileService.listActiveByPicture(PICTURE_ID)).thenReturn(List.of(file));
        when(pictureFileService.deleteFile(file)).thenReturn(true);

        deletionService.deletePicture(PICTURE_ID, owner);

        verify(pictureService).removeById(PICTURE_ID);
        verify(spaceMapper).releasePictureSlot(SPACE_ID, 100L);
        verify(pictureFileService).deleteFile(file);
        verifyNoInteractions(pictureStorageService);
        verifyNoInteractions(storageCleanupService);
    }

    @Test
    void deniedUserNeverTouchesStorageOrTransaction() {
        Picture picture = picture(SPACE_ID, 100L);
        when(pictureService.getById(PICTURE_ID)).thenReturn(picture);
        when(spaceAuthority.canOperatePicture(picture, owner, PICTURE_DELETE)).thenReturn(false);

        assertThrows(BusinessException.class, () -> deletionService.deletePicture(PICTURE_ID, owner));

        verify(pictureService, never()).removeById(any());
        verifyNoInteractions(resourceLockManager, pictureStorageService, storageCleanupService);
    }

    @Test
    void failedRecordedDeleteEnqueuesRetryableCleanup() {
        Picture picture = picture(SPACE_ID, 100L);
        PictureFile file = file();
        givenAuthorizedDeletion(picture);
        when(spaceMapper.releasePictureSlot(SPACE_ID, 100L)).thenReturn(1);
        when(pictureFileService.listActiveByPicture(PICTURE_ID)).thenReturn(List.of(file));
        when(pictureFileService.deleteFile(file)).thenReturn(false);

        deletionService.deletePicture(PICTURE_ID, owner);

        verify(storageCleanupService).enqueueDeletedPictureFile(eq(file), contains(Long.toString(PICTURE_ID)));
    }

    @Test
    void legacyObjectStillReferencedByAnotherRowIsKept() {
        Picture picture = picture(null, 0L);
        picture.setUrl("https://acceptance.invalid/legacy.png");
        givenAuthorizedDeletion(picture);
        when(pictureFileService.listActiveByPicture(PICTURE_ID)).thenReturn(List.of());
        LambdaQueryChainWrapper<Picture> query = mock(LambdaQueryChainWrapper.class);
        when(pictureService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.count()).thenReturn(2L);

        deletionService.deletePicture(PICTURE_ID, owner);

        verifyNoInteractions(pictureStorageService);
        verify(spaceMapper, never()).releasePictureSlot(any(), anyLong());
    }

    private void givenAuthorizedDeletion(Picture picture) {
        when(pictureService.getById(PICTURE_ID)).thenReturn(picture);
        when(spaceAuthority.canOperatePicture(picture, owner, PICTURE_DELETE)).thenReturn(true);
        when(resourceIsolationService.isQuarantined(
                ResourceTypeEnum.PICTURE.getValue(), PICTURE_ID)).thenReturn(false);
        when(resourceLockManager.lockResourceRow(
                ResourceTypeEnum.PICTURE.getValue(), PICTURE_ID)).thenReturn(true);
        when(resourceIsolationService.isQuarantinedForUpdate(
                ResourceTypeEnum.PICTURE.getValue(), PICTURE_ID)).thenReturn(false);
        when(pictureMapper.selectForUpdate(PICTURE_ID)).thenReturn(picture);
        when(pictureService.removeById(PICTURE_ID)).thenReturn(true);
    }

    private Picture picture(Long spaceId, Long size) {
        Picture picture = new Picture();
        picture.setId(PICTURE_ID);
        picture.setUserId(OWNER);
        picture.setSpaceId(spaceId);
        picture.setPicSize(size);
        return picture;
    }

    private PictureFile file() {
        PictureFile file = new PictureFile();
        file.setId(11L);
        file.setPictureId(PICTURE_ID);
        return file;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static final PlatformTransactionManager NO_OP_TRANSACTIONS = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    };
}
