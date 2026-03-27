package com.yuwen.visionspace.manager.upload;

import org.dromara.x.file.storage.core.FileInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PictureUploadTemplateTest {

    @Test
    void resolveStoragePathShouldPreferActualStoredPathFromFileInfo() {
        FileInfo fileInfo = new FileInfo()
                .setPath("public/1/")
                .setFilename("demo.png");

        String storagePath = PictureUploadTemplate.resolveStoragePath(fileInfo, "/public/1/demo.png");

        assertEquals("public/1/demo.png", storagePath);
    }

    @Test
    void resolveStoragePathShouldFallbackWhenFileInfoIsIncomplete() {
        FileInfo fileInfo = new FileInfo();

        String storagePath = PictureUploadTemplate.resolveStoragePath(fileInfo, "/public/1/demo.png");

        assertEquals("public/1/demo.png", storagePath);
    }
}
