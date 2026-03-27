package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import org.dromara.x.file.storage.core.Downloader;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PictureStorageServiceTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private Downloader downloader;

    @Mock
    private UploadPretreatment uploadPretreatment;

    @Mock
    private FileStorageProperties fileStorageProperties;

    @InjectMocks
    private PictureStorageService pictureStorageService;

    @Test
    void getObjectShouldDownloadByStoragePathWithoutFileRecorder() throws Exception {
        byte[] expectedBytes = "preview-bytes".getBytes("UTF-8");

        when(fileStorageService.getDefaultPlatform()).thenReturn("tencent-cos-1");
        when(fileStorageService.download(any(FileInfo.class))).thenReturn(downloader);
        when(downloader.bytes()).thenReturn(expectedBytes);

        try (InputStream inputStream = pictureStorageService.getObject("/public/1/demo.png")) {
            assertArrayEquals(expectedBytes, readAllBytes(inputStream));
        }

        ArgumentCaptor<FileInfo> fileInfoCaptor = ArgumentCaptor.forClass(FileInfo.class);
        verify(fileStorageService, never()).download(eq("/public/1/demo.png"));
        verify(fileStorageService).download(fileInfoCaptor.capture());
        assertEquals("tencent-cos-1", fileInfoCaptor.getValue().getPlatform());
        assertEquals("public/1/", fileInfoCaptor.getValue().getPath());
        assertEquals("demo.png", fileInfoCaptor.getValue().getFilename());
    }

    @Test
    void getObjectShouldWrapStorageFailures() {
        when(fileStorageService.getDefaultPlatform()).thenReturn("tencent-cos-1");
        when(fileStorageService.download(any(FileInfo.class))).thenThrow(new RuntimeException("boom"));

        assertThrows(BusinessException.class, () -> pictureStorageService.getObject("/public/1/demo.png"));
    }

    @Test
    void putObjectShouldSplitStorageKeyIntoPathAndFilename() {
        File file = new File("demo.png");
        FileInfo fileInfo = new FileInfo();

        when(fileStorageService.of(file)).thenReturn(uploadPretreatment);
        when(uploadPretreatment.setPath("public/1/")).thenReturn(uploadPretreatment);
        when(uploadPretreatment.setSaveFilename("demo.png")).thenReturn(uploadPretreatment);
        when(uploadPretreatment.upload()).thenReturn(fileInfo);

        pictureStorageService.putObject(file, "/public/1/demo.png");

        verify(uploadPretreatment).setPath("public/1/");
        verify(uploadPretreatment).setSaveFilename("demo.png");
    }

    @Test
    void putPictureObjectShouldSplitStorageKeyIntoPathAndFilename() {
        File file = new File("demo.png");
        FileInfo fileInfo = new FileInfo();

        when(fileStorageService.getFileStorageList()).thenReturn(new CopyOnWriteArrayList<>());
        when(fileStorageService.getProperties()).thenReturn(fileStorageProperties);
        when(fileStorageProperties.getDefaultPlatform()).thenReturn("tencent-cos-1");
        when(fileStorageService.of(file)).thenReturn(uploadPretreatment);
        when(uploadPretreatment.thumbnail(256, 256)).thenReturn(uploadPretreatment);
        when(uploadPretreatment.setPath("public/1/")).thenReturn(uploadPretreatment);
        when(uploadPretreatment.setSaveFilename("demo.png")).thenReturn(uploadPretreatment);
        when(uploadPretreatment.upload()).thenReturn(fileInfo);

        pictureStorageService.putPictureObject(file, "/public/1/demo.png");

        verify(uploadPretreatment).thumbnail(256, 256);
        verify(uploadPretreatment).setPath("public/1/");
        verify(uploadPretreatment).setSaveFilename("demo.png");
    }

    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[256];
        int length;
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }
}
