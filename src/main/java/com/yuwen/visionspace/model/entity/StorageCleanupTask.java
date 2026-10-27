package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 对象存储清理任务。
 *
 * <p>存储里的对象不能随数据库事务回滚：数据库失败之后，已经写进去的新对象、
 * 已经确认不再被引用的旧对象都要在事务之外删除。删除本身可能失败（网络、
 * 权限、平台限流），失败不能只写一行日志就算结束——这里保留一条可重试、
 * 可观察的记录，由清理任务按退避时间重试，直到对象删除被确认。
 *
 * <p>重试前必须重新核对引用：资源可能在上次失败之后又被重新绑定，或者同一个
 * 对象键被多条记录引用。核对不通过的任务标记为 {@link #STATUS_BLOCKED}，
 * 留给人工判断，绝不按"看起来像"的地址删除对象。
 */
@Data
@TableName("storage_cleanup_task")
public class StorageCleanupTask implements Serializable {

    /** 待重试 */
    public static final int STATUS_PENDING = 0;

    /** 对象删除已确认 */
    public static final int STATUS_DONE = 1;

    /** 引用复核不通过，暂停自动重试，等待人工处理 */
    public static final int STATUS_BLOCKED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资源记录 id；上传失败时还没有记录，为空 */
    private Long pictureFileId;

    /** 图片 id；上传失败或临时资源为空 */
    private Long pictureId;

    /** 存储平台标识，来自服务端记录 */
    private String platform;

    /** 存储对象键，来自服务端记录 */
    private String objectKey;

    /** 内容类型：original / thumbnail / preview */
    private String kind;

    /** 触发清理的原因，便于排查 */
    private String reason;

    /** {@link #STATUS_PENDING} / {@link #STATUS_DONE} / {@link #STATUS_BLOCKED} */
    private Integer status;

    /** 已尝试次数 */
    private Integer attempts;

    /** 下次重试时间，由数据库时钟写入 */
    private Date nextRetryTime;

    /** 最近一次失败或阻断的原因（不含秘密值） */
    private String lastError;

    /**
     * 待处理唯一键：{@code platform|kind|objectKey}。
     *
     * <p>唯一索引只约束非空值：进入终态时置空，因此同一个对象被再次清理时
     * 可以重新排队，而不会与历史任务冲突。
     */
    private String pendingKey;

    private Date createTime;

    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
