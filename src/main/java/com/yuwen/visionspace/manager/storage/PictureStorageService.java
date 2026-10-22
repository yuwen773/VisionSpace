package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;

/**
 * 统一存储服务，底层使用 x-file-storage
 */
@Slf4j
@Component
public class PictureStorageService {

    /**
     * 单次内容读取上限。图片上传本身已有 2MB / 10MB 限制，这里再兜底一次，
     * 避免异常对象或错误键把服务端内存打满。
     */
    static final long MAX_CONTENT_BYTES = 20L * 1024 * 1024;

    @Resource
    private FileStorageService fileStorageService;

    /**
     * 上传图片（支持 WebP 压缩 + 缩略图）
     *
     * @param file 文件
     * @param uploadPathPrefix  存储路径 uploadPathPrefix
     * @param uploadFilename  上传文件名 uploadFilename [包含后缀]
     * @return FileInfo 包含 url, thumbnailUrl 等
     */
    public FileInfo putPictureObject(File file, String uploadPathPrefix, String uploadFilename) {
        try {
            log.info("开始上传文件: file uploadPathPrefix={}, file uploadFilename={}", uploadPathPrefix, uploadFilename);
            log.info("FileStorageService 状态: platformListSize={}, defaultPlatform={}",
                    fileStorageService.getFileStorageList().size(),
                    fileStorageService.getProperties().getDefaultPlatform());

            return fileStorageService.of(file)
                    .thumbnail(256, 256)
                    .setPath(uploadPathPrefix)
                    .setSaveFilename(uploadFilename)
                    .upload();
        } catch (Exception e) {
            log.error("图片上传失败: uploadPathPrefix={}", uploadPathPrefix, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        }
    }

    /**
     * 上传原始文件
     */
    public FileInfo putObject(File file, String uploadPathPrefix, String uploadFilename) {
        try {
            return fileStorageService.of(file)
                    .setPath(uploadPathPrefix)
                    .setSaveFilename(uploadFilename)
                    .upload();
        } catch (Exception e) {
            log.error("文件上传失败: uploadPathPrefix={}", uploadPathPrefix, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }
    }

    /**
     * 通过公网 URL 下载文件。
     *
     * <p>仅用于存量资源：新资源一律通过资源记录定位到平台与对象键，再由
     * {@link #readObject(String, String)} 读取。
     */
    public InputStream getObjectByUrl(String fileUrl) {
        try {
            return new URI(fileUrl).toURL().openStream();
        } catch (Exception e) {
            log.error("文件读取失败: fileUrl={}", fileUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件读取失败");
        }
    }

    /**
     * 按资源记录定位读取对象内容。
     *
     * @param platform  存储平台标识
     * @param objectKey 对象键（含平台 basePath）
     * @return 对象字节内容
     */
    public byte[] readObject(String platform, String objectKey) {
        if (platform == null || objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源定位信息不完整");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            fileStorageService.download(fileInfo(platform, objectKey)).outputStream(buffer);
        } catch (Exception e) {
            log.error("按资源记录读取失败: platform={}, key={}", platform, objectKey, e);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "资源内容不可用");
        }
        return limited(buffer, objectKey);
    }

    /**
     * 读取没有资源记录的存量对象。
     *
     * <p>仅供 {@code PictureContentService} 在授权通过后使用；这里不做归属判断，
     * 也不会把地址登记为新资源。
     */
    public byte[] readLegacyObject(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源地址为空");
        }
        try (InputStream inputStream = getObjectByUrl(url)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            inputStream.transferTo(new LimitedOutputStream(buffer, MAX_CONTENT_BYTES));
            return buffer.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("存量资源读取失败: url={}", url, e);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "资源内容不可用");
        }
    }

    private byte[] limited(ByteArrayOutputStream buffer, String objectKey) {
        byte[] content = buffer.toByteArray();
        if (content.length > MAX_CONTENT_BYTES) {
            log.warn("资源内容超出读取上限: key={}, size={}", objectKey, content.length);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "资源内容超出读取上限");
        }
        return content;
    }

    /**
     * 删除文件。
     *
     * <p>保留旧的按 key 删除入口，供未登记资源记录的兼容路径使用；
     * 新代码应使用 {@link #deleteObject(String, String)} 精确指定平台与对象键。
     */
    public boolean deleteObject(String key) {
        try {
            return fileStorageService.delete(key);
        } catch (Exception e) {
            log.error("文件删除失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 按资源记录删除对象。平台与对象键都来自服务端记录，不解析客户端地址。
     *
     * @return 对象是否确认删除；平台缺失或存储异常返回 false，由调用方保留记录以便重试
     */
    public boolean deleteObject(String platform, String objectKey) {
        if (platform == null || objectKey == null || objectKey.isBlank()) {
            log.warn("拒绝删除缺少平台或对象键的资源: platform={}, key={}", platform, objectKey);
            return false;
        }
        try {
            return fileStorageService.delete(fileInfo(platform, objectKey));
        } catch (Exception e) {
            log.error("按资源记录删除失败: platform={}, key={}", platform, objectKey, e);
            return false;
        }
    }

    /**
     * 构造只含平台与对象键的 FileInfo。
     *
     * <p>x-file-storage 的对象键等于 basePath + path + filename，这里把完整键放在
     * path 上，得到与原上传完全一致的定位结果。
     */
    private FileInfo fileInfo(String platform, String objectKey) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setPlatform(platform);
        fileInfo.setPath(objectKey);
        return fileInfo;
    }

    /**
     * 由 FileInfo 还原对象键（basePath + path + filename）。
     */
    public static String objectKey(FileInfo fileInfo) {
        if (fileInfo == null) {
            return null;
        }
        return nullToEmpty(fileInfo.getBasePath()) + nullToEmpty(fileInfo.getPath())
                + nullToEmpty(fileInfo.getFilename());
    }

    /**
     * 由 FileInfo 还原缩略图对象键。
     */
    public static String thumbnailObjectKey(FileInfo fileInfo) {
        if (fileInfo == null || fileInfo.getThFilename() == null || fileInfo.getThFilename().isBlank()) {
            return null;
        }
        return nullToEmpty(fileInfo.getBasePath()) + nullToEmpty(fileInfo.getPath())
                + fileInfo.getThFilename();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 按扩展名推断内容类型，无法识别时使用通用二进制类型。
     */
    public static String contentTypeOf(String objectKey) {
        String lower = objectKey == null ? "" : objectKey.toLowerCase();
        int dot = lower.lastIndexOf('.');
        String suffix = dot >= 0 ? lower.substring(dot + 1) : "";
        return switch (suffix) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}
