package com.yuwen.visionspace.model.dto.feedback;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 添加反馈请求。
 *
 * <p>附件只能通过上传接口登记并提交标识；请求中的原始地址不会被采纳，
 * 也不会被当作附件归属依据。
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
     * 已上传附件的标识列表
     *
     * <p>按十进制字符串传输：附件标识是雪花值，超出 JavaScript 的安全整数范围，
     * 客户端转成 number 会丢精度，服务端收到后按字符串解析。
     */
    private List<String> attachmentIds;

    /**
     * 旧版客户端提交的原始地址。保留字段仅为明确拒绝，不作为附件依据。
     */
    private List<String> pictureUrls;

    private static final long serialVersionUID = 1L;
}
