package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 通用文件上传服务：校验 + 临时文件 + 落存储 + 清理，只写这一份。
 *
 * <p>调用方各自给出路径前缀与限制（头像 2MB、反馈附件 5MB 等），
 * 底层流程一致，结果统一以 {@link StoredFile} 返回。
 */
@Slf4j
@Component
public class FileUploadService {

    /** 反馈附件单文件上限（与前端提示一致） */
    static final long MAX_FEEDBACK_ATTACHMENT_BYTES = 5L * 1024 * 1024;

    /** 反馈附件允许的图片格式 */
    static final List<String> FEEDBACK_ATTACHMENT_FORMATS = Arrays.asList("jpeg", "png", "jpg", "webp");

    private static final List<String> IMAGE_FORMATS = FEEDBACK_ATTACHMENT_FORMATS;

    @Resource
    private PictureStorageService pictureStorageService;

    /**
     * 上传文件（自定义校验参数）
     *
     * @param file           上传的文件
     * @param pathPrefix     存储路径前缀，如 "temp/123/" 或 "agent-chat/2026-04-05/"
     * @param maxSizeBytes   最大允许文件大小（字节）
     * @param allowedFormats 允许的文件扩展名列表（小写），如 ["jpeg","png","jpg","webp"]
     * @return 落盘结果（地址、平台、对象键、文件名、大小）
     */
    public StoredFile storeFile(MultipartFile file, String pathPrefix,
                                long maxSizeBytes, List<String> allowedFormats) {
        // 1. 非空校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 2. 大小校验
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 " + (maxSizeBytes / 1024 / 1024) + "MB");
        }

        // 3. 扩展名校验
        String extension = getFileExtension(file.getOriginalFilename());
        if (!allowedFormats.contains(extension)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的文件格式，仅支持 " + allowedFormats);
        }

        // 4. 生成文件名
        String filename = UUID.randomUUID() + "." + extension;

        // 5. 转为临时文件并上传
        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload-", "." + extension);
            file.transferTo(tempFile);
            FileInfo info = pictureStorageService.putObject(tempFile, pathPrefix, filename);
            StoredFile stored = new StoredFile(info.getUrl(), info.getPlatform(),
                    PictureStorageService.objectKey(info), filename, info.getSize());
            log.info("文件上传成功: path={}, url={}", pathPrefix + filename, stored.url());
            return stored;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            if (tempFile != null && !tempFile.delete()) {
                log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }
    }

    /**
     * 上传文件并只返回访问地址。
     */
    public String uploadFile(MultipartFile file, String pathPrefix,
                             long maxSizeBytes, List<String> allowedFormats) {
        return storeFile(file, pathPrefix, maxSizeBytes, allowedFormats).url();
    }

    /**
     * 上传图片文件（默认校验：2MB, jpeg/png/jpg/webp）
     *
     * @param file       上传的文件
     * @param pathPrefix 存储路径前缀
     * @return 文件访问 URL
     */
    public String uploadImageFile(MultipartFile file, String pathPrefix) {
        return uploadFile(file, pathPrefix, 2L * 1024 * 1024, IMAGE_FORMATS);
    }

    /**
     * 上传反馈附件。
     *
     * <p>与图库和 Agent 上传共用同一条上传流程，但返回的服务端定位信息单独登记，
     * 浏览器只拿到附件标识，不接触源站地址。
     */
    public UploadPictureResult uploadFeedbackAttachment(MultipartFile file, Long ownerUserId) {
        StoredFile stored = storeFile(file, "temp/feedback/" + ownerUserId + "/",
                MAX_FEEDBACK_ATTACHMENT_BYTES, FEEDBACK_ATTACHMENT_FORMATS);
        UploadPictureResult result = new UploadPictureResult();
        result.setPlatform(stored.platform());
        result.setStoragePath(stored.objectKey());
        result.setUrl(stored.url());
        result.setPicSize(stored.size());
        result.setPicName(stored.filename());
        return result;
    }

    private String getFileExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件格式错误");
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
    }
}
