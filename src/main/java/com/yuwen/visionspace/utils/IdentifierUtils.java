package com.yuwen.visionspace.utils;

import cn.hutool.core.util.StrUtil;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import com.yuwen.visionspace.exception.ThrowUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 标识解析。
 *
 * <p>接口上的标识按十进制字符串传输：雪花标识超出 JavaScript 的安全整数范围，
 * 客户端一旦转成 number 就会丢精度，服务端因此只接受字符串形式的标识。
 */
public final class IdentifierUtils {

    private IdentifierUtils() {
    }

    /**
     * 解析单个标识。非法输入按参数错误拒绝，不做静默截断。
     *
     * @param raw   标识原文
     * @param label 报错时使用的名称
     * @return 标识数值
     */
    public static long parseId(String raw, String label) {
        ThrowUtils.throwIf(StrUtil.isBlank(raw), ErrorCode.PARAMS_ERROR, "缺少" + label);
        long id;
        try {
            id = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, label + "格式不正确");
        }
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, label + "不合法");
        return id;
    }

    /**
     * 解析标识列表。任一标识非法即整体拒绝，避免部分绑定。
     *
     * @param raw   标识原文列表
     * @param label 报错时使用的名称
     * @return 标识数值列表
     */
    public static List<Long> parseIds(Collection<String> raw, String label) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(raw.size());
        for (String item : raw) {
            ids.add(parseId(item, label));
        }
        return ids;
    }
}
