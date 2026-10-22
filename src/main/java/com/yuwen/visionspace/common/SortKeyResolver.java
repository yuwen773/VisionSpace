package com.yuwen.visionspace.common;

import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;

import java.util.Locale;
import java.util.Map;

/**
 * 查询排序键白名单。
 *
 * <p>客户端只能提交这里登记的公开键，服务端负责映射到真实列名；
 * 任何未登记的值都按业务错误拒绝，不会进入 SQL 结构。
 */
public final class SortKeyResolver {

    /** 图片查询允许的排序键。 */
    public static final Map<String, String> PICTURE_KEYS = Map.of(
            "id", "id",
            "name", "name",
            "picSize", "picSize",
            "picWidth", "picWidth",
            "picHeight", "picHeight",
            "createTime", "createTime",
            "editTime", "editTime");

    /** 空间查询允许的排序键。 */
    public static final Map<String, String> SPACE_KEYS = Map.of(
            "id", "id",
            "spaceName", "spaceName",
            "spaceLevel", "spaceLevel",
            "totalSize", "totalSize",
            "totalCount", "totalCount",
            "createTime", "createTime",
            "editTime", "editTime");

    /** 用户查询允许的排序键。 */
    public static final Map<String, String> USER_KEYS = Map.of(
            "id", "id",
            "userName", "userName",
            "userRole", "userRole",
            "createTime", "createTime",
            "editTime", "editTime");

    /** 前端既有排序控件只发送这两个取值。 */
    private static final String ASCEND = "ascend";
    private static final String DESCEND = "descend";

    private SortKeyResolver() {
    }

    /**
     * 解析排序请求。
     *
     * @param allowed 该查询允许的公开键到列名映射
     * @param field   客户端提交的排序键，可为空
     * @param order   客户端提交的排序方向，可为空（默认降序）
     * @return 解析结果；键为空时 {@code column} 为 null，调用方据此跳过排序
     */
    public static Sort resolve(Map<String, String> allowed, String field, String order) {
        if (field == null || field.isBlank()) {
            return Sort.NONE;
        }
        String key = field.trim();
        String column = allowed.get(key);
        if (column == null) {
            // 不回显客户端输入，避免把可能的注入片段写进响应或日志
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的排序字段");
        }
        return new Sort(column, isAscending(order));
    }

    /**
     * 方向缺省按降序；非法方向按明确业务错误拒绝，不能静默改成别的顺序。
     */
    private static boolean isAscending(String order) {
        if (order == null || order.isBlank()) {
            return false;
        }
        String normalized = order.trim().toLowerCase(Locale.ROOT);
        if (ASCEND.equals(normalized)) {
            return true;
        }
        if (DESCEND.equals(normalized)) {
            return false;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的排序方向");
    }

    /** 解析后的排序：列名已来自白名单，可安全拼接。 */
    public record Sort(String column, boolean ascending) {
        public static final Sort NONE = new Sort(null, false);

        public boolean isEmpty() {
            return column == null;
        }
    }
}
