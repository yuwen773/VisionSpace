package com.yuwen.visionspace.rag;

/**
 * 站内检索的开关键。
 *
 * <p>{@code spring.ai.vectorstore.type=chroma} 的字面量此前在五个模块里各写一遍
 * （装配、健康信号、管理接口、索引服务、检索工具）。改开关名或取值时漏掉任何一处，
 * 症状是「启动正常但检索工具不存在」这类难查的问题，所以集中到这里。
 */
public final class RagVectorStore {

    /** 开关所在的配置键 */
    public static final String PROPERTY = "spring.ai.vectorstore.type";

    /** 打开站内检索时该键的取值 */
    public static final String CHROMA = "chroma";

    private RagVectorStore() {
    }
}
