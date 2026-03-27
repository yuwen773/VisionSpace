package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import org.dromara.x.file.storage.core.FileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PicturePreviewServiceImplTest {

    @Mock
    private PictureStorageService pictureStorageService;

    @Mock
    private PictureMapper pictureMapper;

    @InjectMocks
    private PicturePreviewServiceImpl picturePreviewService;

    @Test
    void generateAndUpdatePreviewShouldPersistGeneratedPreviewUrl() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 1200, 800);
        graphics.dispose();
        ImageIO.write(image, "png", outputStream);

        FileInfo previewFileInfo = new FileInfo();
        previewFileInfo.setUrl("https://cdn.example.com/public/1/demo_preview.webp");

        when(pictureStorageService.getObject("/public/1/demo.png"))
                .thenReturn(new ByteArrayInputStream(outputStream.toByteArray()));
        when(pictureStorageService.putObject(any(File.class), eq("/public/1/demo_preview.webp")))
                .thenReturn(previewFileInfo);

        picturePreviewService.generateAndUpdatePreview(123L, "/public/1/demo.png");

        ArgumentCaptor<Picture> pictureCaptor = ArgumentCaptor.forClass(Picture.class);
        verify(pictureMapper).updateById(pictureCaptor.capture());
        Picture updatedPicture = pictureCaptor.getValue();
        assertNotNull(updatedPicture);
        assertEquals(123L, updatedPicture.getId());
        assertEquals("https://cdn.example.com/public/1/demo_preview.webp", updatedPicture.getPreviewUrl());
    }
}
