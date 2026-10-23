package com.yuwen.visionspace.model.enums;

import lombok.Getter;

/**
 * 反馈附件状态。
 *
 * <p>UPLOADED 是已上传但尚未随反馈提交；BOUND 表示已绑定到可信的反馈记录；
 * LEGACY_VERIFIED 是人工核对归属后登记的旧附件；BLOCKED 是归属无法确认的旧附件，
 * 保留记录但不提供读取入口，不自动认领也不删除。
 */
@Getter
public enum FeedbackAttachmentStatusEnum {

    UPLOADED("已上传", 0),
    BOUND("已绑定", 1),
    LEGACY_VERIFIED("旧附件已核对", 2),
    BLOCKED("归属待核对", 3);

    private final String text;

    private final int value;

    FeedbackAttachmentStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    public static FeedbackAttachmentStatusEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (FeedbackAttachmentStatusEnum item : values()) {
            if (item.value == value) {
                return item;
            }
        }
        return null;
    }
}
