package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.outbound.OutboundDownloader;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存量地址的读取：服务端记录里保存的原地址不是可信目标，必须走与用户 URL 同一套出站判定。
 *
 * <p>这里的重点是两件事：读取确实交给下载器（而不是直接开连接），
 * 以及被拒绝与取不到对调用方表现一致——都只是「这张存量资源不可读」。
 */
class PictureStorageServiceLegacyReadTest {

    private static final String LEGACY_URL = "https://old.example.com/uploads/a.png";

    @Test
    void legacyAddressIsReadThroughOutboundDownloader() {
        byte[] content = "legacy-bytes".getBytes(StandardCharsets.UTF_8);
        OutboundDownloader downloader = mock(OutboundDownloader.class);
        when(downloader.readToMemory(eq(LEGACY_URL), anyLong(), any())).thenReturn(content);

        byte[] read = service(downloader).readLegacyObject(LEGACY_URL);

        assertEquals("legacy-bytes", new String(read, StandardCharsets.UTF_8));
        // 上限必须来自内容读取边界，不受用户 URL 的下载上限影响
        verify(downloader).readToMemory(LEGACY_URL, PictureStorageService.MAX_CONTENT_BYTES, null);
    }

    @Test
    void deniedLegacyAddressIsReportedAsUnavailableWithoutEchoingTheReason() {
        OutboundDownloader downloader = mock(OutboundDownloader.class);
        when(downloader.readToMemory(anyString(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.PARAMS_ERROR, "目标地址不允许"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(downloader).readLegacyObject(LEGACY_URL));

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), error.getCode());
        assertEquals("资源内容不可用", error.getMessage());
        // 拒绝原因不外泄：否则「哪些地址被拒绝」就成了探测内网的手段
        assertFalse(String.valueOf(error.getMessage()).contains("不允许"));
    }

    @Test
    void blankAddressIsRejectedBeforeAnyRequest() {
        OutboundDownloader downloader = mock(OutboundDownloader.class);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(downloader).readLegacyObject("  "));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), error.getCode());
        verify(downloader, never()).readToMemory(anyString(), anyLong(), any());
    }

    @Test
    void urlEntryReturnsTheSameContentAsAStream() throws IOException {
        byte[] content = {7, 8, 9};
        OutboundDownloader downloader = mock(OutboundDownloader.class);
        when(downloader.readToMemory(eq(LEGACY_URL), anyLong(), any())).thenReturn(content);

        try (InputStream stream = service(downloader).getObjectByUrl(LEGACY_URL)) {
            assertEquals(3, stream.readAllBytes().length);
        }
    }

    private PictureStorageService service(OutboundDownloader downloader) {
        PictureStorageService service = new PictureStorageService();
        ReflectionTestUtils.setField(service, "outboundDownloader", downloader);
        return service;
    }
}
