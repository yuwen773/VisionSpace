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
 *
 * <p>本表不使用全局 {@code isDelete} 逻辑删除：{@link #status} 需要区分
 * TEMP / ACTIVE / DELETED / SUPERSEDED 四种状态，两态的逻辑删除表达不了
 * "已替换"与"已删除"的差别。查询一律显式带 status 条件。
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

    /**
     * 删除任务状态：0=无删除任务 1=对象删除未确认待重试 2=删除已确认。
     *
     * <p>对象存储删除失败时保留可重试状态，清理任务按退避时间重试；不静默丢弃。
     */
    private Integer deleteState;

    /** 删除重试次数 */
    private Integer deleteRetryCount;

    /** 下次重试时间 */
    private Date deleteNextRetryTime;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
