package com.yuwen.visionspace.model.dto.feedback;

import lombok.Data;
import java.io.Serializable;

/**
 * 更新反馈状态请求
 */
@Data
public class FeedbackUpdateStatusRequest implements Serializable {

    /**
     * 反馈ID
     */
    private Long id;

    /**
     * 处理状态：0=待处理 1=处理中 2=已解决
     */
    private Integer status;

    /**
     * 管理员处理备注
     */
    private String handlerNote;

    private static final long serialVersionUID = 1L;
}
