package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 空间
 * @TableName space
 */
@TableName(value ="space")
@Data
public class Space implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;

    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;

    /**
     * 当前空间下图片的总大小
     */
    private Long totalSize;

    /**
     * 当前空间下的图片数量
     */
    private Long totalCount;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 有效空间唯一占位键：同一创建者的同一空间类型只允许一条有效记录。
     *
     * <p>取值为 {@code userId:spaceType}，空间被删除时置空。唯一索引只约束
     * 非空值，因此删除后可以重新创建、反复重建也不会被历史墓碑挡住。
     *
     * <p>不能用 {@code (userId, spaceType)} 唯一索引代替：逻辑删除只是把
     * isDelete 改成 1，行还在表里，第二次删除或重建必然冲突。
     *
     * <p>这是内部唯一性占位，不是业务字段：{@code /space/get} 与
     * {@code /space/list/page} 直接返回本实体，不屏蔽就会把它写进对外契约。
     */
    @JsonIgnore
    private String activeKey;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}