package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AgentImageUploadService {

    private static final String UPLOAD_PATH_PREFIX = "agent-chat";

    private static final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "png", "jpg", "webp");

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

    @Resource
    private PictureStorageService pictureStorageService;

    public String uploadImage(MultipartFile file) {
        // 1. 校验文件
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        long fileSize = file.getSize();
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 2MB");
        }
        String fileSuffix = getFileSuffix(file.getOriginalFilename());
        if (!ALLOW_FORMAT_LIST.contains(fileSuffix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误，仅支持 jpeg、png、jpg、webp");
        }

        // 2. 生成文件名：agent-chat/YYYY-MM-DD/{uuid}.{suffix}
        String dateStr = java.time.LocalDate.now().toString();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String filename = uuid + "." + fileSuffix;
        String uploadPath = UPLOAD_PATH_PREFIX + "/" + dateStr + "/";

        // 3. 转换为临时 File 并上传
        File tempFile = null;
        try (InputStream inputStream = file.getInputStream()) {
            tempFile = File.createTempFile("agent-upload-", "." + fileSuffix);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                inputStream.transferTo(fos);
            }
            FileInfo fileInfo = pictureStorageService.putObject(tempFile, uploadPath, filename);
            String url = fileInfo.getUrl();
            log.info("Agent 图片上传成功: url={}", url);
            return url;
        } catch (Exception e) {
            log.error("Agent 图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            if (tempFile != null && !tempFile.delete()) {
                log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址不能为空");
        }
        try {
            URI uri = new URI(imageUrl);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的图片地址");
            }
            // 去掉开头的 /，得到存储路径 key，如 agent-chat/2026-04-03/uuid.jpg
            String key = path.startsWith("/") ? path.substring(1) : path;
            // 安全校验：必须是 agent-chat 路径
            if (!key.startsWith(UPLOAD_PATH_PREFIX)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的图片路径");
            }
            boolean deleted = pictureStorageService.deleteObject(key);
            log.info("Agent 图片删除: key={}, success={}", key, deleted);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agent 图片删除失败: imageUrl={}", imageUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片删除失败");
        }
    }

    private String getFileSuffix(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
