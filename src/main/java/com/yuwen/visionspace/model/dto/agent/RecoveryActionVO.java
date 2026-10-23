package com.yuwen.visionspace.model.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 动作台账的对外视图。
 *
 * <p>只给可核对的信息：标识、工具名、状态与结果摘要。
 * 不返回参数原文或会话正文。
 */
@Data
public class RecoveryActionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String actionId;

    private String toolName;

    /** EXECUTING / SUCCEEDED / FAILED / UNKNOWN_NEEDS_REVIEW */
    private String status;

    private Integer attempt;

    /** 截断后的结果摘要，不含正文 */
    private String resultSummary;

    private Date updateTime;
}
