package com.yuwen.visionspace.model.vo;

import com.yuwen.visionspace.model.entity.Picture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PictureVOTest {

    @Test
    void objToVoShouldPreservePreviewUrl() {
        Picture picture = new Picture();
        picture.setPreviewUrl("https://cdn.example.com/public/1/demo_preview.webp");

        PictureVO pictureVO = PictureVO.objToVo(picture);

        assertEquals("https://cdn.example.com/public/1/demo_preview.webp", pictureVO.getPreviewUrl());
    }
}
