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
        // 用锁定读：两个实例同时替换同一张图时，后到的一方必须看到前一方已经提交的
        // 记录状态，否则会把同一批旧记录再"替换"一次，旧对象被删除两遍。
        List<PictureFile> candidates = baseMapper.selectByPictureAndStatusForUpdate(
                pictureId, PictureFileStatusEnum.ACTIVE.getValue());
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
        // 已经被确认删除过：重复清理按成功处理，不再碰存储。
        // 同一张图的旧记录可能被两条路径同时选中，重复删除会让计数和外部调用都不对。
        PictureFile current = this.getById(file.getId());
        String objectKey = current == null ? file.getObjectKey() : current.getObjectKey();
        String platform = current == null ? file.getPlatform() : current.getPlatform();
        // 同一个对象键仍被别的有效记录引用时不删除：删掉会让另一条记录指向不存在的对象
        if (baseMapper.countLiveReferences(platform, objectKey, file.getId()) > 0) {
            log.info("对象仍被其他有效记录引用，跳过删除: platform={}, key={}", platform, objectKey);
            return true;
        }
        // 只有仍占用存储的记录才需要删对象。已替换/已删除的记录直接收敛状态，
        // 不重复调用存储：同一批旧记录可能被两条路径先后选中。
        boolean live = current == null || current.getStatus() == null
                || current.getStatus() == PictureFileStatusEnum.TEMP.getValue()
                || current.getStatus() == PictureFileStatusEnum.ACTIVE.getValue();
        if (!live) {
            return true;
        }
        boolean removed = pictureStorageService.deleteObject(platform, objectKey);
        if (!removed) {
            // 保留记录，便于重试与排查；状态不变表示删除未确认
            log.warn("资源对象删除未确认，保留记录: id={}, platform={}, key={}",
                    file.getId(), platform, objectKey);
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

    @Override
    public PictureFile lockById(Long id) {
        if (id == null) {
            return null;
        }
        return baseMapper.selectForUpdate(id);
    }

    @Override
    public List<PictureFile> listReclaimCandidates(int retentionHours, int limit) {
        if (retentionHours <= 0) {
            return List.of();
        }
        return baseMapper.selectReclaimCandidates(retentionHours, limit);
    }

    @Override
    public List<PictureFile> listPendingDeletes(int limit) {
        return baseMapper.selectPendingDeletes(limit);
    }

    @Override
    public void markDeletePending(Long id, int retryCount, long retryDelaySeconds) {
        if (id == null) {
            return;
        }
        baseMapper.markDeletePending(id, retryCount, retryDelaySeconds);
    }

    @Override
    public void markDeleteConfirmed(Long id, int retryCount) {
        if (id == null) {
            return;
        }
        baseMapper.markDeleteConfirmed(id, retryCount);
    }

    @Override
    public void markActive(Long id) {
        if (id == null) {
            return;
        }
        baseMapper.markActive(id);
    }
}
