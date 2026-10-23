package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 违规处置视图。
 *
 * <p>后台只展示处置所需的元数据：目标标识、类型、状态、操作者与理由。
 * 不返回原图、缩略图、预览地址或存储定位信息，查看内容必须经过资源方授权。
 */
@Data
public class ModerationResourceVO implements Serializable {

    private String resourceType;

    private Long resourceId;

    /** 资源展示名（仅名称，不含内容地址） */
    private String resourceName;

    /** QUARANTINED / RELEASED */
    private String status;

    private Long operatorId;

    private String operatorUserName;

    private String reason;

    private Date createTime;

    private Long releaseOperatorId;

    private String releaseOperatorUserName;

    private String releaseReason;

    private Date releaseTime;

    /** 该资源的完整处置历史（含已恢复） */
    private List<ModerationResourceVO> history;

    private static final long serialVersionUID = 1L;
}
