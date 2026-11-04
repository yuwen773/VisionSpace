package com.yuwen.visionspace.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 用户长期偏好视图 VO
 *
 * <p>显式设置与自动学习分块返回，用户能看到模型学到了什么、自己设置了什么；
 * preferenceVersion 用于前端乐观展示，不作为授权依据。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreferenceViewVO implements Serializable {

    /** 显式设置（用户直接编辑，优先于推断） */
    private Map<String, Object> explicit;

    /** 自动学习产物（反馈推断） */
    private Map<String, Object> learned;

    /** 反馈历史（条数有上限） */
    private List<Map<String, Object>> feedbackHistory;

    /** 自动学习是否开启 */
    private Boolean autoLearn;

    /** 偏好版本，单调递增 */
    private Long preferenceVersion;

    /** 数据纪元，用户控制操作递增 */
    private Long dataEpoch;
}
