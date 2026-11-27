package com.yuwen.visionspace.utils;

import com.yuwen.visionspace.model.entity.Picture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PictureReorderUtilsTest {

    // ========== Helper ==========

    private Picture createPicture(Long id, Long userId, String category) {
        Picture pic = new Picture();
        pic.setId(id);
        pic.setUserId(userId);
        pic.setCategory(category);
        return pic;
    }

    // ========== reorderByAuthor Tests ==========

    @Test
    void reorderByAuthor_nullList_returnsNull() {
        List<Picture> result = PictureReorderUtils.reorderByAuthor(null, 20, 2);
        assertNull(result);
    }

    @Test
    void reorderByAuthor_emptyList_returnsEmpty() {
        List<Picture> result = PictureReorderUtils.reorderByAuthor(Collections.emptyList(), 20, 2);
        assertTrue(result.isEmpty());
    }

    @Test
    void reorderByAuthor_singleElement_returnsSame() {
        Picture pic = createPicture(1L, 1L, "风景");
        List<Picture> input = Arrays.asList(pic);
        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 20, 2);

        assertEquals(1, result.size());
        assertEquals(pic, result.get(0));
    }

    @Test
    void reorderByAuthor_sameAuthorExceedsLimit_defersExcess() {
        // 同作者前两本留在原位，第三本延后到末尾；只断言长度无法区分「真的延后」和「完全没处理」
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 1L, "风景"),
            createPicture(3L, 1L, "风景"),
            createPicture(4L, 2L, "人物")
        );

        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 20, 2);

        assertEquals(Arrays.asList(1L, 2L, 4L, 3L),
            result.stream().map(Picture::getId).collect(Collectors.toList()));
    }

    @Test
    void reorderByAuthor_beyondWindowSize_noLimit() {
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 1L, "风景"),
            createPicture(3L, 2L, "人物"),
            createPicture(4L, 1L, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 3, 2);

        assertEquals(4, result.size());
        assertEquals(4L, result.get(3).getId());
    }

    @Test
    void reorderByAuthor_multipleAuthors_maintainsOrder() {
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 2L, "人物"),
            createPicture(3L, 1L, "风景"),
            createPicture(4L, 2L, "人物"),
            createPicture(5L, 1L, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 20, 2);

        assertEquals(5, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals(3L, result.get(2).getId());
        assertEquals(4L, result.get(3).getId());
        assertEquals(5L, result.get(4).getId());
    }

    @Test
    void reorderByAuthor_nullUserId_notCounted() {
        List<Picture> input = Arrays.asList(
            createPicture(1L, null, "风景"),
            createPicture(2L, null, "风景"),
            createPicture(3L, null, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 20, 2);

        assertEquals(3, result.size());
        // All null userId should be added directly without limit
    }

    @Test
    void reorderByAuthor_invalidParams_returnsOriginal() {
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 2L, "人物")
        );

        // windowSize <= 0
        List<Picture> result1 = PictureReorderUtils.reorderByAuthor(input, 0, 2);
        assertEquals(input, result1);

        // maxCount <= 0
        List<Picture> result2 = PictureReorderUtils.reorderByAuthor(input, 20, 0);
        assertEquals(input, result2);
    }

    // ========== reorderByCategory Tests ==========

    @Test
    void reorderByCategory_nullList_returnsNull() {
        List<Picture> result = PictureReorderUtils.reorderByCategory(null, 3);
        assertNull(result);
    }

    @Test
    void reorderByCategory_emptyList_returnsEmpty() {
        List<Picture> result = PictureReorderUtils.reorderByCategory(Collections.emptyList(), 3);
        assertTrue(result.isEmpty());
    }

    @Test
    void reorderByCategory_nullCategory_skipsCheck() {
        Picture pic1 = createPicture(1L, 1L, null);
        Picture pic2 = createPicture(2L, 1L, null);
        Picture pic3 = createPicture(3L, 1L, null);

        List<Picture> input = Arrays.asList(pic1, pic2, pic3);
        List<Picture> result = PictureReorderUtils.reorderByCategory(input, 3);

        assertEquals(3, result.size());
    }

    @Test
    void reorderByCategory_consecutiveSameCategory_reorders() {
        // 第 3 张连续「风景」触发打散：它被移到首个非风景元素之前，
        // 结果 [风景4, 人物1, 风景2, 风景3]——末尾只剩连续 2 张，未超过 maxConsecutive=3。
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "人物"),
            createPicture(2L, 2L, "风景"),
            createPicture(3L, 3L, "风景"),
            createPicture(4L, 4L, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByCategory(input, 3);

        assertEquals(Arrays.asList(4L, 1L, 2L, 3L),
            result.stream().map(Picture::getId).collect(Collectors.toList()));
        assertTrue(maxConsecutiveRun(result) <= 3, "打散后同类连续段不能超过阈值");
    }

    @Test
    void reorderByCategory_mixedCategories_maintainsOrder() {
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 2L, "人物"),
            createPicture(3L, 3L, "动物"),
            createPicture(4L, 4L, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByCategory(input, 3);

        assertEquals(4, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals(3L, result.get(2).getId());
        assertEquals(4L, result.get(3).getId());
    }

    @Test
    void reorderByCategory_allSameCategory_noReorder() {
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 2L, "风景"),
            createPicture(3L, 3L, "风景"),
            createPicture(4L, 4L, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByCategory(input, 3);

        assertEquals(4, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals(3L, result.get(2).getId());
        assertEquals(4L, result.get(3).getId());
    }

    @Test
    void reorderByCategory_invalidParams_returnsOriginal() {
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 2L, "人物")
        );

        // maxConsecutive <= 1
        List<Picture> result = PictureReorderUtils.reorderByCategory(input, 1);
        assertEquals(input, result);
    }

    // ========== Combination Tests ==========

    @Test
    void reorderByAuthorThenCategory_combined() {
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 1L, "风景"),
            createPicture(3L, 1L, "风景"),
            createPicture(4L, 2L, "风景"),
            createPicture(5L, 2L, "风景"),
            createPicture(6L, 2L, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 20, 2);
        result = PictureReorderUtils.reorderByCategory(result, 3);

        // 作者打散把第 3、6 张同作者延后： [1,2,4,5,3,6]；同类打散无从下手（全是风景）
        assertEquals(Arrays.asList(1L, 2L, 4L, 5L, 3L, 6L),
            result.stream().map(Picture::getId).collect(Collectors.toList()));
    }

    /** 最长同类连续段长度；用于断言打散后的不变量。 */
    private static int maxConsecutiveRun(List<Picture> pictures) {
        int longest = 0;
        int current = 0;
        String previous = null;
        for (Picture picture : pictures) {
            current = picture.getCategory() != null && picture.getCategory().equals(previous)
                ? current + 1 : 1;
            previous = picture.getCategory();
            longest = Math.max(longest, current);
        }
        return longest;
    }
}
