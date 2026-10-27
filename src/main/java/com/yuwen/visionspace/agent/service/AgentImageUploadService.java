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

    @Resource
    private AgentAttachmentService agentAttachmentService;

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
     * 丢弃尚未绑定的临时上传。
     *
     * <p>只有资源记录的所有者可以丢弃；记录已删除时按成功处理（重复删除无副作用）。
     * 已经绑定到会话的资源不在这里删除：绑定的图片保留到会话或图片被明确删除，
     * 前端的失败清理不会把已经进入会话的图片删掉。
     *
     * @param resourceId 资源标识
     * @param loginUser  当前登录用户
     * @return 是否执行了删除
     */
    public boolean deleteImage(Long resourceId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(resourceId == null || resourceId <= 0, ErrorCode.PARAMS_ERROR, "资源标识不能为空");
        return agentAttachmentService.discardUnboundUpload(resourceId, loginUser.getId());
    }

    /**
     * 兼容旧客户端的按地址删除：仅在地址与本人已有记录精确匹配时允许。
     * 未知归属的旧文件不会被自动认领，也不会被删除。
     */
    public boolean deleteImageByExactUrl(String imageUrl, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(imageUrl), ErrorCode.PARAMS_ERROR, "资源地址不能为空");
        PictureFile file = pictureFileService.findByExactUrl(imageUrl.trim());
        if (file == null || !loginUser.getId().equals(file.getOwnerUserId())) {
            // 不区分"不存在"与"不属于你"，避免探测他人资源
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return agentAttachmentService.discardUnboundUpload(file.getId(), loginUser.getId());
    }

    private void validate(MultipartFile file) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(file.getSize() > MAX_SIZE_BYTES, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2MB");
        String extension = FileUtil.getSuffix(file.getOriginalFilename());
        ThrowUtils.throwIf(extension == null || !ALLOWED_FORMATS.contains(extension.toLowerCase()),
                ErrorCode.PARAMS_ERROR, "不支持的文件格式，仅支持 " + ALLOWED_FORMATS);
    }
}
