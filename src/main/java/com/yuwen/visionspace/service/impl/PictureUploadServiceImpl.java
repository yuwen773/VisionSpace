package com.yuwen.visionspace.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.auth.SpaceAuthority;
import com.yuwen.visionspace.manager.auth.model.SpaceUserPermissionConstant;
import com.yuwen.visionspace.manager.lock.ResourceLockManager;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.manager.storage.UploadQuotaManager;
import com.yuwen.visionspace.manager.upload.FilePictureUpload;
import com.yuwen.visionspace.manager.upload.PictureUploadTemplate;
import com.yuwen.visionspace.manager.upload.UrlPictureUpload;
import com.yuwen.visionspace.manager.websocket.lease.PictureEditLeaseManager;
import com.yuwen.visionspace.mapper.PictureMapper;
import com.yuwen.visionspace.mapper.SpaceMapper;
import com.yuwen.visionspace.model.dto.file.UploadPictureResult;
import com.yuwen.visionspace.model.dto.picture.PictureUploadByBatchRequest;
import com.yuwen.visionspace.model.dto.picture.PictureUploadRequest;
import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.Space;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.PictureFilePurposeEnum;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import com.yuwen.visionspace.model.enums.ResourceTypeEnum;
import com.yuwen.visionspace.model.enums.SpaceTypeEnum;
import com.yuwen.visionspace.model.vo.PictureVO;
import com.yuwen.visionspace.service.PictureContentService;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import com.yuwen.visionspace.service.PictureFileService;
import com.yuwen.visionspace.service.PicturePreviewService;
import com.yuwen.visionspace.service.PictureService;
import com.yuwen.visionspace.service.PictureUploadService;
import com.yuwen.visionspace.service.ResourceIsolationService;
import com.yuwen.visionspace.service.SpaceService;
import com.yuwen.visionspace.service.StorageCleanupService;
import com.yuwen.visionspace.utils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 图片上传生命周期实现：对象存储、配额预留、资源登记与失败补偿。
 *
 * <p>补偿协议（回收临时对象、登记清理任务、退还当日次数）集中在这里，
 * 与正常上传路径共用同一组 helper，避免失败路径各自演化。
 */
@Slf4j
@Service
public class PictureUploadServiceImpl implements PictureUploadService {

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureContentService pictureContentService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private PicturePreviewService picturePreviewService;

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private PictureFileService pictureFileService;

    // 站内向量检索（RAG）默认关闭；关闭时该 Bean 不存在，调用点统一判空。
    @Autowired
    private ObjectProvider<PictureEmbeddingService> pictureEmbeddingServiceProvider;

    @Resource
    private UploadQuotaManager uploadQuotaManager;

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

    @Resource
    private PictureEditLeaseManager pictureEditLeaseManager;

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        Space space = null;
        if (spaceId != null) {
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 业务边界复核：只有空间所有者或团队编辑者可向该空间上传
            ThrowUtils.throwIf(!spaceAuthority.canOperateSpace(space, loginUser,
                            SpaceUserPermissionConstant.PICTURE_UPLOAD),
                    ErrorCode.NO_AUTH_ERROR);
            // 额度不在进入存储前做「读-判断」：读到的计数随时会被另一实例改掉。
            // 权威判定是事务里的条件更新，见下方 reservePictureSlot / applyPictureSizeDelta。
        }
        // 判断是新增还是替换
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        if (pictureId != null) {
            Picture oldPicture = pictureService.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 重新上传会覆盖原图，业务边界必须复核归属
            checkPictureAuth(loginUser, oldPicture, SpaceUserPermissionConstant.PICTURE_EDIT);
            // 预检：请求开始前就已隔离的资源直接拒绝，不进入对象存储
            // 事务内还有一次权威判定（行锁 + 锁定读），覆盖预检之后隔离才生效的竞态
            ThrowUtils.throwIf(isQuarantined(pictureId), ErrorCode.OPERATION_ERROR, "资源处于隔离状态，不能替换");
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId（这样也兼容了公共图库）
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原图片的空间 id 一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        Space replacementSpace = pictureId != null && spaceId != null ? spaceService.getById(spaceId) : null;
        boolean teamReplacement = replacementSpace != null
                && Objects.equals(replacementSpace.getSpaceType(), SpaceTypeEnum.TEAM.getValue());
        if (teamReplacement) {
            ThrowUtils.throwIf(!pictureEditLeaseManager.canSave(pictureId, loginUser.getId(),
                    pictureUploadRequest.getCollaborationToken()), ErrorCode.NO_AUTH_ERROR, "协作编辑权已失效");
        }
        // 上传图片，得到图片信息
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            // 公共图库
            uploadPathPrefix = String.format("/public/%s/", loginUser.getId());
        } else {
            // 空间
            uploadPathPrefix = String.format("/space/%s/", spaceId);
        }
        // 根据 inputSource 的类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        // 用户级速率与额度：超限时在进入对象存储前拒绝，拒绝路径零副作用
        uploadQuotaManager.checkRate(loginUser.getId(), PictureFilePurposeEnum.GALLERY);
        // 上传前的预检只能看已用额度；本次文件大小要等上传完成后才知道
        uploadQuotaManager.checkQuota(loginUser.getId(), null);
        UploadPictureResult uploadPictureResult;
        try {
            uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        } catch (RuntimeException e) {
            // 存储失败不计入当日上传次数
            uploadQuotaManager.refundRate(loginUser.getId(), PictureFilePurposeEnum.GALLERY);
            throw e;
        }
        try {
            // 把本次上传的字节数计入额度，避免"已用未超、再加就超"的请求落库
            uploadQuotaManager.checkQuota(loginUser.getId(), uploadPictureResult.getPicSize());
        } catch (RuntimeException e) {
            discardUploadedObjects(loginUser.getId(), uploadPictureResult);
            throw e;
        }
        // 登记资源归属：所有者、平台与对象键都来自服务端，后续删除/读取以记录为准
        List<PictureFile> uploadedFiles;
        try {
            uploadedFiles = pictureFileService.registerUpload(
                    loginUser, PictureFilePurposeEnum.GALLERY, spaceId, uploadPictureResult);
        } catch (RuntimeException e) {
            // 记录登记失败时对象已经写入存储：先按平台与对象键回收，再让请求失败
            discardUploadedObjects(loginUser.getId(), uploadPictureResult);
            throw e;
        }
        // 构造要入库的图片信息
        Picture picture = new Picture();
        // 属性拷贝
        BeanUtil.copyProperties(uploadPictureResult, picture);
        // 转换为标准颜色（6位十六进制）
        picture.setPicColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()));
        picture.setSpaceId(spaceId);
        picture.setPreviewUrl(null);
        // 支持外层传递图片名称
        String picName = StrUtil.isNotBlank(pictureUploadRequest.getPicName()) ? pictureUploadRequest.getPicName()
                : uploadPictureResult.getPicName();
        picture.setName(picName);
        picture.setUserId(loginUser.getId());
        // 补充审核参数
        pictureService.fillReviewParams(picture, loginUser);
        // 操作数据库
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 开启事务
        Long finalSpaceId = spaceId;
        Long finalPictureId = pictureId;
        try {
            transactionTemplate.execute(status -> {
                long oldSize = 0L;
                if (finalPictureId != null) {
                    // 替换已有资源：先取资源行锁，再按最新状态复核隔离。
                    // 隔离与替换共用同一把行锁：谁先取到谁先提交，后取到的一方
                    // 在锁释放后重新判定，因此"隔离已生效但原图仍被替换销毁"的窗口不存在。
                    ThrowUtils.throwIf(!resourceLockManager.lockResourceRow(
                                    ResourceTypeEnum.PICTURE.getValue(), finalPictureId),
                            ErrorCode.NOT_FOUND_ERROR, "图片不存在");
                    ThrowUtils.throwIf(resourceIsolationService.isQuarantinedForUpdate(
                                    ResourceTypeEnum.PICTURE.getValue(), finalPictureId),
                            ErrorCode.OPERATION_ERROR, "资源处于隔离状态，不能替换");
                    // 差额必须基于锁内读到的最新大小，不能用请求开始前读到的值
                    Picture locked = pictureMapper.selectForUpdate(finalPictureId);
                    ThrowUtils.throwIf(locked == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
                    oldSize = locked.getPicSize() == null ? 0L : locked.getPicSize();
                    if (teamReplacement) {
                        // 团队图片替换：锁内再复核归属与连接专属凭据，
                        // 与 #44 的「过期租约迟到保存不得落库」保持同一判定点。
                        checkPictureAuth(loginUser, locked, SpaceUserPermissionConstant.PICTURE_EDIT);
                        ThrowUtils.throwIf(!pictureEditLeaseManager.canSave(finalPictureId, loginUser.getId(),
                                pictureUploadRequest.getCollaborationToken()), ErrorCode.NO_AUTH_ERROR, "协作编辑权已失效");
                    }
                }
                // 先占额度再写图片：额度判定与写入是同一条条件更新，
                // 超限时影响 0 行，后面的图片写入随事务一起回滚，不会留下超额的图片。
                if (finalSpaceId != null) {
                    long newSize = picture.getPicSize() == null ? 0L : picture.getPicSize();
                    int updated = finalPictureId == null
                            // 新增：数量 +1、大小 +本次字节数，同一条语句判定上限
                            ? spaceMapper.reservePictureSlot(finalSpaceId, newSize)
                            // 替换：数量不变，按新旧差额调整；变小不受上限限制
                            : spaceMapper.applyPictureSizeDelta(finalSpaceId, newSize - oldSize);
                    ThrowUtils.throwIf(updated == 0, ErrorCode.OPERATION_ERROR,
                            finalPictureId == null ? "空间额度不足" : "空间大小不足");
                }
                // 插入数据
                boolean result = pictureService.saveOrUpdate(picture);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
                // 资源记录与图片记录在同一事务内绑定，避免出现无主对象
                pictureFileService.bindToPicture(picture.getId(), loginUser.getId(), uploadedFiles);
                // 与替换一同提交清理任务，提交前绝不删除旧对象。
                List<PictureFile> superseded = pictureFileService.supersedeExcept(picture.getId(),
                        uploadedFiles.stream().map(PictureFile::getId).toList());
                for (PictureFile file : superseded) {
                    storageCleanupService.enqueueReplacedFile(file);
                }
                return picture;
            });
        } catch (RuntimeException e) {
            // 事务回滚后图片记录不存在，登记的资源仍是临时状态：回收对象并删除记录
            discardRegisteredFiles(loginUser.getId(), uploadedFiles, uploadPictureResult);
            throw e;
        }
        picturePreviewService.generateAndUpdatePreview(picture.getId(), uploadPictureResult.getUrl());
        // 异步生成向量嵌入（仅审核通过的公开图片）
        if (picture.getReviewStatus() != null
                && picture.getReviewStatus().equals(PictureReviewStatusEnum.PASS.getValue())
                && picture.getSpaceId() == null) {
            PictureEmbeddingService embeddings = embeddingService();
            if (embeddings != null) {
                embeddings.generateAndStoreEmbedding(picture);
            }
        }
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 上传响应同样只返回受控内容入口
        pictureContentService.fillContentUrls(pictureVO, picture);
        return pictureVO;
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 名称前缀默认等于搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        // 遍历元素，依次处理上传图片
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片的地址，防止转义或者和对象存储冲突的问题
            // codefather.cn?yuwen=dog，应该只保留 codefather.cn
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    /**
     * 上传后失败时回收已写入存储的对象（还没有资源记录可依据）。
     *
     * <p>删除未确认时登记清理任务：对象已经写进存储，数据库里却没有任何记录
     * 指向它，不登记就没有任何线索能在之后把它找出来。
     */
    private void discardUploadedObjects(Long userId, UploadPictureResult uploadResult) {
        if (uploadResult != null) {
            String platform = uploadResult.getPlatform();
            String originalKey = uploadResult.getStoragePath();
            boolean originalRemoved = pictureStorageService.deleteObject(platform, originalKey);
            if (!originalRemoved) {
                // 登记失败时对象仍在存储里：记录定位信息并继续尝试缩略图，
                // 不让一个对象的问题掩盖另一个对象。
                enqueueOrLog(platform, originalKey, PictureFileService.KIND_ORIGINAL,
                        "上传失败回收原图未确认");
            }
            // 缩略图独立重试：原图回收失败时也要登记，否则它会成为永久孤儿对象
            String thumbnailKey = uploadResult.getThumbnailObjectKey();
            if (StrUtil.isNotBlank(thumbnailKey)
                    && !pictureStorageService.deleteObject(platform, thumbnailKey)) {
                enqueueOrLog(platform, thumbnailKey, PictureFileService.KIND_THUMBNAIL,
                        "上传失败回收缩略图未确认");
            }
        }
        uploadQuotaManager.refundRate(userId, PictureFilePurposeEnum.GALLERY);
    }

    /**
     * 登记失败保留定位信息：对象已经写进存储却没有任何记录指向它，日志是最后的
     * 人工核对线索。缩略图与原图各自独立登记，一个失败不影响另一个。
     */
    private void enqueueOrLog(String platform, String key, String kind, String reason) {
        try {
            storageCleanupService.enqueueUploadedObject(platform, key, kind, reason);
        } catch (RuntimeException e) {
            log.error("上传失败后的对象清理任务登记失败: platform={}, key={}, kind={}, reason={}",
                    platform, key, kind, reason, e);
        }
    }

    /**
     * 绑定失败时回收已登记的资源：删除对象与临时记录，并回滚当日上传次数。
     */
    private void discardRegisteredFiles(Long userId, List<PictureFile> files,
                                        UploadPictureResult uploadResult) {
        if (files == null || files.isEmpty()) {
            discardUploadedObjects(userId, uploadResult);
            return;
        }
        for (PictureFile file : files) {
            if (!pictureFileService.deleteFile(file)) {
                try {
                    // 原子标记已放弃的临时记录并登记任务；记录已变化时不猜测删除。
                    storageCleanupService.enqueueAbandonedUpload(file, "上传失败后的资源回收未完成");
                    log.warn("上传失败后的资源回收未完成: fileId={}, kind={}", file.getId(), file.getKind());
                } catch (RuntimeException e) {
                    // 单个任务登记失败不能阻止后续对象的回收；临时记录仍保留供核对。
                    log.error("上传失败后的资源清理任务登记失败: fileId={}, platform={}, key={}",
                            file.getId(), file.getPlatform(), file.getObjectKey(), e);
                }
            }
        }
        uploadQuotaManager.refundRate(userId, PictureFilePurposeEnum.GALLERY);
    }

    /**
     * 图片写操作的统一判定：公开图库仅作者，私有空间仅所有者，团队空间按成员角色。
     */
    private void checkPictureAuth(User loginUser, Picture picture, String permission) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(!spaceAuthority.canOperatePicture(picture, loginUser, permission),
                ErrorCode.NO_AUTH_ERROR);
    }

    private boolean isQuarantined(Long pictureId) {
        return pictureId != null
                && resourceIsolationService.isQuarantined(ResourceTypeEnum.PICTURE.getValue(), pictureId);
    }

    private PictureEmbeddingService embeddingService() {
        return pictureEmbeddingServiceProvider.getIfAvailable();
    }
}
