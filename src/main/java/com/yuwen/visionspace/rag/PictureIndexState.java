package com.yuwen.visionspace.rag;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;

/**
 * 公开图库索引的目标状态。
 *
 * <p>索引应当是什么，只由图片当前在数据库里的状态决定，不由调用方手里那个
 * 「改动前 / 改动后」的对象快照决定。历史上 {@code editPicture} 用编辑前的
 * 对象重建索引，导致一张已转入待审核的公开图片仍带着旧内容留在索引里，
 * 站内检索照样能召回它。
 *
 * <p>这里是唯一的判定入口：写入侧（{@code PictureEmbeddingServiceImpl}）与
 * 读取侧（{@code PublicGalleryIndexReader}）引用同一组判定，写入侧据此决定
 * 覆盖还是移除，读取侧据此决定命中是否仍然成立。两边各判一次是必要的：
 * 写入侧负责让索引收敛，读取侧负责在索引尚未收敛时也不越权。
 */
public final class PictureIndexState {

    /**
     * 索引应当采取的动作。
     */
    public enum Operation {
        /** 图片当前可公开检索：写入索引（同一文档标识覆盖旧内容）。 */
        UPSERT,
        /** 图片当前不可公开检索：从索引移除。 */
        DELETE
    }

    private PictureIndexState() {
    }

    /**
     * 图片是否属于公开图库。空间图片不进公开图库的索引。
     */
    public static boolean isPublicGallery(Picture picture) {
        return picture != null && picture.getId() != null && picture.getSpaceId() == null;
    }

    /**
     * 图片当前是否审核通过。
     */
    public static boolean isApproved(Picture picture) {
        return picture != null && picture.getReviewStatus() != null
                && picture.getReviewStatus() == PictureReviewStatusEnum.PASS.getValue();
    }

    /**
     * 依据图片的当前持久化状态决定索引应当是什么。
     *
     * @param picture     图片记录；为空表示图片已被删除
     * @param quarantined 图片当前是否处于隔离状态
     * @return 应当采取的动作
     */
    public static Operation decide(Picture picture, boolean quarantined) {
        if (picture == null) {
            // 图片已删除：索引里的旧文档必须移除
            return Operation.DELETE;
        }
        if (quarantined) {
            // 隔离中的图片不对外展示，索引里也不保留
            return Operation.DELETE;
        }
        if (!isPublicGallery(picture)) {
            return Operation.DELETE;
        }
        if (!isApproved(picture)) {
            // 待审核与被拒绝的公开图片都不是可检索内容
            return Operation.DELETE;
        }
        return Operation.UPSERT;
    }
}
