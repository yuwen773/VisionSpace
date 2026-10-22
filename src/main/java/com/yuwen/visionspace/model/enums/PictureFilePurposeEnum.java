package com.yuwen.visionspace.model.enums;

import lombok.Getter;

/**
 * 资源记录的用途。
 *
 * <p>图库上传与 Agent 临时上传使用同一套归属记录，但生命周期不同：
 * 图库文件随图片记录生效/替换，Agent 文件先临时存在、绑定会话后才保留。
 */
@Getter
public enum PictureFilePurposeEnum {

    GALLERY("gallery", "图库"),
    AGENT_CHAT("agent-chat", "Agent 会话");

    private final String value;

    private final String text;

    PictureFilePurposeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    public static PictureFilePurposeEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (PictureFilePurposeEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }
}
