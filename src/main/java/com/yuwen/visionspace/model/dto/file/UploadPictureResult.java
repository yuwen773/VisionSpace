package com.yuwen.visionspace.model.dto.file;

import lombok.Data;

/**
 * 上传图片的结果
 */
@Data
public class UploadPictureResult {

    /**
     * 图片地址
     */
    private String url;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

    /**
     * 原图对象键（含平台 basePath），用于资源记录
     */
    private String storagePath;

    /**
     * 缩略图对象键（含平台 basePath），用于资源记录
     */
    private String thumbnailObjectKey;

    /**
     * 实际保存的存储平台标识
     */
    private String platform;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private int picWidth;

    /**
     * 图片高度
     */
    private int picHeight;

    /**
     * 图片宽高比
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 图片主色调
     */
    private String picColor;
}
