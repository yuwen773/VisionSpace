package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 公开图库索引的待重试任务。
 *
 * <p>索引写入发生在数据库事务之外：向量库不可达、embedding 调用失败或进程退出时，
 * 数据库里的图片状态与向量库里的内容会不一致。只写一行日志不算收敛——那样这类
 * 分歧会一直留着，直到有人手工处理。这里为失败的同步留一条记录，由重试任务按
 * 退避时间重做，直到索引与数据库一致。
 *
 * <p>一条记录对应一张图片：重试时重新读数据库的当前状态再决定覆盖还是移除，
 * 因此中途发生的编辑、撤审与删除都会被下一次重试带上。
 */
@Data
@TableName("picture_index_task")
public class PictureIndexTask implements Serializable {

    /** 同步已完成，索引与数据库一致 */
    public static final int STATUS_SUCCEEDED = 0;

    /** 同步失败，等待重试 */
    public static final int STATUS_FAILED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 图片 id */
    private Long pictureId;

    /** 最近一次尝试的动作：UPSERT / DELETE */
    private String operation;

    /** {@link #STATUS_SUCCEEDED} / {@link #STATUS_FAILED} */
    private Integer status;

    /** 已尝试次数 */
    private Integer attempts;

    /** 下次重试时间，由数据库时钟写入 */
    private Date nextRetryTime;

    /** 最近一次失败的原因（不含秘密值） */
    private String lastError;

    private Date createTime;

    private Date updateTime;
}
