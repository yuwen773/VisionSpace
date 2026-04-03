package com.yuwen.visionspace.agent.model;

import lombok.Getter;

/**
 * Agent 迭代阶段枚举
 */
@Getter
public enum AgentPhase {

    EXPLORATION("探索阶段：搜索站内/站外图片"),
    GENERATION("生成阶段：AIGC 生成图片"),
    DONE("完成：用户满意");

    private final String text;

    AgentPhase(String text) {
        this.text = text;
    }
}
