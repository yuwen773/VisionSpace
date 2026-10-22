package com.yuwen.visionspace.manager.storage;

/**
 * 存储凭据脱敏与"未修改"判定。
 *
 * <p>响应中只出现 {@link #MASK}；请求中带回 {@link #MASK} 或留空表示保留原值，
 * 只有实际不同的值才视为显式替换，避免把遮蔽文本写回数据库。
 */
public final class StorageCredentialMasker {

    public static final String MASK = "******";

    private StorageCredentialMasker() {
    }

    /** 已配置返回固定遮蔽值，未配置返回 null，便于前端区分两种状态。 */
    public static String mask(String secret) {
        return secret == null || secret.isBlank() ? null : MASK;
    }

    /**
     * 判断请求是否要求替换该凭据。
     *
     * <p>null、空白、遮蔽值都表示"不修改"；其余值视为显式替换。
     */
    public static boolean isReplacement(String submitted) {
        if (submitted == null) {
            return false;
        }
        String value = submitted.trim();
        return !value.isEmpty() && !MASK.equals(value);
    }
}
