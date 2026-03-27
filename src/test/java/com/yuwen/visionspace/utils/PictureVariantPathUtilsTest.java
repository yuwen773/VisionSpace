package com.yuwen.visionspace.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PictureVariantPathUtilsTest {

    @Test
    void buildPreviewPathShouldAppendPreviewSuffixBeforeExtension() {
        String previewPath = PictureVariantPathUtils.buildPreviewPath("/public/1/2026-03-27_demo.jpg");

        assertEquals("/public/1/2026-03-27_demo_preview.webp", previewPath);
    }
}
