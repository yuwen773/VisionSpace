package com.yuwen.visionspace.model.dto.feedback;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 添加反馈请求
 */
@Data
public class FeedbackAddRequest implements Serializable {

    /**
     * 反馈类型：1=产品建议 2=内容举报 3=工单支持
     */
    private Integer type;

    /**
     * 反馈标题
     */
    private String title;

    /**
     * 反馈内容
     */
    private String content;

    /**
     * 附件图片URLs
     */
    private List<String> pictureUrls;

    private static final long serialVersionUID = 1L;
}
