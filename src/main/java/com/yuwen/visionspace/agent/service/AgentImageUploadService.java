package com.yuwen.visionspace.agent.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.manager.storage.UploadQuotaManager;
import com.yuwen.visionspace.model.entity.PictureFile;
import com.yuwen.visionspace.model.entity.User;
import com.yuwen.visionspace.model.enums.PictureFilePurposeEnum;
import com.yuwen.visionspace.model.enums.PictureFileStatusEnum;
import com.yuwen.visionspace.service.PictureFileService;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Agent 临时图片上传。
 *
 * <p>每次上传登记一条资源记录（所有者、平台、对象键、临时状态），客户端拿到的是
 * 资源标识而不是源站地址；删除只按资源标识与所有者判定，任意 URL 都不能触发删除。
 */
@Slf4j
@Service
public class AgentImageUploadService {

    private static final long MAX_SIZE_BYTES = 2L * 1024 * 1024;

    private static final List<String> ALLOWED_FORMATS = Arrays.asList("jpeg", "png", "jpg", "webp");

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private PictureFileService pictureFileService;

    @Resource
    private UploadQuotaManager uploadQuotaManager;

    /**
     * 上传临时图片。
     *
     * @return 新登记的资源记录
     */
    public PictureFile uploadImage(MultipartFile file, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        validate(file);
        // 用户级速率与额度：超限时在进入对象存储前拒绝
        uploadQuotaManager.checkRate(loginUser.getId(), PictureFilePurposeEnum.AGENT_CHAT);
        uploadQuotaManager.checkQuota(loginUser.getId(), file.getSize());
        String extension = FileUtil.getSuffix(file.getOriginalFilename()).toLowerCase();
        String pathPrefix = "/agent-chat/" + loginUser.getId() + "/" + java.time.LocalDate.now() + "/";
        String filename = UUID.randomUUID() + "." + extension;
        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload-", "." + extension);
            file.transferTo(tempFile);
            FileInfo fileInfo = pictureStorageService.putObject(tempFile, pathPrefix, filename);

            PictureFile record = new PictureFile();
            record.setOwnerUserId(loginUser.getId());
            record.setPurpose(PictureFilePurposeEnum.AGENT_CHAT.getValue());
            record.setKind(PictureFileService.KIND_ORIGINAL);
            record.setPlatform(fileInfo.getPlatform());
            record.setObjectKey(PictureStorageService.objectKey(fileInfo));
            record.setUrl(fileInfo.getUrl());
            record.setFileSize(fileInfo.getSize());
            record.setStatus(PictureFileStatusEnum.TEMP.getValue());
            ThrowUtils.throwIf(!pictureFileService.save(record), ErrorCode.OPERATION_ERROR, "资源记录保存失败");
            return record;
        } catch (BusinessException e) {
            uploadQuotaManager.refundRate(loginUser.getId(), PictureFilePurposeEnum.AGENT_CHAT);
            throw e;
        } catch (Exception e) {
            uploadQuotaManager.refundRate(loginUser.getId(), PictureFilePurposeEnum.AGENT_CHAT);
            log.error("Agent 图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            if (tempFile != null && !tempFile.delete()) {
                log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }
    }

    /**
     * 删除临时图片。
     *
     * <p>只有资源记录的所有者可以删除；记录已删除时按成功处理（重复删除无副作用）。
     *
     * @param resourceId 资源标识
     * @param loginUser  当前登录用户
     * @return 是否执行了删除
     */
    public boolean deleteImage(Long resourceId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(resourceId == null || resourceId <= 0, ErrorCode.PARAMS_ERROR, "资源标识不能为空");
        return deleteOwnedRecord(pictureFileService.getById(resourceId), loginUser);
    }

    /**
     * 校验对话附件属于当前用户。
     *
     * <p>Agent 会把附件地址交给模型与工具读取，因此地址必须先与本人的资源记录
     * 精确匹配；未登记或属于他人的地址一律拒绝，不做前缀或域名匹配。
     *
     * @param imageUrls 客户端提交的附件地址
     * @param loginUser 当前登录用户
     */
    public void assertOwnedAttachments(List<String> imageUrls, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        for (String url : imageUrls) {
            if (StrUtil.isBlank(url)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "附件地址不能为空");
            }
            PictureFile file = pictureFileService.findByExactUrl(url.trim());
            if (file == null
                    || !loginUser.getId().equals(file.getOwnerUserId())
                    || !PictureFilePurposeEnum.AGENT_CHAT.getValue().equals(file.getPurpose())) {
                // 不区分"不存在"与"不属于你"，避免探测他人资源
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "附件不存在或无权访问");
            }
        }
    }

    /**
     * 兼容旧客户端的按地址删除：仅在地址与本人已有记录精确匹配时允许。
     * 未知归属的旧文件不会被自动认领，也不会被删除。
     */
    public boolean deleteImageByExactUrl(String imageUrl, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(imageUrl), ErrorCode.PARAMS_ERROR, "资源地址不能为空");
        return deleteOwnedRecord(pictureFileService.findByExactUrl(imageUrl.trim()), loginUser);
    }

    /**
     * 按资源记录删除：归属只看服务端记录，已删除的记录按成功处理。
     * 未知记录同样按"不存在或无权"拒绝，不区分两者以避免探测他人资源。
     */
    private boolean deleteOwnedRecord(PictureFile file, User loginUser) {
        if (file == null || !loginUser.getId().equals(file.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (PictureFileStatusEnum.DELETED.getValue() == file.getStatus()) {
            return false;
        }
        return pictureFileService.deleteFile(file);
    }

    private void validate(MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(file.getSize() > MAX_SIZE_BYTES, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2MB");
        String extension = FileUtil.getSuffix(file.getOriginalFilename());
        ThrowUtils.throwIf(extension == null || !ALLOWED_FORMATS.contains(extension.toLowerCase()),
                ErrorCode.PARAMS_ERROR, "不支持的文件格式，仅支持 " + ALLOWED_FORMATS);
    }
}
