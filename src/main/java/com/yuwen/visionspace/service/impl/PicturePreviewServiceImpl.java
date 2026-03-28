package com.yuwen.visionspace.service.impl;

import cn.hutool.core.io.FileUtil;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.service.PicturePreviewService;
import com.yuwen.visionspace.utils.PicturePreviewUtils;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

@Service
@Slf4j
public class PicturePreviewServiceImpl implements PicturePreviewService {

    private static final int PREVIEW_MAX_EDGE = 1600;

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private PictureMapper pictureMapper;

    @Async("picturePreviewExecutor")
    @Override
    public void generateAndUpdatePreview(Long pictureId, String picUrl) {
        if (pictureId == null || picUrl == null || picUrl.trim().isEmpty()) {
            return;
        }
        try {
            log.info(Thread.currentThread().getName(),"start generate preview picture......");
            String previewUrl = generatePreview(picUrl);
            if (previewUrl == null || previewUrl.trim().isEmpty()) {
                return;
            }
            Picture updatePicture = new Picture();
            updatePicture.setId(pictureId);
            updatePicture.setPreviewUrl(previewUrl);
            pictureMapper.updateById(updatePicture);
        } catch (Exception e) {
            log.error(Thread.currentThread().getName(),"generate preview failed, pictureId={}, picUrl={}", pictureId, picUrl, e);
        }
    }

    private static final String PREVIEW_FORMAT = "jpg";
    private static final String PREVIEW_FILENAME_PREFIX = "preview_";
    // TODO 升级到 Java 21+ 后改为 "webp"

    private String generatePreview(String picUrl) throws IOException, URISyntaxException {
        String originalFilename = FileUtil.getName(picUrl);
        String previewFilename = PREVIEW_FILENAME_PREFIX + FileUtil.mainName(originalFilename) + "." + PREVIEW_FORMAT;
        File previewTempFile = File.createTempFile("picture-preview-", "." + PREVIEW_FORMAT);
        try (InputStream inputStream = pictureStorageService.getObjectByUrl(picUrl)) {
            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                throw new IOException("unsupported image content");
            }
            String path = new URI(picUrl).getPath();
            String uploadPathPrefix = path.substring(1, path.lastIndexOf("/"));

            int[] dimensions = PicturePreviewUtils.limitDimensions(
                    originalImage.getWidth(), originalImage.getHeight(), PREVIEW_MAX_EDGE);
            Thumbnails.of(originalImage)
                    .size(dimensions[0], dimensions[1])
                    .outputFormat(PREVIEW_FORMAT)
                    .toFile(previewTempFile);

            FileInfo previewFileInfo = pictureStorageService.putObject(
                    previewTempFile, "/" + uploadPathPrefix + "/", previewFilename);
            return previewFileInfo.getUrl();
        } finally {
            if (!previewTempFile.delete()) {
                log.warn(Thread.currentThread().getName(),"failed to delete preview temp file: {}", previewTempFile.getAbsolutePath());
            }
        }
    }

    /**
     * WebP 写入（Java 21+ 原生支持，暂未使用）
     */
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
