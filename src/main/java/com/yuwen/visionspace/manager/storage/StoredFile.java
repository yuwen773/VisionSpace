package com.yuwen.visionspace.manager.storage;

/**
 * 一次上传落地的结果。
 *
 * <p>存储位置由平台与对象键共同确定，调用方要登记资源时直接取用，
 * 不再从 URL 反解。
 *
 * @param url       可访问地址
 * @param platform  实际上传的存储平台
 * @param objectKey 完整对象键（含平台 basePath）
 * @param filename  落盘文件名
 * @param size      字节数
 */
public record StoredFile(String url, String platform, String objectKey, String filename, long size) {
}
