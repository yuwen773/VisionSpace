package com.yuwen.visionspace.model.enums;

import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 存储平台枚举类
 */
@Getter
public enum StoragePlatformEnum {

    MINIO("minio", "MinIO"),
    COS("cos", "腾讯云COS"),
    OSS("oss", "阿里云OSS"),
    OBS("obs", "华为云OBS");

    private final String code;

    private final String text;

    StoragePlatformEnum(String code, String text) {
        this.code = code;
        this.text = text;
    }

    private static final Map<String, StoragePlatformEnum> CODE_MAP;

    static {
        CODE_MAP = Stream.of(values())
                .collect(Collectors.toMap(StoragePlatformEnum::getCode, e -> e, (a, b) -> a));
    }

    /**
     * 根据 code 获取枚举
     */
    public static StoragePlatformEnum getEnumByCode(String code) {
        if (code == null) {
            return null;
        }
        return CODE_MAP.get(code);
    }
}
