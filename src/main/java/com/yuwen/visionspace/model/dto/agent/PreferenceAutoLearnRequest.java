package com.yuwen.visionspace.model.dto.agent;

import lombok.Data;

import java.io.Serializable;

/**
 * 偏好自动学习开关请求
 */
@Data
public class PreferenceAutoLearnRequest implements Serializable {

    /** true-开启自动学习，false-关闭 */
    private Boolean enabled;
}
