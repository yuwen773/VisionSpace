package com.yuwen.visionspace.common;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

/**
 * JSON 媒体类型判定。
 *
 * <p>合法 JSON 请求允许携带 charset 等参数（如 {@code application/json;charset=UTF-8}），
 * 按原始字符串比较会把这类请求误判成表单参数请求，从而让鉴权上下文解析出错。
 * 过滤器与权限解析必须共用这里的判定，保证读到的请求体语义一致。
 */
public final class JsonContentType {

    private JsonContentType() {
    }

    /**
     * @param contentType 原始 Content-Type 请求头
     * @return 是否可当作 JSON 请求体处理；空值或无法解析时返回 false
     */
    public static boolean isJson(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        try {
            return MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON);
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
