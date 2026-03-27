package com.yuwen.visionspace.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PicturePreviewUtilsTest {

    @Test
    void limitDimensionsShouldScaleDownLargeImageWithinMaxEdge() {
        int[] dimensions = PicturePreviewUtils.limitDimensions(4000, 2000, 1600);

        assertEquals(1600, dimensions[0]);
        assertEquals(800, dimensions[1]);
    }

    @Test
    void limitDimensionsShouldKeepSmallImageUnchanged() {
        int[] dimensions = PicturePreviewUtils.limitDimensions(800, 600, 1600);

        assertEquals(800, dimensions[0]);
        assertEquals(600, dimensions[1]);
    }
}
