package com.yuwen.visionspace.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.PictureFileMapper;
import com.yuwen.visionspace.model.dto.file.UploadPictureResult;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.PictureFilePurposeEnum;
import com.yuwen.visionspace.model.enums.PictureFileStatusEnum;
import com.yuwen.visionspace.service.PictureFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class PictureFileServiceImpl extends ServiceImpl<PictureFileMapper, PictureFile>
        implements PictureFileService {

    @Resource
    private PictureStorageService pictureStorageService;

    @Override
    public List<PictureFile> registerUpload(User owner, PictureFilePurposeEnum purpose, Long spaceId,
                                            UploadPictureResult uploadResult) {
        ThrowUtils.throwIf(owner == null || owner.getId() == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(purpose == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(uploadResult == null || StrUtil.isBlank(uploadResult.getPlatform())
                        || StrUtil.isBlank(uploadResult.getStoragePath()),
                ErrorCode.SYSTEM_ERROR, "上传结果缺少资源定位信息");

        List<PictureFile> files = new ArrayList<>();
        files.add(newRecord(owner.getId(), spaceId, purpose, KIND_ORIGINAL,
                uploadResult.getPlatform(), uploadResult.getStoragePath(), uploadResult.getUrl(),
                uploadResult.getPicSize()));
        if (StrUtil.isNotBlank(uploadResult.getThumbnailObjectKey())) {
            files.add(newRecord(owner.getId(), spaceId, purpose, KIND_THUMBNAIL,
                    uploadResult.getPlatform(), uploadResult.getThumbnailObjectKey(),
                    uploadResult.getThumbnailUrl(), null));
        }
        for (PictureFile file : files) {
            ThrowUtils.throwIf(!this.save(file), ErrorCode.OPERATION_ERROR, "资源记录保存失败");
        }
        return files;
    }

    private PictureFile newRecord(Long ownerUserId, Long spaceId, PictureFilePurposeEnum purpose, String kind,
                                  String platform, String objectKey, String url, Long fileSize) {
        PictureFile file = new PictureFile();
        file.setOwnerUserId(ownerUserId);
        file.setSpaceId(spaceId);
        file.setPurpose(purpose.getValue());
        file.setKind(kind);
        file.setPlatform(platform);
        file.setObjectKey(objectKey);
        file.setUrl(url);
        file.setFileSize(fileSize);
        file.setStatus(PictureFileStatusEnum.TEMP.getValue());
        return file;
    }

    @Override
    public void bindToPicture(Long pictureId, Long ownerUserId, List<PictureFile> files) {
        ThrowUtils.throwIf(pictureId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(files == null || files.isEmpty(), ErrorCode.PARAMS_ERROR);
        for (PictureFile file : files) {
            // 只允许绑定本人登记的资源，避免把他人临时资源挂到自己名下
            ThrowUtils.throwIf(!ownerUserId.equals(file.getOwnerUserId()), ErrorCode.NO_AUTH_ERROR);
            PictureFile update = new PictureFile();
            update.setId(file.getId());
            update.setPictureId(pictureId);
            update.setStatus(PictureFileStatusEnum.ACTIVE.getValue());
            ThrowUtils.throwIf(!this.updateById(update), ErrorCode.OPERATION_ERROR, "资源绑定失败");
        }
    }

    @Override
    public List<PictureFile> supersedeExcept(Long pictureId, List<Long> keptFileIds) {
        if (pictureId == null) {
            return List.of();
        }
        List<PictureFile> candidates = this.lambdaQuery()
                .eq(PictureFile::getPictureId, pictureId)
                .eq(PictureFile::getStatus, PictureFileStatusEnum.ACTIVE.getValue())
                .list();
        List<PictureFile> superseded = new ArrayList<>();
        for (PictureFile candidate : candidates) {
            if (keptFileIds != null && keptFileIds.contains(candidate.getId())) {
                continue;
            }
            PictureFile update = new PictureFile();
            update.setId(candidate.getId());
            update.setStatus(PictureFileStatusEnum.SUPERSEDED.getValue());
            this.updateById(update);
            superseded.add(candidate);
        }
        return superseded;
    }

    @Override
    public PictureFile findActive(Long pictureId, String kind) {
        if (pictureId == null || StrUtil.isBlank(kind)) {
            return null;
        }
        return this.lambdaQuery()
                .eq(PictureFile::getPictureId, pictureId)
                .eq(PictureFile::getKind, kind)
                .eq(PictureFile::getStatus, PictureFileStatusEnum.ACTIVE.getValue())
                .orderByDesc(PictureFile::getId)
                .last("LIMIT 1")
                .one();
    }

    @Override
    public List<PictureFile> listActiveByPicture(Long pictureId) {
        if (pictureId == null) {
            return List.of();
        }
        return this.lambdaQuery()
                .eq(PictureFile::getPictureId, pictureId)
                .eq(PictureFile::getStatus, PictureFileStatusEnum.ACTIVE.getValue())
                .list();
    }

    @Override
    public List<PictureFile> listActiveByPictures(Collection<Long> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) {
            return List.of();
        }
        return this.lambdaQuery()
                .in(PictureFile::getPictureId, pictureIds)
                .eq(PictureFile::getStatus, PictureFileStatusEnum.ACTIVE.getValue())
                .list();
    }

    @Override
    public PictureFile findByExactUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        // 精确等值匹配：未知归属的旧文件不会被"前缀命中"自动认领
        return this.lambdaQuery()
                .eq(PictureFile::getUrl, url)
                .ne(PictureFile::getStatus, PictureFileStatusEnum.DELETED.getValue())
                .orderByDesc(PictureFile::getId)
                .last("LIMIT 1")
                .one();
    }

    @Override
    public boolean deleteFile(PictureFile file) {
        if (file == null || file.getId() == null) {
            return true;
        }
        boolean removed = pictureStorageService.deleteObject(file.getPlatform(), file.getObjectKey());
        if (!removed) {
            // 保留记录，便于重试与排查；状态不变表示删除未确认
            log.warn("资源对象删除未确认，保留记录: id={}, platform={}, key={}",
                    file.getId(), file.getPlatform(), file.getObjectKey());
            return false;
        }
        PictureFile update = new PictureFile();
        update.setId(file.getId());
        update.setStatus(PictureFileStatusEnum.DELETED.getValue());
        update.setUpdateTime(new Date());
        this.updateById(update);
        return true;
    }

    @Override
    public long usedBytes(Long ownerUserId) {
        if (ownerUserId == null) {
            return 0L;
        }
        // 只统计仍然占用存储的记录：被替换的旧对象已删除，不应继续计入额度
        return this.lambdaQuery()
                .eq(PictureFile::getOwnerUserId, ownerUserId)
                .in(PictureFile::getStatus, PictureFileStatusEnum.TEMP.getValue(),
                        PictureFileStatusEnum.ACTIVE.getValue())
                .list()
                .stream()
                .map(PictureFile::getFileSize)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }
}
