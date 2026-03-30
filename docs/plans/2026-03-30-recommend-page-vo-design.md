# 首页瀑布流推荐接口优化设计

## 背景

当前 `GET /api/picture/recommend/list` 接口返回 `BaseResponse<List<Picture>>`，没有返回总数。前端通过 `data.length === size` 判断是否还有更多数据，这个逻辑在数据量刚好是 pageSize 整数倍时会错误地终止加载。

同时，当前返回的是原始 `Picture` 实体，包含原图 URL 等冗余字段，增加了不必要的带宽消耗。

## 目标

1. 修复 `hasMore` 判断 bug
2. 返回精简的图片对象，减少带宽消耗
3. 保持接口分页能力

## 设计

### 1. 新建 `RecommendPictureVO`

用于首页瀑布流展示，精简字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 图片ID |
| thumbnailUrl | String | 缩略图（展示用） |
| name | String | 图片名称 |
| picWidth | Integer | 宽度（计算瀑布流高度） |
| picHeight | Integer | 高度 |
| picScale | Double | 宽高比 |
| picColor | String | 主色调 |
| tags | List<String> | 标签 |

### 2. 新建 `RecommendPageVO`

分页包装对象：

| 字段 | 类型 | 说明 |
|------|------|------|
| records | List<RecommendPictureVO> | 图片列表 |
| total | Long | 总数 |

### 3. 接口改动

**修改 `GET /api/picture/recommend/list`**
- 返回类型：`BaseResponse<RecommendPageVO>`
- 字段：`records`（图片列表）、`total`（总数）

### 4. 前端改动

**`HomeRecommend.vue`**
- 适配新返回结构：`response.data.records` 和 `response.data.total`
- 修改 hasMore 判断：`hasMore.value = records.length < total`

## 文件清单

- 新建：`src/main/java/com/yuwen/visionspace/model/vo/RecommendPictureVO.java`
- 新建：`src/main/java/com/yuwen/visionspace/model/vo/RecommendPageVO.java`
- 修改：`src/main/java/com/yuwen/visionspace/controller/PictureRecommendController.java`
- 修改：`src/main/java/com/yuwen/visionspace/service/impl/PictureRecommendServiceImpl.java`
- 修改：`frontend/src/components/HomeRecommend.vue`
