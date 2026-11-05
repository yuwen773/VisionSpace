package com.yuwen.visionspace.model.dto.agent;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 偏好显式设置修改请求
 */
@Data
public class PreferenceUpdateRequest implements Serializable {

    /**
     * 显式设置内容。只接受白名单键（liked_styles / disliked_styles /
     * liked_colors / notes），其余键被忽略而不是保存。
     */
    private Map<String, Object> explicit;
}
