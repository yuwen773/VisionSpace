package com.yuwen.visionspace.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.manager.storage.PictureStorageService;
import com.yuwen.visionspace.model.dto.file.UploadPictureResult;
import com.yuwen.visionspace.utils.strategy.ColorExtractStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Date;

/**
 * 图片上传模板
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private PictureStorageService pictureStorageService;

    @Resource
    private ColorExtractStrategyFactory colorExtractStrategyFactory;

    /**
     * 上传图片
     *
     * @param inputSource      文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 校验图片
        validPicture(inputSource);
        // 2. 图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginFilename(inputSource);
        // 自己拼接文件上传路径，而不是使用原始文件名称，可以增强安全性
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;
        try {
            // 3. 创建临时文件，获取文件到服务器
            file = File.createTempFile(uploadPath, null);
            // 处理文件来源
            processFile(inputSource, file);
            // 4. 上传图片到对象存储
            FileInfo fileInfo = pictureStorageService.putPictureObject(file, uploadPath);
            // 5. 封装返回结果
            return buildResult(originalFilename, file, fileInfo);
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 6. 临时文件清理
            this.deleteTempFile(file);
        }

    }

    /**
     * 校验输入源（本地文件或 URL）
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 封装返回结果
     *
     * @param originalFilename 原始文件名
     * @param file              本地文件
     * @param fileInfo          x-file-storage 返回的文件信息
     * @return
     */
    private UploadPictureResult buildResult(String originalFilename, File file, FileInfo fileInfo) {
        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        // 设置 URL 和缩略图地址（x-file-storage 已经处理了 WebP 压缩和缩略图生成）
        uploadPictureResult.setUrl(fileInfo.getUrl());
        uploadPictureResult.setThumbnailUrl(fileInfo.getThUrl());
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(fileInfo.getSize());
        // 读取图片宽高
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                int picWidth = image.getWidth();
                int picHeight = image.getHeight();
                double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
                uploadPictureResult.setPicWidth(picWidth);
                uploadPictureResult.setPicHeight(picHeight);
                uploadPictureResult.setPicScale(picScale);
                // 提取图片主色调
                String picColor = colorExtractStrategyFactory.extractDominantColor(image);
                uploadPictureResult.setPicColor(picColor);
            }
        } catch (Exception e) {
            log.warn("读取图片宽高失败: {}", originalFilename, e);
        }
        // 设置图片格式
        uploadPictureResult.setPicFormat(FileUtil.getSuffix(originalFilename));
        // 返回可访问的地址
        return uploadPictureResult;
    }

    /**
     * 清理临时文件
     *
     * @param file
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        // 删除临时文件
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }
}













