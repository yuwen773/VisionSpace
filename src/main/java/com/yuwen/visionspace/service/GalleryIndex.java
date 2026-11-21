package com.yuwen.visionspace.service;

import org.springframework.ai.vectorstore.filter.Filter;

/**
 * 站内图库索引与检索共享的授权接缝。
 *
 * <p>索引只收「审核通过的公开图片」：{@code spaceId} 为空的图片，写入向量库时
 * 记为 {@link #GALLERY_SPACE_ID}。查询侧可见范围因此是同一个常量，而不是模型
 * 填写的参数——否则模型可以自己声明「我是管理员」来去掉过滤。
 *
 * <p>两侧都用这里的常量与条件：索引改了收录范围，检索不会还按旧范围过滤。
 */
public final class GalleryIndex {

    /** 向量库元数据里的空间字段 */
    public static final String META_SPACE_ID = "spaceId";

    /** 向量库元数据里的归属用户字段 */
    public static final String META_USER_ID = "userId";

    /** 公开图库在元数据里的空间标识（图片真实 spaceId 为空时记为它） */
    public static final long GALLERY_SPACE_ID = 0L;

    private GalleryIndex() {
    }

    /** 检索条件：只返回公开图库内容。 */
    public static Filter.Expression publicGalleryFilter() {
        return new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key(META_SPACE_ID),
                new Filter.Value(GALLERY_SPACE_ID));
    }
}
