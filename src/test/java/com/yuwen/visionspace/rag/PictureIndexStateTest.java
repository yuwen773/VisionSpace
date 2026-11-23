package com.yuwen.visionspace.rag;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.model.enums.PictureReviewStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 索引目标状态的判定表。
 *
 * <p>写入侧与读取侧引用同一份判定：写入侧据此决定覆盖还是移除，读取侧据此决定
 * 命中是否仍然成立。两边各判一次是必要的——写入侧负责让索引收敛，读取侧负责
 * 在索引尚未收敛时也不越权。
 */
class PictureIndexStateTest {

    @Test
    void approvedPublicPictureShouldBeIndexed() {
        assertEquals(PictureIndexState.Operation.UPSERT,
                PictureIndexState.decide(picture(PictureReviewStatusEnum.PASS, null), false));
    }

    @Test
    void pendingPictureShouldNotBeIndexed() {
        assertEquals(PictureIndexState.Operation.DELETE,
                PictureIndexState.decide(picture(PictureReviewStatusEnum.REVIEWING, null), false));
    }

    @Test
    void rejectedPictureShouldNotBeIndexed() {
        assertEquals(PictureIndexState.Operation.DELETE,
                PictureIndexState.decide(picture(PictureReviewStatusEnum.REJECT, null), false));
    }

    @Test
    void pictureWithoutReviewStatusShouldNotBeIndexed() {
        assertEquals(PictureIndexState.Operation.DELETE,
                PictureIndexState.decide(picture(null, null), false));
    }

    @Test
    void spacePictureShouldNotBeIndexed() {
        assertEquals(PictureIndexState.Operation.DELETE,
                PictureIndexState.decide(picture(PictureReviewStatusEnum.PASS, 42L), false));
    }

    @Test
    void quarantinedPictureShouldNotBeIndexed() {
        assertEquals(PictureIndexState.Operation.DELETE,
                PictureIndexState.decide(picture(PictureReviewStatusEnum.PASS, null), true));
    }

    @Test
    void deletedPictureShouldNotBeIndexed() {
        // 图片已被删除：查不出来，索引里的旧文档必须移除
        assertEquals(PictureIndexState.Operation.DELETE, PictureIndexState.decide(null, false));
    }

    private Picture picture(PictureReviewStatusEnum status, Long spaceId) {
        Picture picture = new Picture();
        picture.setId(7L);
        picture.setSpaceId(spaceId);
        picture.setReviewStatus(status == null ? null : status.getValue());
        return picture;
    }
}
