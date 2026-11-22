package com.yuwen.visionspace.rag;

import com.yuwen.visionspace.model.entity.Picture;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.HashMap;
import java.util.Map;

/**
 * 站内图片索引的契约：文档标识、元数据键与可见范围条件。
 *
 * <p>写入方（{@link com.yuwen.visionspace.service.impl.PictureEmbeddingServiceImpl}）
 * 与读取方（{@code InternalImageSearchTool}）跨两个包，此前只靠约定共享
 * {@code picture_} 前缀与 {@code imageUrl} / {@code spaceId} 等键。任何一侧改名都不会
 * 报错，只会让检索悄悄返回空结果。现在两边都引用这里的常量与 {@link #document}，
 * 往返测试（{@code PictureIndexRoundTripTest}）会先于线上发现漂移。
 *
 * <p>索引只收「审核通过的公开图片」：{@code spaceId} 为空的图片，写入时记为
 * {@link #GALLERY_SPACE_ID}。查询侧的可见范围因此是同一个常量，而不是模型
 * 填写的参数——否则模型可以自己声明「我是管理员」来去掉过滤。
 */
public final class PictureIndex {

    /** 文档标识前缀：同一张图片反复索引时按 id 覆盖而不是堆积副本。 */
    public static final String DOC_ID_PREFIX = "picture_";

    public static final String META_PICTURE_ID = "pictureId";
    public static final String META_IMAGE_URL = "imageUrl";
    public static final String META_THUMBNAIL_URL = "thumbnailUrl";
    public static final String META_TITLE = "title";
    public static final String META_TAGS = "tags";
    public static final String META_CATEGORY = "category";
    public static final String META_PIC_COLOR = "picColor";
    public static final String META_SPACE_ID = "spaceId";
    public static final String META_USER_ID = "userId";
    public static final String META_INDEXED_AT = "indexedAt";

    /** 公开图库在元数据里的空间标识（图片真实 spaceId 为空时记为它） */
    public static final long GALLERY_SPACE_ID = 0L;

    private PictureIndex() {
    }

    /** 一张图片在向量库里的文档标识。 */
    public static String docId(Long pictureId) {
        return DOC_ID_PREFIX + pictureId;
    }

    /** 检索条件：只返回公开图库内容。 */
    public static Filter.Expression publicGalleryFilter() {
        return new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key(META_SPACE_ID),
                new Filter.Value(GALLERY_SPACE_ID));
    }

    /**
     * 构造一条索引文档。
     *
     * <p>文本（名称 + 简介 + 标签 + 视觉描述）由调用方拼好传入：拼法与视觉模型
     * 相关，属于写入方的细节；标识与元数据属于契约，留在这里。
     */
    public static Document document(Picture picture, String indexText) {
        return new Document(docId(picture.getId()), indexText, metadata(picture));
    }

    private static Map<String, Object> metadata(Picture picture) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_PICTURE_ID, picture.getId());
        metadata.put(META_IMAGE_URL, picture.getUrl() != null ? picture.getUrl() : "");
        metadata.put(META_THUMBNAIL_URL,
                picture.getThumbnailUrl() != null ? picture.getThumbnailUrl() : "");
        metadata.put(META_TITLE, picture.getName() != null ? picture.getName() : "");
        metadata.put(META_TAGS, picture.getTags() != null ? picture.getTags() : "[]");
        metadata.put(META_CATEGORY, picture.getCategory() != null ? picture.getCategory() : "");
        metadata.put(META_PIC_COLOR, picture.getPicColor() != null ? picture.getPicColor() : "");
        metadata.put(META_SPACE_ID,
                picture.getSpaceId() != null ? picture.getSpaceId() : GALLERY_SPACE_ID);
        metadata.put(META_USER_ID, picture.getUserId() != null ? picture.getUserId() : 0L);
        metadata.put(META_INDEXED_AT, System.currentTimeMillis());
        return metadata;
    }
}
