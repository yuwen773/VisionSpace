package com.yuwen.visionspace.service.impl;

import cn.hutool.core.io.FileUtil;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.enums.PictureFilePurposeEnum;
import com.yuwen.visionspace.model.enums.PictureFileStatusEnum;
import com.yuwen.visionspace.service.PictureFileService;
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

    @Resource
    private PictureFileService pictureFileService;

    @Async("picturePreviewExecutor")
    @Override
    public void generateAndUpdatePreview(Long pictureId, String picUrl) {
        if (pictureId == null || picUrl == null || picUrl.trim().isEmpty()) {
            return;
        }
        try {
            log.info(Thread.currentThread().getName(),"start generate preview picture......");
            Picture picture = pictureMapper.selectById(pictureId);
            if (picture == null) {
                return;
            }
            PreviewObject preview = generatePreview(picUrl);
            if (preview == null) {
                return;
            }
            // 预览图也是受保护内容：登记归属后，内容入口才能按同一规则鉴权
            PictureFile previewFile = new PictureFile();
            previewFile.setOwnerUserId(picture.getUserId());
            previewFile.setSpaceId(picture.getSpaceId());
            previewFile.setPictureId(pictureId);
            previewFile.setPurpose(PictureFilePurposeEnum.GALLERY.getValue());
            previewFile.setKind(PictureFileService.KIND_PREVIEW);
            previewFile.setPlatform(preview.platform());
            previewFile.setObjectKey(preview.objectKey());
            previewFile.setUrl(preview.url());
            previewFile.setFileSize(preview.size());
            previewFile.setStatus(PictureFileStatusEnum.ACTIVE.getValue());
            pictureFileService.save(previewFile);

            Picture updatePicture = new Picture();
            updatePicture.setId(pictureId);
            updatePicture.setPreviewUrl(preview.url());
            pictureMapper.updateById(updatePicture);
        } catch (Exception e) {
            log.error(Thread.currentThread().getName(),"generate preview failed, pictureId={}, picUrl={}", pictureId, picUrl, e);
        }
    }

    private static final String PREVIEW_FORMAT = "jpg";
    private static final String PREVIEW_FILENAME_PREFIX = "preview_";
    // TODO 升级到 Java 21+ 后改为 "webp"

    /**
     * 预览对象的上传结果。
     */
    private record PreviewObject(String url, String objectKey, String platform, Long size) {
    }

    private PreviewObject generatePreview(String picUrl) throws IOException, URISyntaxException {
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
            return new PreviewObject(previewFileInfo.getUrl(),
                    PictureStorageService.objectKey(previewFileInfo),
                    previewFileInfo.getPlatform(),
                    previewFileInfo.getSize());
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
