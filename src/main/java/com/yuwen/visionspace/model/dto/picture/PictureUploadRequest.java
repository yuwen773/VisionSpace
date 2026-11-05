package com.yuwen.visionspace.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片上传请求
 *
 * @author yuwen
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 文件地址
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 空间 id
     */
    private Long spaceId;

    /** 协作编辑连接的保存凭据。 */
    private String collaborationToken;

    private static final long serialVersionUID = 1L;
}
