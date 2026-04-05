package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 通用文件上传服务，封装校验 + 文件名生成 + 存储调用
 */
@Slf4j
@Component
public class FileUploadService {

    @Resource
    private PictureStorageService pictureStorageService;

    /**
     * 上传文件（自定义校验参数）
     *
     * @param file           上传的文件
     * @param pathPrefix     存储路径前缀，如 "/temp/123/" 或 "/agent-chat/2026-04-05/"
     * @param maxSizeBytes   最大允许文件大小（字节）
     * @param allowedFormats 允许的文件扩展名列表（小写），如 ["jpeg","png","jpg","webp"]
     * @return 文件访问 URL
     */
    public String uploadFile(MultipartFile file, String pathPrefix,
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
            String url = pictureStorageService.putObject(tempFile, pathPrefix, filename).getUrl();
            log.info("文件上传成功: path={}, url={}", pathPrefix + filename, url);
            return url;
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
     * 上传图片文件（默认校验：2MB, jpeg/png/jpg/webp）
     *
     * @param file       上传的文件
     * @param pathPrefix 存储路径前缀
     * @return 文件访问 URL
     */
    public String uploadImageFile(MultipartFile file, String pathPrefix) {
        return uploadFile(file, pathPrefix, 2L * 1024 * 1024,
                Arrays.asList("jpeg", "png", "jpg", "webp"));
    }

    /**
     * 通过 URL 删除文件
     *
     * @param fileUrl                文件公网 URL
     * @param pathPrefixSafetyCheck  安全校验前缀，非 null 时验证存储 key 必须以此开头
     */
    public void deleteFileByUrl(String fileUrl, String pathPrefixSafetyCheck) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址不能为空");
        }
        try {
            URI uri = new URI(fileUrl);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的文件地址");
            }
            String key = path.startsWith("/") ? path.substring(1) : path;
            if (pathPrefixSafetyCheck != null && !key.startsWith(pathPrefixSafetyCheck)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的文件路径");
            }
            boolean deleted = pictureStorageService.deleteObject(key);
            log.info("文件删除: key={}, success={}", key, deleted);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件删除失败: fileUrl={}", fileUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败");
        }
    }

    private String getFileExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件格式错误");
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
    }
}
