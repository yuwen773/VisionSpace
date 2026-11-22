package com.yuwen.visionspace.service.impl;

import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.auth.SpaceAuthority;
import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.manager.lock.ResourceLockManager;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.mapper.SpaceMapper;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.service.PictureDeletionService;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import com.yuwen.visionspace.service.PictureFileService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.StorageCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 图片删除生命周期实现：行锁与隔离复核、额度释放、对象清理。
 *
 * <p>事务内只做数据库删除与额度释放；对象清理在事务提交后按记录精确执行，
 * 删除未确认时登记可重试的清理任务，而不是只留日志。
 */
@Slf4j
@Service
public class PictureDeletionServiceImpl implements PictureDeletionService {

    @Resource
    private PictureService pictureService;

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private PictureFileService pictureFileService;

    // 站内向量检索（RAG）默认关闭；关闭时该 Bean 不存在，调用点统一判空。
    @Autowired
    private ObjectProvider<PictureEmbeddingService> pictureEmbeddingServiceProvider;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private SpaceAuthority spaceAuthority;

    @Resource
    private ResourceIsolationService resourceIsolationService;

    @Resource
    private ResourceLockManager resourceLockManager;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private StorageCleanupService storageCleanupService;

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 业务边界最终复核：注解鉴权依赖请求上下文，直接调用服务时不能跳过归属判断
        ThrowUtils.throwIf(!spaceAuthority.canOperatePicture(oldPicture, loginUser,
                        SpaceUserPermissionConstant.PICTURE_DELETE),
                ErrorCode.NO_AUTH_ERROR);
        // 预检：请求开始前就已隔离的资源直接拒绝
        ThrowUtils.throwIf(isQuarantined(pictureId), ErrorCode.OPERATION_ERROR, "资源处于隔离状态，暂不能删除");
        // 开启事务
        Picture deletedPicture = transactionTemplate.execute(status -> {
            // 资源行锁：与隔离、替换共用同一把锁。
            // 拿到锁之后复核隔离状态，隔离若在预检之后生效，这里直接拒绝：
            // 谁先取到锁谁先提交，破坏性动作不会落在隔离生效之后。
            ThrowUtils.throwIf(!resourceLockManager.lockResourceRow(
                            ResourceTypeEnum.PICTURE.getValue(), pictureId),
                    ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            ThrowUtils.throwIf(resourceIsolationService.isQuarantinedForUpdate(
                            ResourceTypeEnum.PICTURE.getValue(), pictureId),
                    ErrorCode.OPERATION_ERROR, "资源处于隔离状态，暂不能删除");
            Picture lockedPicture = pictureMapper.selectForUpdate(pictureId);
            ThrowUtils.throwIf(lockedPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 操作数据库：逻辑删除影响 0 行说明已被并发删除，此时不能再扣额度，
            // 否则同一张图会被扣两次
            boolean result = pictureService.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的使用额度，释放额度（仅当图片属于某个空间时）。
            // 公开图库（spaceId 为空）没有空间额度可扣。
            // 释放语句把计数收敛到 0：存量偏差不能把删除卡住，也不能让计数变负。
            if (lockedPicture.getSpaceId() != null) {
                long size = lockedPicture.getPicSize() == null ? 0L : lockedPicture.getPicSize();
                ThrowUtils.throwIf(spaceMapper.releasePictureSlot(lockedPicture.getSpaceId(), size) == 0,
                        ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return lockedPicture;
        });
        // 异步删除向量嵌入
        PictureEmbeddingService embeddings = embeddingService();
        if (embeddings != null) {
            embeddings.deleteEmbedding(pictureId);
        }
        // 对象清理放在事务之后：图片记录此时已经删除，隔离无法再针对它生效，
        // 因此不会与处置流程抢同一份内容。
        // 这里同步调用（不经代理）：调用方要的是一次完整的删除，清理失败已由
        // 清理任务登记兜底，不把结果留给不可见的异步线程。
        this.clearPictureFile(deletedPicture);
    }

    /**
     * 清理图片关联的对象。有资源记录时按记录精确删除；存量对象按地址删除。
     */
    private void clearPictureFile(Picture oldPicture) {
        // 有资源记录的图片：按记录里的平台与对象键精确删除，不解析 URL
        List<PictureFile> recorded = pictureFileService.listActiveByPicture(oldPicture.getId());
        if (!recorded.isEmpty()) {
            cleanupRecordedFiles(oldPicture.getId(), recorded);
            return;
        }
        // 兼容路径：没有归属记录的存量图片。仅当同一地址仍被其他记录引用时才跳过，
        // 不会因为前缀或域名相同而误删其他对象。
        String pictureUrl = oldPicture.getUrl();
        long count = pictureService.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // 存量对象没有平台与对象键，只能按地址删除；删除未确认时登记为等待人工核对，
        // 不按地址进入自动重试（地址不能作为可信的删除定位）
        if (!pictureStorageService.deleteObject(pictureUrl)) {
            enqueueForReviewOrLog(pictureUrl, "存量图片原图删除未确认");
        }
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl) && !pictureStorageService.deleteObject(thumbnailUrl)) {
            enqueueForReviewOrLog(thumbnailUrl, "存量图片缩略图删除未确认");
        }
        String previewUrl = oldPicture.getPreviewUrl();
        if (StrUtil.isNotBlank(previewUrl) && !pictureStorageService.deleteObject(previewUrl)) {
            enqueueForReviewOrLog(previewUrl, "存量图片预览图删除未确认");
        }
    }

    /** 登记失败保留地址线索：三个衍生对象各自独立，一个失败不影响其余。 */
    private void enqueueForReviewOrLog(String url, String reason) {
        try {
            storageCleanupService.enqueueForReview(url, reason);
        } catch (RuntimeException e) {
            log.error("存量对象人工核对任务登记失败: url={}, reason={}", url, reason, e);
        }
    }

    /**
     * 删除图片关联的资源对象。任一对象删除失败只记录日志并保留记录，
     * 由后续重试或维护清理处理；数据库删除与额度释放已经生效。
     */
    private void cleanupRecordedFiles(Long pictureId, List<PictureFile> files) {
        cleanupFiles(files, "图片资源删除未完成: pictureId=" + pictureId);
    }

    private void cleanupFiles(List<PictureFile> files, String reason) {
        for (PictureFile file : files) {
            if (!pictureFileService.deleteFile(file)) {
                try {
                    // 删除未确认：登记可重试的清理任务，而不是只留一行日志
                    storageCleanupService.enqueueDeletedPictureFile(file, reason);
                    log.warn("{}, fileId={}, kind={}", reason, file.getId(), file.getKind());
                } catch (RuntimeException e) {
                    // 单个对象登记失败不影响其余对象；资源记录仍在，下一轮可再核对
                    log.error("清理任务登记失败，资源记录保留: fileId={}, key={}, reason={}",
                            file.getId(), file.getObjectKey(), reason, e);
                }
            }
        }
    }

    private boolean isQuarantined(Long pictureId) {
        return pictureId != null
                && resourceIsolationService.isQuarantined(ResourceTypeEnum.PICTURE.getValue(), pictureId);
    }

    private PictureEmbeddingService embeddingService() {
        return pictureEmbeddingServiceProvider.getIfAvailable();
    }
}
