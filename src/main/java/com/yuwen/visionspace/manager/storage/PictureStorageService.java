package com.yuwen.visionspace.manager.storage;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.net.URI;

/**
 * 统一存储服务，底层使用 x-file-storage
 */
@Slf4j
@Component
public class PictureStorageService {

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
     * 通过公网 URL 下载文件
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
     * 删除文件
     */
    public boolean deleteObject(String key) {
        try {
            return fileStorageService.delete(key);
        } catch (Exception e) {
            log.error("文件删除失败: key={}", key, e);
            return false;
        }
    }
}
