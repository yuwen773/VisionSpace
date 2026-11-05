package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 上传平台钉住语义（#40 / D20）：上传任务开始时解析并固定平台，
 * 切换并发时在途上传按原平台完成；无默认平台时显式拒绝而非落到未知平台。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PictureStorageServicePlatformPinTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private UploadPretreatment pretreatment;

    private final File file;

    {
        try {
            file = File.createTempFile("pin-test", ".png");
            file.deleteOnExit();
        } catch (IOException e) {
            throw new IllegalStateException("temporary file unavailable", e);
        }
    }

    @BeforeEach
    void setUp() {
        when(fileStorageService.getFileStorageList()).thenReturn(new java.util.concurrent.CopyOnWriteArrayList<>());
        when(fileStorageService.of(file)).thenReturn(pretreatment);
        when(pretreatment.setPlatform(anyString())).thenReturn(pretreatment);
        // 子类 UploadPretreatment 覆写了 thumbnail(int, int)：返回类型是子类
        org.mockito.Mockito.doReturn(pretreatment).when(pretreatment).thumbnail(256, 256);
        when(pretreatment.setPath(anyString())).thenReturn(pretreatment);
        when(pretreatment.setSaveFilename(anyString())).thenReturn(pretreatment);
        when(pretreatment.upload()).thenReturn(new FileInfo());
    }

    @Test
    void uploadPinsPlatformResolvedAtTaskStart() {
        PictureStorageService service = serviceWithDefaultPlatform("minio");
        service.putObject(file, "prefix/", "name.png");
        verify(fileStorageService.of(file)).setPlatform("minio");
    }

    @Test
    void pictureUploadAlsoPinsPlatform() {
        PictureStorageService service = serviceWithDefaultPlatform("oss");
        service.putPictureObject(file, "prefix/", "name.png");
        org.mockito.Mockito.verify(pretreatment).setPlatform("oss");
    }

    @Test
    void missingDefaultPlatformIsRejectedExplicitly() {
        PictureStorageService service = serviceWithDefaultPlatform(null);
        assertThrows(BusinessException.class, () -> service.putObject(file, "prefix/", "name.png"));
        assertThrows(BusinessException.class, () -> service.putPictureObject(file, "prefix/", "name.png"));
        verifyNoInteractions(pretreatment);
    }

    @Test
    void blankDefaultPlatformIsRejectedExplicitly() {
        PictureStorageService service = serviceWithDefaultPlatform("  ");
        assertThrows(BusinessException.class, () -> service.putObject(file, "prefix/", "name.png"));
    }

    private PictureStorageService serviceWithDefaultPlatform(String platform) {
        PictureStorageService service = new PictureStorageService();
        ReflectionTestUtils.setField(service, "fileStorageService", fileStorageService);
        FileStorageProperties properties = new FileStorageProperties();
        properties.setDefaultPlatform(platform);
        when(fileStorageService.getProperties()).thenReturn(properties);
        return service;
    }
}
