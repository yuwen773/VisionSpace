package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.model.dto.file.UploadPictureResult;
import org.dromara.x.file.storage.core.FileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上传流程只写一份：校验、路径前缀、落盘结果定位与反馈附件限制都走同一条流程。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileUploadServiceTest {

    @Mock
    private PictureStorageService pictureStorageService;

    private FileUploadService service() {
        FileUploadService service = new FileUploadService();
        ReflectionTestUtils.setField(service, "pictureStorageService", pictureStorageService);
        return service;
    }

    private FileInfo storedInfo() {
        FileInfo info = new FileInfo();
        info.setUrl("https://cdn.example.com/prefix/a.png");
        info.setPlatform("minio");
        info.setBasePath("bucket/");
        info.setPath("prefix/");
        info.setFilename("a.png");
        info.setSize(3L);
        return info;
    }

    @Test
    void storeFileReturnsLocatorBuiltFromStorageReply() {
        when(pictureStorageService.putObject(any(File.class), anyString(), anyString()))
                .thenReturn(storedInfo());
        MockMultipartFile file = new MockMultipartFile("file", "My Photo.PNG",
                "image/png", new byte[]{1, 2, 3});

        StoredFile stored = service().storeFile(file, "avatar/", 2L * 1024 * 1024, List.of("png"));

        ArgumentCaptor<String> filename = ArgumentCaptor.forClass(String.class);
        verify(pictureStorageService).putObject(any(File.class), eq("avatar/"), filename.capture());
        // 后缀统一小写，落在唯一文件名上，不再沿用原始文件名
        assertTrue(filename.getValue().endsWith(".png"), filename.getValue());
        assertEquals("https://cdn.example.com/prefix/a.png", stored.url());
        assertEquals("minio", stored.platform());
        assertEquals("bucket/prefix/a.png", stored.objectKey());
        assertEquals(3L, stored.size());
    }

    @Test
    void oversizedFileIsRejectedBeforeStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "big.png",
                "image/png", new byte[3 * 1024 * 1024]);

        assertThrows(BusinessException.class,
                () -> service().storeFile(file, "avatar/", 2L * 1024 * 1024, List.of("png")));
        verify(pictureStorageService, never()).putObject(any(), anyString(), anyString());
    }

    @Test
    void unsupportedFormatIsRejectedBeforeStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", new byte[]{1});

        assertThrows(BusinessException.class,
                () -> service().storeFile(file, "avatar/", 2L * 1024 * 1024, List.of("png")));
        verify(pictureStorageService, never()).putObject(any(), anyString(), anyString());
    }

    @Test
    void feedbackAttachmentUsesOwnerScopedPrefix() {
        when(pictureStorageService.putObject(any(File.class), anyString(), anyString()))
                .thenReturn(storedInfo());

        UploadPictureResult result = service().uploadFeedbackAttachment(
                new MockMultipartFile("file", "shot.jpg", "image/jpeg", new byte[]{1, 2}), 42L);

        verify(pictureStorageService).putObject(any(File.class), eq("temp/feedback/42/"), anyString());
        assertEquals("minio", result.getPlatform());
        assertEquals("bucket/prefix/a.png", result.getStoragePath());
        assertEquals("https://cdn.example.com/prefix/a.png", result.getUrl());
        assertEquals(3L, result.getPicSize());
        assertTrue(result.getPicName().endsWith(".jpg"), result.getPicName());
    }

    @Test
    void feedbackAttachmentKeepsFiveMegabyteLimit() {
        MockMultipartFile file = new MockMultipartFile("file", "big.png",
                "image/png", new byte[6 * 1024 * 1024]);

        assertThrows(BusinessException.class, () -> service().uploadFeedbackAttachment(file, 42L));
        verify(pictureStorageService, never()).putObject(any(), anyString(), anyString());
    }
}
