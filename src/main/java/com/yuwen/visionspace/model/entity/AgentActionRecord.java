package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent 外部动作台账。
 *
 * <p>只记录"能不能核对"，不记录正文：参数与结果都只保存摘要，
 * 审计和错误信息不会泄露私人会话内容或秘密。
 *
 * <p>{@code actionId} 由会话与工具调用 id 派生，同一动作重复登记是空操作；
 * 已确认完成（{@link #STATUS_SUCCEEDED}）的动作不会再执行。
 */
@TableName(value = "agent_action_record")
@Data
public class AgentActionRecord implements Serializable {

    /** 已发出、结果未知：可能是外部完成而本地未提交 */
    public static final String STATUS_EXECUTING = "EXECUTING";
    /** 已确认完成 */
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    /** 明确失败（可以安全重试） */
    public static final String STATUS_FAILED = "FAILED";
    /** 结果未知且不能安全重试，等待有权用户核对 */
    public static final String STATUS_UNKNOWN_NEEDS_REVIEW = "UNKNOWN_NEEDS_REVIEW";

    /** 可证明幂等，允许结果未知时安全重试 */
    public static final String IDEMPOTENCY_PROVEN = "PROVEN";
    /** 不能证明幂等，结果未知时只能进入待核对 */
    public static final String IDEMPOTENCY_NOT_PROVEN = "NOT_PROVEN";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String threadId;

    private Long userId;

    /** 可核对的动作标识 */
    private String actionId;

    private String toolName;

    /** 参数摘要（sha-256），不保存正文 */
    private String argumentsDigest;

    private String status;

    /** PROVEN / NOT_PROVEN */
    private String idempotency;

    private Integer attempt;

    /** 结果摘要（截断，不含正文） */
    private String resultSummary;

    private Date createTime;

    private Date updateTime;
}
