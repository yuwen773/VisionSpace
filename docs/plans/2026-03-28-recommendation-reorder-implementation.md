# 推荐重排打散逻辑实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在推荐结果返回前实现作者打散和分类打散逻辑，提升首页推荐多样性。

**Architecture:** 在 `PictureRecommendServiceImpl.calculateRecommendPictures()` 返回前，通过 `PictureReorderUtils` 工具类对结果进行内存重排，先作者打散后分类打散。

**Tech Stack:** Java 8, Spring Boot 2.7.6, MyBatis Plus, JUnit 5

---

## Task 1: 创建 ReorderConfig 配置类

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/config/ReorderConfig.java`

**Step 1: 创建配置类**

```java
package com.yuwen.visionspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐重打散配置
 */
@Component
@ConfigurationProperties(prefix = "vision-space.recommend.reorder")
@Data
public class ReorderConfig {

    /**
     * 作者打散窗口大小
     */
    private int authorWindowSize = 20;

    /**
     * 窗口内同作者最大数量
     */
    private int authorMaxCount = 2;

    /**
     * 最大连续同分类数
     */
    private int categoryMaxConsecutive = 3;
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/config/ReorderConfig.java
git commit -m "feat: add ReorderConfig for reorder parameters"
```

---

## Task 2: 创建 PictureReorderUtils 工具类骨架

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/utils/PictureReorderUtils.java`
- Create: `src/test/java/com/yuwen/visionspace/utils/PictureReorderUtilsTest.java`

**Step 1: 创建工具类骨架**

```java
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
     *
     * @param pictures  图片列表
     * @param windowSize 窗口大小
     * @param maxCount   同作者最大数量
     * @return 打散后的列表
     */
    public static List<Picture> reorderByAuthor(List<Picture> pictures, int windowSize, int maxCount) {
        if (pictures == null || pictures.size() <= 1) {
            return pictures;
        }

        List<Picture> result = new ArrayList<>();
        Map<Long, Integer> authorCount = new HashMap<>();
        List<Picture> deferred = new ArrayList<>();

        for (Picture pic : pictures) {
            Long userId = pic.getUserId();
            int count = authorCount.getOrDefault(userId, 0);

            // 窗口内该作者已达上限 -> 暂时放后面
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
     *
     * @param pictures        图片列表
     * @param maxConsecutive  最大连续同分类数
     * @return 打散后的列表
     */
    public static List<Picture> reorderByCategory(List<Picture> pictures, int maxConsecutive) {
        if (pictures == null || pictures.size() <= 1) {
            return pictures;
        }

        List<Picture> result = new ArrayList<>();

        for (Picture pic : pictures) {
            result.add(pic);

            if (result.size() >= maxConsecutive) {
                String category = pic.getCategory();

                // category 为 null 跳过检查
                if (category == null) {
                    continue;
                }

                // 检查末尾是否连续同分类
                if (isConsecutiveCategory(result, category, maxConsecutive)) {
                    Picture toMove = result.remove(result.size() - 1);
                    int insertPos = findInsertPosition(result, category);
                    result.add(insertPos, toMove);
                }
            }
        }

        return result;
    }

    /**
     * 检查末尾是否连续N条同分类
     */
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

    /**
     * 找到第一个不同分类的位置
     */
    private static int findInsertPosition(List<Picture> pictures, String category) {
        // 从前往后找第一个不同分类的位置
        for (int i = 0; i < pictures.size(); i++) {
            if (!category.equals(pictures.get(i).getCategory())) {
                return i;
            }
        }
        // 找不到则放到末尾
        return pictures.size();
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/utils/PictureReorderUtils.java
git commit -m "feat: add PictureReorderUtils with author and category reorder"
```

---

## Task 3: 编写单元测试 - 作者打散

**Files:**
- Modify: `src/test/java/com/yuwen/visionspace/utils/PictureReorderUtilsTest.java`

**Step 1: 编写测试 - 空列表和单条记录**

```java
package com.yuwen.visionspace.utils;

import com.yuwen.visionspace.model.entity.Picture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PictureReorderUtilsTest {

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

    private Picture createPicture(Long id, Long userId, String category) {
        Picture pic = new Picture();
        pic.setId(id);
        pic.setUserId(userId);
        pic.setCategory(category);
        return pic;
    }
}
```

**Step 2: 运行测试**

```bash
cd D:\work\code\VisionSpace
mvn test -Dtest=PictureReorderUtilsTest#reorderByAuthor_nullList_returnsNull
mvn test -Dtest=PictureReorderUtilsTest#reorderByAuthor_emptyList_returnsEmpty
mvn test -Dtest=PictureReorderUtilsTest#reorderByAuthor_singleElement_returnsSame
```

Expected: 全部 PASS

**Step 3: 编写测试 - 作者打散核心逻辑**

```java
    @Test
    void reorderByAuthor_sameAuthorExceedsLimit_defersExcess() {
        // 作者1有5张图片，窗口20内最多2张，3张应被延后
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 1L, "风景"),
            createPicture(3L, 1L, "风景"),  // 应被延后
            createPicture(4L, 1L, "风景"),  // 应被延后
            createPicture(5L, 1L, "风景")   // 应被延后
        );

        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 20, 2);

        assertEquals(5, result.size());
        // 前2张保持原位
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        // 后3张被放到末尾
        assertEquals(3L, result.get(2).getId());
        assertEquals(4L, result.get(3).getId());
        assertEquals(5L, result.get(4).getId());
    }

    @Test
    void reorderByAuthor_beyondWindowSize_noLimit() {
        // 窗口大小3，同作者最多2张
        // 第4张（窗口外）不受限制
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 1L, "风景"),
            createPicture(3L, 2L, "人物"),
            createPicture(4L, 1L, "风景")  // 窗口外，应保留在原位
        );

        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 3, 2);

        assertEquals(4, result.size());
        assertEquals(4L, result.get(3).getId()); // 第4张保留在原位
    }

    @Test
    void reorderByAuthor_multipleAuthors_maintainsOrder() {
        // 多作者混合，保持相对顺序
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 2L, "人物"),
            createPicture(3L, 1L, "风景"),
            createPicture(4L, 2L, "人物"),
            createPicture(5L, 1L, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 20, 2);

        assertEquals(5, result.size());
        // 验证作者1的3张图片：第5张应被延后
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals(3L, result.get(2).getId());
        assertEquals(4L, result.get(3).getId());
        assertEquals(5L, result.get(4).getId());
    }
```

**Step 4: 运行测试**

```bash
mvn test -Dtest=PictureReorderUtilsTest
```

Expected: 全部 PASS

**Step 5: Commit**

```bash
git add src/test/java/com/yuwen/visionspace/utils/PictureReorderUtilsTest.java
git commit -m "test: add author reorder unit tests"
```

---

## Task 4: 编写单元测试 - 分类打散

**Files:**
- Modify: `src/test/java/com/yuwen/visionspace/utils/PictureReorderUtilsTest.java`

**Step 1: 编写测试 - 分类打散**

```java
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
        // 连续3张同分类，第4张应被移动
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 2L, "风景"),
            createPicture(3L, 3L, "风景"),
            createPicture(4L, 4L, "风景")  // 应被移动到前面
        );

        List<Picture> result = PictureReorderUtils.reorderByCategory(input, 3);

        assertEquals(4, result.size());
        // 第4张应该被移动到第1个位置（第一个不同分类）
        assertEquals(4L, result.get(0).getId());
    }

    @Test
    void reorderByCategory_mixedCategories_maintainsOrder() {
        // 不同分类混合，保持顺序
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 2L, "人物"),
            createPicture(3L, 3L, "动物"),
            createPicture(4L, 4L, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByCategory(input, 3);

        assertEquals(4, result.size());
        // 验证顺序保持不变
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals(3L, result.get(2).getId());
        assertEquals(4L, result.get(3).getId());
    }

    @Test
    void reorderByCategory_allSameCategory_movesToEnd() {
        // 全部同分类，后续元素应被移动到末尾
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 2L, "风景"),
            createPicture(3L, 3L, "风景"),
            createPicture(4L, 4L, "风景")
        );

        List<Picture> result = PictureReorderUtils.reorderByCategory(input, 3);

        assertEquals(4, result.size());
        // 前3张保持，第4张被移到末尾（找不到不同分类的位置）
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertEquals(3L, result.get(2).getId());
        assertEquals(4L, result.get(3).getId());
    }

    @Test
    void reorderByCategory_combinedWithAuthorReorder() {
        // 组合测试：先作者打散，再分类打散
        List<Picture> input = Arrays.asList(
            createPicture(1L, 1L, "风景"),
            createPicture(2L, 1L, "风景"),
            createPicture(3L, 1L, "风景"),  // 作者延后
            createPicture(4L, 2L, "风景"),
            createPicture(5L, 2L, "风景"),
            createPicture(6L, 2L, "风景")   // 作者延后
        );

        List<Picture> result = PictureReorderUtils.reorderByAuthor(input, 20, 2);
        result = PictureReorderUtils.reorderByCategory(result, 3);

        assertEquals(6, result.size());
    }
```

**Step 2: 运行测试**

```bash
mvn test -Dtest=PictureReorderUtilsTest
```

Expected: 全部 PASS

**Step 3: Commit**

```bash
git add src/test/java/com/yuwen/visionspace/utils/PictureReorderUtilsTest.java
git commit -m "test: add category reorder unit tests"
```

---

## Task 5: 更新 application.yml 配置

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: 添加重排配置**

在 `vision-space.recommend` 下添加 `reorder` 配置：

```yaml
vision-space:
  recommend:
    engagement-weight: 0.40
    freshness-weight: 0.20
    quality-weight: 0.20
    conversion-weight: 0.10
    manual-weight: 0.10
    time-decay-rate: 0.05
    cache-ttl-minutes: 5
    reorder:
      author-window-size: 20
      author-max-count: 2
      category-max-consecutive: 3
```

**Step 2: 验证配置**

```bash
mvn compile
```

Expected: 编译成功，无错误

**Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: add reorder parameters to application.yml"
```

---

## Task 6: 集成到 PictureRecommendServiceImpl

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureRecommendServiceImpl.java`

**Step 1: 添加依赖注入**

在类的开头添加：

```java
import com.yuwen.visionspace.config.ReorderConfig;
import com.yuwen.visionspace.utils.PictureReorderUtils;
```

在 `@Resource` 字段后添加：

```java
@Resource
private ReorderConfig reorderConfig;
```

**Step 2: 修改 calculateRecommendPictures 方法**

在 `switch` 语句结束后，`return pictures;` 之前添加：

```java
        // ... switch 语句结束 ...

        default:
            pictures = Collections.emptyList();
        }

        // 重排打散
        pictures = PictureReorderUtils.reorderByAuthor(
            pictures,
            reorderConfig.getAuthorWindowSize(),
            reorderConfig.getAuthorMaxCount()
        );
        pictures = PictureReorderUtils.reorderByCategory(
            pictures,
            reorderConfig.getCategoryMaxConsecutive()
        );

        return pictures;
    }
```

**Step 3: 运行测试**

```bash
mvn test -Dtest=PictureRecommendServiceImplTest
```

（如果没有该测试类，跳过此步）

**Step 4: 启动应用验证**

```bash
mvn spring-boot:run
```

访问：`http://localhost:8080/api/picture/recommend/list?type=hot&pageNum=1&pageSize=20`

Expected: 返回成功，结果已按重排规则处理

**Step 5: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/service/impl/PictureRecommendServiceImpl.java
git commit -m "feat: integrate reorder logic into recommendation service"
```

---

## Task 7: 集成测试

**Files:**
- Create: `src/test/java/com/yuwen/visionspace/service/impl/PictureRecommendServiceImplIntegrationTest.java`

**Step 1: 编写集成测试**

```java
package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.model.entity.Picture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PictureRecommendServiceImplIntegrationTest {

    @Resource
    private PictureRecommendService pictureRecommendService;

    @Test
    void getRecommendPictures_hotType_returnsReorderedResults() {
        List<Picture> pictures = pictureRecommendService.getRecommendPictures("hot", 1, 20);

        assertNotNull(pictures);
        assertFalse(pictures.isEmpty());

        // 验证作者打散：前20条中，同作者不超过2条
        assertAuthorReorder(pictures, 20, 2);

        // 验证分类打散：连续不超过3条同分类
        assertCategoryReorder(pictures, 3);
    }

    private void assertAuthorReorder(List<Picture> pictures, int windowSize, int maxCount) {
        int checkSize = Math.min(pictures.size(), windowSize);
        for (int i = 0; i < checkSize; i++) {
            Long userId = pictures.get(i).getUserId();
            int count = 0;
            for (int j = 0; j < checkSize; j++) {
                if (userId.equals(pictures.get(j).getUserId())) {
                    count++;
                }
            }
            assertTrue(count <= maxCount,
                "作者 " + userId + " 在前" + windowSize + "条中出现 " + count + " 次，超过限制 " + maxCount);
        }
    }

    private void assertCategoryReorder(List<Picture> pictures, int maxConsecutive) {
        int consecutiveCount = 1;
        String lastCategory = null;

        for (Picture pic : pictures) {
            String category = pic.getCategory();
            if (category == null) {
                consecutiveCount = 1;
                continue;
            }

            if (category.equals(lastCategory)) {
                consecutiveCount++;
                assertTrue(consecutiveCount <= maxConsecutive,
                    "连续 " + consecutiveCount + " 条同分类: " + category);
            } else {
                consecutiveCount = 1;
                lastCategory = category;
            }
        }
    }
}
```

**Step 2: 运行集成测试**

```bash
mvn test -Dtest=PictureRecommendServiceImplIntegrationTest
```

Expected: PASS

**Step 3: Commit**

```bash
git add src/test/java/com/yuwen/visionspace/service/impl/PictureRecommendServiceImplIntegrationTest.java
git commit -m "test: add integration test for reorder logic"
```

---

## Task 8: 更新进度文档

**Files:**
- Modify: `docs/progress/recommendation-progress.md`

**Step 1: 更新进度**

在"已完成"部分添加：

```markdown
| **重排打散** | `PictureReorderUtils.java` | 作者/分类打散 |
| | `ReorderConfig.java` | 配置化参数 |
```

在"未完成"部分移除：

```markdown
| 重排打散 | ❌ 未做 | 作者/分类打散 |
```

**Step 2: 提交进度文档**

```bash
git add -f docs/progress/recommendation-progress.md
git commit -m "docs: update progress - reorder logic completed"
```

---

## 验证清单

完成所有任务后，运行以下验证：

```bash
# 1. 全部单元测试
mvn test

# 2. 启动应用
mvn spring-boot:run

# 3. 测试 API
curl http://localhost:8080/api/picture/recommend/list?type=hot&pageNum=1&pageSize=20

# 4. 检查日志确认重排逻辑执行
```

---

## 相关文档

- 设计文档: `docs/plans/2026-03-28-recommendation-reorder-design.md`
- 进度文档: `docs/progress/recommendation-progress.md`
