# 首页瀑布流推荐接口优化实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复推荐接口 hasMore 判断 bug，返回精简图片对象减少带宽

**Architecture:** 新建 RecommendPictureVO 和 RecommendPageVO，复用 Picture 实体字段但仅返回瀑布流必要字段

**Tech Stack:** Spring Boot (Java), Vue 3, MyBatis Plus

---

## 文件清单

- 新建：`src/main/java/com/yuwen/visionspace/model/vo/RecommendPictureVO.java`
- 新建：`src/main/java/com/yuwen/visionspace/model/vo/RecommendPageVO.java`
- 修改：`src/main/java/com/yuwen/visionspace/service/PictureRecommendService.java`
- 修改：`src/main/java/com/yuwen/visionspace/service/impl/PictureRecommendServiceImpl.java`
- 修改：`src/main/java/com/yuwen/visionspace/controller/PictureRecommendController.java`
- 修改：`frontend/src/components/HomeRecommend.vue`

---

## Task 1: 创建 RecommendPictureVO

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/vo/RecommendPictureVO.java`

**Step 1: 创建文件**

```java
package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 首页瀑布流推荐图片视图
 */
@Data
public class RecommendPictureVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片ID
     */
    private Long id;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 宽高比
     */
    private Double picScale;

    /**
     * 主色调
     */
    private String picColor;

    /**
     * 标签
     */
    private List<String> tags;
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/model/vo/RecommendPictureVO.java
git commit -m "feat: 添加首页瀑布流推荐图片VO"
```

---

## Task 2: 创建 RecommendPageVO

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/vo/RecommendPageVO.java`

**Step 1: 创建文件**

```java
package com.yuwen.visionspace.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 推荐分页包装对象
 */
@Data
public class RecommendPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片列表
     */
    private List<RecommendPictureVO> records;

    /**
     * 总数
     */
    private Long total;
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/model/vo/RecommendPageVO.java
git commit -m "feat: 添加推荐分页包装对象"
```

---

## Task 3: 修改 PictureRecommendService 接口

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/service/PictureRecommendService.java`

**Step 1: 查看现有接口**

```java
// 找到 getRecommendPictures 方法签名，应该是：
List<Picture> getRecommendPictures(String type, int page, int size);
```

**Step 2: 修改返回类型**

将 `List<Picture>` 改为返回分页信息。查看现有文件后确定具体修改位置。

**Step 3: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/service/PictureRecommendService.java
git commit -m "refactor: PictureRecommendService 返回类型改为 RecommendPageVO"
```

---

## Task 4: 修改 PictureRecommendServiceImpl

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureRecommendServiceImpl.java`

**Step 1: 查看现有 getRecommendPictureIds 方法获取 total**

现有代码已有 `getRecommendPictureIds` 方法返回 `List<Long>`。需要新增方法返回总数。

**Step 2: 修改 getRecommendPictures 方法返回 RecommendPageVO**

```java
@Override
public RecommendPageVO getRecommendPictures(String type, int page, int size) {
    List<Long> ids = getRecommendPictureIds(type, page, size);
    if (ids.isEmpty()) {
        return new RecommendPageVO(Collections.emptyList(), 0L);
    }

    // 查询图片详情
    QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
    queryWrapper.lambda().in(Picture::getId, ids);
    List<Picture> pictures = pictureMapper.selectList(queryWrapper);

    // 按ID顺序排序
    Map<Long, Integer> orderMap = new HashMap<>();
    for (int i = 0; i < ids.size(); i++) {
        orderMap.put(ids.get(i), i);
    }
    pictures.sort(Comparator.comparingInt(p -> orderMap.getOrDefault(p.getId(), Integer.MAX_VALUE)));

    // 转换为 VO
    List<RecommendPictureVO> records = pictures.stream()
            .map(this::toRecommendPictureVO)
            .collect(Collectors.toList());

    // 计算总数 (从缓存或计算获取)
    Long total = getRecommendTotal(type);

    return new RecommendPageVO(records, total);
}

/**
 * 转换 Picture 为 RecommendPictureVO
 */
private RecommendPictureVO toRecommendPictureVO(Picture picture) {
    RecommendPictureVO vo = new RecommendPictureVO();
    vo.setId(picture.getId());
    vo.setThumbnailUrl(picture.getThumbnailUrl());
    vo.setName(picture.getName());
    vo.setPicWidth(picture.getPicWidth());
    vo.setPicHeight(picture.getPicHeight());
    vo.setPicScale(picture.getPicScale());
    vo.setPicColor(picture.getPicColor());
    vo.setTags(JSONUtil.toList(picture.getTags(), String.class));
    return vo;
}

/**
 * 获取推荐总数
 */
private Long getRecommendTotal(String type) {
    List<Picture> pictures = calculateRecommendPictures(type);
    return (long) pictures.size();
}
```

**Step 3: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/service/impl/PictureRecommendServiceImpl.java
git commit -m "feat: PictureRecommendServiceImpl 返回 RecommendPageVO"
```

---

## Task 5: 修改 PictureRecommendController

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/controller/PictureRecommendController.java`

**Step 1: 查看现有 getRecommendList 方法**

```java
// 原返回类型 BaseResponse<List<Picture>>
@GetMapping("/list")
public BaseResponse<List<Picture>> getRecommendList(...)
```

**Step 2: 修改返回类型**

```java
import com.yuwen.visionspace.model.vo.RecommendPageVO;

@GetMapping("/list")
public BaseResponse<RecommendPageVO> getRecommendList(
        @RequestParam(name = "type", defaultValue = "hot") String type,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", defaultValue = "10") int size) {

    size = Math.min(size, 100);
    RecommendPageVO pageVO = pictureRecommendService.getRecommendPictures(type, page, size);
    return ResultUtils.success(pageVO);
}
```

**Step 3: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/controller/PictureRecommendController.java
git commit -m "feat: PictureRecommendController 返回 RecommendPageVO"
```

---

## Task 6: 修改前端 HomeRecommend.vue

**Files:**
- Modify: `frontend/src/components/HomeRecommend.vue`

**Step 1: 找到数据获取和 hasMore 判断逻辑**

在 `loadMoreData` 函数中，找到：
```javascript
const res = await getRecommendListUsingGet({
  type: currentType.value,
  page: page.value,
  size: size.value,
})
// 原: hasMore.value = data.length === size.value
```

**Step 2: 修改为新结构**

```javascript
const res = await getRecommendListUsingGet({
  type: currentType.value,
  page: page.value,
  size: size.value,
})
const data = res.data
// 新: 从 response.data.records 获取列表
const records = data.records || []
// 新: 从 response.data.total 获取总数
const total = data.total || 0

// 添加到瀑布流
pictures.value.push(...records.map(picture => ({
  ...picture,
  // 保持与原有字段兼容
  src: picture.thumbnailUrl,
  height: picture.picHeight,
})))

// 修改 hasMore 判断
hasMore.value = pictures.value.length < total
```

**Step 3: Commit**

```bash
git add frontend/src/components/HomeRecommend.vue
git commit -m "feat: HomeRecommend 适配新的推荐接口返回结构"
```

---

## Task 7: 验证编译和测试

**Step 1: 编译后端**

```bash
cd D:/Work/code/VisionSpace
mvn compile -q
```

预期：无错误

**Step 2: 编译前端**

```bash
cd D:/Work/code/VisionSpace/frontend
npm run build
```

预期：无错误

**Step 3: 提交所有更改**

```bash
git add -A
git commit -m "feat: 首页瀑布流推荐接口优化 - 返回分页对象和精简VO"
```
