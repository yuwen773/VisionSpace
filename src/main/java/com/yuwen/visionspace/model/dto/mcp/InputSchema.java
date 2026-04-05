package com.yuwen.visionspace.model.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 工具输入模式 (JSON Schema 格式)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputSchema implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 类型
     */
    private String type;

    /**
     * 属性映射
     */
    private Map<String, Object> properties;

    /**
     * 必填字段列表
     */
    private List<String> required;
}
