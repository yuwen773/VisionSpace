# 图片颜色提取策略模式重构设计

## 背景

当前 `ColorExtractUtils` 使用自实现的 K-Means 算法提取图片主色调，存在以下问题：
- K-Means 随机初始化导致结果不稳定
- 性能较差（缩放 + 多次迭代）
- 背景色过滤过于简单

引入成熟的 [color-thief-java](https://github.com/SvenWoltmann/color-thief-java)（Median Cut 算法），性能提升 40 倍，结果更稳定。

## 目标

1. 引入 color-thief-java 作为主要颜色提取实现
2. 保留原 K-Means 实现作为降级备选
3. 通过配置切换两种实现
4. 调用方无感知，无需修改业务代码

## 架构设计

### 策略模式

```
PictureUploadTemplate
       │
       ▼
ColorExtractStrategyFactory  ── 根据配置创建策略
       │
       ▼
ColorExtractStrategy (接口)
   │
   ├── ColorThiefStrategy   (primary: Median Cut)
   └── KMeansStrategy        (fallback: K-Means)
```

### 策略接口

```java
public interface ColorExtractStrategy {
    /**
     * 从 BufferedImage 提取主色调
     * @param image 图片
     * @return HEX 格式颜色字符串，如 "#FF5733"，提取失败返回 null
     */
    String extractDominantColor(BufferedImage image);
}
```

## 文件变更

| 操作 | 文件路径 |
|------|----------|
| 新增 | `src/main/java/com/yuwen/visionspace/utils/strategy/ColorExtractStrategy.java` |
| 新增 | `src/main/java/com/yuwen/visionspace/utils/strategy/ColorThiefStrategy.java` |
| 新增 | `src/main/java/com/yuwen/visionspace/utils/strategy/KMeansStrategy.java` |
| 新增 | `src/main/java/com/yuwen/visionspace/utils/strategy/ColorExtractStrategyFactory.java` |
| 修改 | `pom.xml` — 添加 color-thief 依赖 |
| 修改 | `PictureUploadTemplate.java` — 注入工厂，使用策略 |
| 修改 | `application.yml` — 添加配置项 |
| 保留 | `ColorExtractUtils.java` — 原实现，供 KMeansStrategy 调用 |

## 配置设计

```yaml
vision-space:
  color-extract:
    method: median-cut  # median-cut（默认）或 kmeans
    fallback-enabled: true  # 是否启用降级
```

## 降级逻辑

1. 使用配置的 primary method（默认 `median-cut`）
2. 若调用失败且 `fallback-enabled=true`，降级使用 K-Means
3. 降级失败则返回 `null`

## 依赖信息

```xml
<dependency>
    <groupId>de.androidpit</groupId>
    <artifactId>color-thief</artifactId>
    <version>1.1.2</version>
</dependency>
```

**License**: Creative Commons Attribution 2.5 — 需保留作者署名

## 验收标准

1. 单元测试 `ColorExtractUtilsTest` 仍然通过
2. 图片上传后 `picColor` 字段正常填充
3. 配置 `method=kmeans` 时使用 K-Means 实现
4. 配置 `method=median-cut` 时使用 Median Cut 实现
5. Median Cut 异常时能降级到 K-Means
