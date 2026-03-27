package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.service.PicturePreviewService;
import com.yuwen.visionspace.utils.PicturePreviewUtils;
import com.yuwen.visionspace.utils.PictureVariantPathUtils;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Service
@Slf4j
public class PicturePreviewServiceImpl implements PicturePreviewService {

    private static final int PREVIEW_MAX_EDGE = 1600;

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private PictureMapper pictureMapper;

    @Async
    @Override
    public void generateAndUpdatePreview(Long pictureId, String originalStoragePath) {
        if (pictureId == null || originalStoragePath == null || originalStoragePath.trim().isEmpty()) {
            return;
        }
        try {
            String previewUrl = generatePreview(originalStoragePath);
            if (previewUrl == null || previewUrl.trim().isEmpty()) {
                return;
            }
            Picture updatePicture = new Picture();
            updatePicture.setId(pictureId);
            updatePicture.setPreviewUrl(previewUrl);
            pictureMapper.updateById(updatePicture);
        } catch (Exception e) {
            log.error("generate preview failed, pictureId={}, storagePath={}", pictureId, originalStoragePath, e);
        }
    }

    private String generatePreview(String originalStoragePath) throws IOException {
        File previewTempFile = File.createTempFile("picture-preview-", ".webp");
        try (InputStream inputStream = pictureStorageService.getObject(originalStoragePath)) {
            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                throw new IOException("unsupported image content");
            }
            BufferedImage previewImage = resizeForPreview(originalImage);
            writeWebp(previewImage, previewTempFile);
            FileInfo previewFileInfo = pictureStorageService.putObject(
                    previewTempFile,
                    PictureVariantPathUtils.buildPreviewPath(originalStoragePath)
            );
            return previewFileInfo.getUrl();
        } finally {
            if (!previewTempFile.delete()) {
                log.warn("failed to delete preview temp file: {}", previewTempFile.getAbsolutePath());
            }
        }
    }

    private BufferedImage resizeForPreview(BufferedImage originalImage) throws IOException {
        int[] dimensions = PicturePreviewUtils.limitDimensions(
                originalImage.getWidth(),
                originalImage.getHeight(),
                PREVIEW_MAX_EDGE
        );
        if (dimensions[0] == originalImage.getWidth() && dimensions[1] == originalImage.getHeight()) {
            return originalImage;
        }
        return Thumbnails.of(originalImage)
                .size(dimensions[0], dimensions[1])
                .asBufferedImage();
    }

    private void writeWebp(BufferedImage image, File targetFile) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IOException("no webp writer available");
        }
        ImageWriter writer = writers.next();
        try (FileImageOutputStream outputStream = new FileImageOutputStream(targetFile)) {
            writer.setOutput(outputStream);
            writer.write(null, new IIOImage(image, null, null), null);
        } finally {
            writer.dispose();
        }
    }
}
