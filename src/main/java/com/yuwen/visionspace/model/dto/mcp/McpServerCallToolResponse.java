package com.yuwen.visionspace.model.dto.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 调用 MCP 服务器工具响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpServerCallToolResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否错误
     */
    private Boolean isError;

    /**
     * 内容列表
     */
    private List<Content> content;

    /**
     * 内容基类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 内容类型
         */
        private String type;
    }

    /**
     * 文本内容
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextContent extends Content {

        private static final long serialVersionUID = 1L;

        /**
         * 文本内容
         */
        private String text;
    }
}
