# 颜色提取与搜索功能设计

## 需求概述

增强 `ColorTransformUtils` 工具类，实现：
1. K-Means 颜色提取：从图片中提取主色调
2. 按颜色搜索图片：支持手动颜色和参考图两种方式

## 功能设计

### 1. K-Means 颜色提取

**类**：`ColorExtractUtils`（新建）

**方法**：
```java
public static String extractDominantColor(BufferedImage image)
public static String extractDominantColor(String imagePath)
```

**算法流程**：
1. 图片缩放至 100x100（加快处理速度）
2. 采样所有像素点，忽略透明像素
3. K-Means 聚类（K=5，迭代 20 次）
4. 返回最显著的簇中心颜色（HEX 格式）

**忽略颜色**：
- 白色：`RGB > 240`
- 黑色：`RGB < 15`

### 2. 相似度计算

**类**：`ColorSimilarUtils`（已存在）

**方法**：
```java
public static double calculateSimilarity(Color color1, Color color2)
public static double calculateSimilarity(String hexColor1, String hexColor2)
```

**算法**：欧氏距离归一化，公式：
```
similarity = 1 - distance / sqrt(3 * 255^2)
```

### 3. 按颜色搜索（Service 层）

**方法**：
```java
public List<PictureVO> searchPictureByColor(String color, double threshold)
```

**参数**：
- `color`：目标颜色（HEX 格式）
- `threshold`：相似度阈值（0~1）

**逻辑**：
1. 计算目标颜色与图片 `picColor` 的相似度
2. 过滤：相似度 >= threshold
3. 排序：按相似度降序

### 4. 两种颜色指定方式

| 方式 | 说明 |
|------|------|
| 手动输入 | 用户传入 HEX 颜色字符串 |
| 参考图 | 用户上传图片，系统调用 `extractDominantColor` 提取主色调 |

### 5. 未来接口规划（暂不实现）

```
POST /api/picture/search/by-color
请求体：{
  "color": "#FF5733",        // 目标颜色
  "threshold": 0.7,          // 相似度阈值 0~1
  "referencePic": File(可选) // 上传参考图
}
```

## 文件变更

| 文件 | 变更 |
|------|------|
| `utils/ColorExtractUtils.java` | 新增，K-Means 颜色提取 |
| `utils/ColorTransformUtils.java` | 保留现有方法 |
| `utils/ColorSimilarUtils.java` | 无变更（已实现） |
| `service/impl/PictureServiceImpl.java` | 新增 `searchPictureByColor` 方法 |

## K-Means 算法要点

**初始化**：随机选择 K 个像素点作为初始质心

**迭代**：
1. 计算每个像素到各质心的欧氏距离
2. 将像素分配到最近的簇
3. 重新计算每个簇的中心
4. 重复直到收敛或达到最大迭代次数

**主色选取**：取像素数量最多的簇的中心颜色

## 阈值建议

| 阈值 | 效果 |
|------|------|
| 0.8+ | 非常相似的颜色 |
| 0.6-0.8 | 中等相似 |
| 0.5-0.6 | 宽泛匹配，可能包含不相关结果 |
