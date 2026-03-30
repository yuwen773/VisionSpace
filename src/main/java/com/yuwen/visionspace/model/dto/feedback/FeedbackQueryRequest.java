package com.yuwen.visionspace.model.dto.feedback;

import com.yuwen.visionspace.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;

/**
 * 反馈查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FeedbackQueryRequest extends PageRequest implements Serializable {

    /**
     * 反馈类型：1=产品建议 2=内容举报 3=工单支持
     */
    private Integer type;

    /**
     * 处理状态：0=待处理 1=处理中 2=已解决
     */
    private Integer status;

    private static final long serialVersionUID = 1L;
}
