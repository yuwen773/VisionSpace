package com.yuwen.visionspace.agent.tools;

/**
 * 图片生成没有得到可用产物。
 *
 * <p>单独一个类型是为了让动作台账能区分「生成明确失败」与「生成结果未知」：
 * 调用已经在服务端受理但本地超时时，异常类型与「请求根本没发出去」不同，
 * 不能一律当成可安全重试。
 */
public class ImageGenerationException extends RuntimeException {

    /** 没有底层异常的情况（例如调用成功但没返回可用产物） */
    public ImageGenerationException(String message) {
        super(message);
    }

    public ImageGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
