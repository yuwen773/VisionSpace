package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片资源记录。
 *
 * <p>一次上传的每个对象（原图、缩略图、预览）各占一条记录，记录可信的所有者、
 * 存储平台与对象键。删除、替换与内容读取都以本表为准，而不是解析客户端传来的 URL。
 */
@Data
@TableName("picture_file")
public class PictureFile implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所有者用户 id，来自服务端会话 */
    private Long ownerUserId;

    /** 空间 id；公共图库为 null */
    private Long spaceId;

    /** 绑定后的图片 id；临时上传为 null */
    private Long pictureId;

    /** {@link com.yuwen.visionspace.model.enums.PictureFilePurposeEnum} */
    private String purpose;

    /** 内容类型：original / thumbnail / preview */
    private String kind;

    /** 存储平台标识 */
    private String platform;

    /** 存储对象键，含平台 basePath */
    private String objectKey;

    /** 上传时返回的地址，仅作记录与兼容，不作为删除依据 */
    private String url;

    private Long fileSize;

    /** {@link com.yuwen.visionspace.model.enums.PictureFileStatusEnum} */
    private Integer status;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
