package com.yuwen.visionspace.manager.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 资源身份的读取分派：登记过平台的记录按平台与键读取，
 * 「平台为 legacy」的存量记录按原地址读取——这条兼容规则只写在存储边界里。
 */
class PictureStorageServiceRecordReadTest {

    @Test
    void recordedObjectIsReadByPlatformAndKey() {
        PictureStorageService service = spy(new PictureStorageService());
        byte[] content = {1, 2, 3};
        doReturn(content).when(service).readObject("minio", "bucket/temp/a.png");

        assertArrayEquals(content, service.readRecordedObject("minio", "bucket/temp/a.png"));
        verify(service, never()).readLegacyObject(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void legacyPlatformReadsTheObjectKeyAsAddress() {
        PictureStorageService service = spy(new PictureStorageService());
        byte[] content = {4, 5};
        String legacyUrl = "https://old.example.com/uploads/a.png";
        doReturn(content).when(service).readLegacyObject(legacyUrl);

        assertArrayEquals(content, service.readRecordedObject(PictureStorageService.LEGACY_PLATFORM, legacyUrl));
        verify(service, never()).readObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
