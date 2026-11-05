package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户长期偏好。
 *
 * <p>每个用户一行（{@code uk_userId}），跨重启与双实例读取一致。
 * 三块内容分列存储：{@code explicitContent} 是用户直接编辑的显式设置，
 * 自动学习永远不改写它；{@code learnedContent} 是反馈推断产物，可被清除；
 * {@code feedbackHistory} 是反馈历史（条数有上限）。
 *
 * <p>并发语义见 {@link com.yuwen.visionspace.mapper.UserPreferenceMapper}：
 * 学习写入按 {@code (preferenceVersion, dataEpoch)} CAS，用户的每一次控制
 * 操作都递增两个值——清除或关闭之后，在途的旧学习任务写不回已清除的数据。
 */
@TableName(value = "user_preference")
@Data
public class UserPreference implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 偏好归属用户；全部读写条件都带它 */
    private Long userId;

    /** 显式设置 JSON（用户直接编辑，优先于推断） */
    private String explicitContent;

    /** 自动学习 JSON（反馈推断，可清除） */
    private String learnedContent;

    /** 反馈历史 JSON（条数有上限） */
    private String feedbackHistory;

    /** 自动学习开关: 0-关闭（默认） 1-开启 */
    private Integer autoLearn;

    /** 偏好版本，单调递增，学习写入按它 CAS */
    private Long preferenceVersion;

    /** 数据纪元，用户控制操作递增，旧学习任务据此失效 */
    private Long dataEpoch;

    /** 最近一次学习放弃原因（短码）；学习成功或用户控制操作时清空 */
    private String learnError;

    private Date createTime;

    private Date updateTime;
}
