package com.yuwen.visionspace.agent.model;

import lombok.Getter;

/**
 * 意图匹配判定结果。
 * <p>
 * 区分「判断为不匹配」与「无法判断」两种语义：前者说明搜到的图确实不对，
 * 应该重搜或改用 AIGC；后者只是描述信息不足以判断，应该先给用户看。
 * 两者在 matchScore 上都可能落成中性分，因此需要单独的字段承载。
 */
@Getter
public enum MatchVerdict {

    /**
     * 有明确证据表明图片符合意图
     */
    MATCHED("图片描述与意图相符"),

    /**
     * 有明确证据表明图片不符合意图
     */
    MISMATCHED("图片与意图不符"),

    /**
     * 描述信息不足以判断（典型场景：中文意图 + 英文图库描述）
     */
    UNKNOWN("无法从描述判断匹配度");

    private final String text;

    MatchVerdict(String text) {
        this.text = text;
    }
}
