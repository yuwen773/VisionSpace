package com.yuwen.visionspace.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent 完整执行状态（版本化）。
 *
 * <p>与 {@code agent_session.checkpointData} 的区别：后者是仅含消息的轻量归档，
 * 只能用于历史展示；本表的 {@code statePayload} 是可继续执行的完整状态，
 * 带格式版本与单调递增的 {@code stateVersion}，写入按版本做 CAS，
 * 迟到的旧备份不会覆盖新状态。
 */
@TableName(value = "agent_run_state")
@Data
public class AgentRunState implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话 id */
    private String threadId;

    /** 会话归属用户；读取与恢复只认它 */
    private Long userId;

    /** 状态版本，单调递增 */
    private Long stateVersion;

    /** 状态格式版本，例如 v2-full */
    private String stateFormat;

    /** 完整恢复点 id */
    private String checkpointId;

    /** 已完成的节点 */
    private String nodeId;

    /** 下一步应执行的节点 */
    private String nextNodeId;

    /** 完整状态（gzip 后的序列化数据） */
    private byte[] statePayload;

    /** RUNNING / AWAITING_CONFIRMATION / COMPLETED / FAILED / PAUSED_UNRECOVERABLE */
    private String runStatus;

    /** 待人工确认信息（已脱敏） */
    private String pendingConfirmation;

    /** 1 = 仅有旧轻量归档，可查看但不可续跑 */
    private Integer legacyArchiveOnly;

    /** 最近一次备份失败原因（不含正文/秘密） */
    private String backupError;

    /** 备份尝试次数 */
    private Integer backupAttempts;

    private Date createTime;

    private Date updateTime;
}
