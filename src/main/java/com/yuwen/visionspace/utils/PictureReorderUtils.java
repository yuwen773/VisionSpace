package com.yuwen.visionspace.utils;

import com.yuwen.visionspace.model.entity.Picture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐重打散工具类
 */
public class PictureReorderUtils {

    /**
     * 按作者打散：前N条同作者最多M条
     */
    public static List<Picture> reorderByAuthor(List<Picture> pictures, int windowSize, int maxCount) {
        if (pictures == null || pictures.size() <= 1) {
            return pictures;
        }

        if (windowSize <= 0 || maxCount <= 0) {
            return pictures;
        }

        List<Picture> result = new ArrayList<>();
        Map<Long, Integer> authorCount = new HashMap<>();
        List<Picture> deferred = new ArrayList<>();

        for (Picture pic : pictures) {
            Long userId = pic.getUserId();

            // userId 为 null 不参与作者打散
            if (userId == null) {
                result.add(pic);
                continue;
            }

            int count = authorCount.getOrDefault(userId, 0);

            if (result.size() < windowSize && count >= maxCount) {
                deferred.add(pic);
            } else {
                result.add(pic);
                authorCount.put(userId, count + 1);
            }
        }

        result.addAll(deferred);
        return result;
    }

    /**
     * 按分类打散：连续N条不能同分类
     */
    public static List<Picture> reorderByCategory(List<Picture> pictures, int maxConsecutive) {
        if (pictures == null || pictures.size() <= 1) {
            return pictures;
        }

        if (maxConsecutive <= 1) {
            return pictures;
        }

        List<Picture> result = new ArrayList<>();

        for (Picture pic : pictures) {
            result.add(pic);

            if (result.size() >= maxConsecutive) {
                String category = pic.getCategory();
                if (category == null) {
                    continue;
                }

                if (isConsecutiveCategory(result, category, maxConsecutive)) {
                    Picture toMove = result.remove(result.size() - 1);
                    int insertPos = findInsertPosition(result, category);
                    result.add(insertPos, toMove);
                }
            }
        }

        return result;
    }

    private static boolean isConsecutiveCategory(List<Picture> pictures, String category, int maxConsecutive) {
        int count = 0;
        for (int i = pictures.size() - 1; i >= 0; i--) {
            String picCategory = pictures.get(i).getCategory();
            if (category.equals(picCategory)) {
                count++;
                if (count >= maxConsecutive) {
                    return true;
                }
            } else {
                break;
            }
        }
        return false;
    }

    private static int findInsertPosition(List<Picture> pictures, String category) {
        for (int i = 0; i < pictures.size(); i++) {
            if (!category.equals(pictures.get(i).getCategory())) {
                return i;
            }
        }
        return pictures.size();
    }
}
