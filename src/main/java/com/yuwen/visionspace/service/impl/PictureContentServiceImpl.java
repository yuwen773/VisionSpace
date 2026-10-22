package com.yuwen.visionspace.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.auth.PictureContentAuthManager;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.vo.PictureVO;
import com.yuwen.visionspace.service.PictureContentService;
import com.yuwen.visionspace.service.PictureFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PictureContentServiceImpl implements PictureContentService {

    private static final String CONTENT_PATH = "/picture/content/";

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private PictureFileService pictureFileService;

    @Resource
    private PictureContentAuthManager pictureContentAuthManager;

    @Resource
    private PictureStorageService pictureStorageService;

    @Override
    public Content loadContent(Long pictureId, ContentKind kind, User viewer, boolean download) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureMapper.selectById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 每次新请求都按当前角色判定，成员移除或降权立即生效
        ThrowUtils.throwIf(!pictureContentAuthManager.canReadContent(picture, viewer),
                ErrorCode.NO_AUTH_ERROR, "无权读取该图片内容");

        PictureFile file = resolveFile(pictureId, kind);
        String contentType;
        String downloadName;
        byte[] bytes;
        if (file != null) {
            bytes = pictureStorageService.readObject(file.getPlatform(), file.getObjectKey());
            contentType = PictureStorageService.contentTypeOf(file.getObjectKey());
            downloadName = download ? downloadName(picture, file.getObjectKey()) : null;
        } else {
            // 存量兼容：没有归属记录的旧图片仍可读取，但每次请求同样经过上面的授权判定；
            // 这些记录不会被自动认领，删除与归属迁移由维护窗口处理。
            String legacyUrl = legacyUrl(picture, kind);
            ThrowUtils.throwIf(StrUtil.isBlank(legacyUrl), ErrorCode.NOT_FOUND_ERROR, "该图片没有可用的内容记录");
            bytes = pictureStorageService.readLegacyObject(legacyUrl);
            contentType = PictureStorageService.contentTypeOf(legacyUrl);
            downloadName = download ? downloadName(picture, legacyUrl) : null;
        }
        return new Content(bytes, contentType, downloadName);
    }

    @Override
    public String contentUrl(Picture picture, ContentKind kind) {
        if (picture == null || picture.getId() == null || kind == null) {
            return null;
        }
        // 只有登记过资源记录的图片才对外给出内容地址。存量旧资源保留在库里，
        // 但不会因为猜测或前缀匹配被自动认领，因此也不暴露入口。
        return resolveFile(picture.getId(), kind) != null ? endpoint(picture.getId(), kind) : null;
    }

    @Override
    public void fillContentUrls(PictureVO pictureVO, Picture picture) {
        if (pictureVO == null || picture == null || picture.getId() == null) {
            return;
        }
        pictureVO.setUrl(contentUrl(picture, ContentKind.ORIGINAL));
        pictureVO.setThumbnailUrl(contentUrl(picture, ContentKind.THUMBNAIL));
        pictureVO.setPreviewUrl(contentUrl(picture, ContentKind.PREVIEW));
    }

    @Override
    public void fillContentUrls(List<PictureVO> pictureVOs, List<Picture> pictures) {
        if (pictureVOs == null || pictures == null || pictureVOs.isEmpty()) {
            return;
        }
        Set<Long> pictureIds = pictures.stream()
                .map(Picture::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Map<String, PictureFile>> filesByPicture = indexActiveFiles(pictureIds);
        int size = Math.min(pictureVOs.size(), pictures.size());
        for (int i = 0; i < size; i++) {
            PictureVO pictureVO = pictureVOs.get(i);
            Picture picture = pictures.get(i);
            if (pictureVO == null || picture == null || picture.getId() == null) {
                continue;
            }
            Map<String, PictureFile> files = filesByPicture.getOrDefault(picture.getId(), Map.of());
            pictureVO.setUrl(available(ContentKind.ORIGINAL, files)
                    ? endpoint(picture.getId(), ContentKind.ORIGINAL) : null);
            pictureVO.setThumbnailUrl(available(ContentKind.THUMBNAIL, files)
                    ? endpoint(picture.getId(), ContentKind.THUMBNAIL) : null);
            pictureVO.setPreviewUrl(available(ContentKind.PREVIEW, files)
                    ? endpoint(picture.getId(), ContentKind.PREVIEW) : null);
        }
    }

    private boolean available(ContentKind kind, Map<String, PictureFile> files) {
        if (files.containsKey(kind.value())) {
            return true;
        }
        // 与单条解析保持一致：预览缺失时退回原图
        return kind == ContentKind.PREVIEW && files.containsKey(ContentKind.ORIGINAL.value());
    }

    private Map<Long, Map<String, PictureFile>> indexActiveFiles(Collection<Long> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, PictureFile>> result = new HashMap<>();
        for (PictureFile file : pictureFileService.listActiveByPictures(pictureIds)) {
            result.computeIfAbsent(file.getPictureId(), key -> new HashMap<>()).put(file.getKind(), file);
        }
        return result;
    }

    /**
     * 解析某类内容的资源记录。预览不是每次上传都会生成，缺失时退回原图。
     */
    private PictureFile resolveFile(Long pictureId, ContentKind kind) {
        PictureFile file = pictureFileService.findActive(pictureId, kind.value());
        if (file != null) {
            return file;
        }
        if (kind == ContentKind.PREVIEW) {
            return pictureFileService.findActive(pictureId, ContentKind.ORIGINAL.value());
        }
        return null;
    }

    /**
     * 存量图片在记录里的地址。只用于读取兼容，不参与归属判定。
     */
    private String legacyUrl(Picture picture, ContentKind kind) {
        return switch (kind) {
            case ORIGINAL -> picture.getUrl();
            case THUMBNAIL -> StrUtil.isNotBlank(picture.getThumbnailUrl())
                    ? picture.getThumbnailUrl() : picture.getUrl();
            case PREVIEW -> StrUtil.isNotBlank(picture.getPreviewUrl())
                    ? picture.getPreviewUrl() : picture.getUrl();
        };
    }

    private String endpoint(Long pictureId, ContentKind kind) {
        return CONTENT_PATH + pictureId + "/" + kind.value();
    }

    private String downloadName(Picture picture, String objectKey) {
        String name = StrUtil.isNotBlank(picture.getName()) ? picture.getName() : "picture";
        String suffix = FileUtil.getSuffix(objectKey);
        if (StrUtil.isNotBlank(suffix) && !name.toLowerCase().endsWith("." + suffix.toLowerCase())) {
            name = name + "." + suffix;
        }
        return name;
    }
}
