# 推荐重排打散逻辑设计文档

> 日期：2026-03-28
> 状态：已批准

## 一、需求背景

当前首页推荐可能存在同作者/同分类内容堆积的问题，影响用户体验。需要实现重排打散逻辑来提升推荐多样性。

## 二、核心需求

| 规则 | 参数 | 说明 |
|------|------|------|
| **作者打散** | 前20条同作者最多2条 | 避免首页第一屏同作者内容堆积 |
| **分类打散** | 连续3条不能同分类 | 避免同类内容连续出现 |
| **探索位插入** | 暂不实现 | 后续根据效果再决定 |

## 三、实现方案

### 方案选择：内存重排

在 `PictureRecommendServiceImpl.calculateRecommendPictures()` 返回前对结果进行重排。

**理由：**
- 实现简单快速
- 有 Redis 缓存，实际计算频率不高
- 灵活，参数可配置
- 后续可优化为预计算快照

### 处理流程

```
评分排序 → 作者打散 → 分类打散 → 缓存 → 返回
```

## 四、详细设计

### 4.1 作者打散算法

```java
public static List<Picture> reorderByAuthor(List<Picture> pictures, int windowSize, int maxCount) {
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
```

### 4.2 分类打散算法

```java
public static List<Picture> reorderByCategory(List<Picture> pictures, int maxConsecutive) {
    List<Picture> result = new ArrayList<>();

    for (Picture pic : pictures) {
        result.add(pic);

        if (result.size() >= maxConsecutive) {
            // 检查末尾是否连续同分类
            if (isConsecutiveCategory(result, pic.getCategory(), maxConsecutive)) {
                Picture toMove = result.remove(result.size() - 1);
                int insertPos = findInsertPosition(result, toMove.getCategory());
                result.add(insertPos, toMove);
            }
        }
    }

    return result;
}
```

### 4.3 配置化

**application.yml**

```yaml
vision-space:
  recommend:
    reorder:
      author-window-size: 20       # 作者打散窗口大小
      author-max-count: 2          # 窗口内同作者最大数量
      category-max-consecutive: 3  # 最大连续同分类数
```

**ReorderConfig.java**

```java
@Component
@ConfigurationProperties(prefix = "vision-space.recommend.reorder")
@Data
public class ReorderConfig {
    private int authorWindowSize = 20;
    private int authorMaxCount = 2;
    private int categoryMaxConsecutive = 3;
}
```

### 4.4 集成点

**PictureRecommendServiceImpl.java**

```java
@Resource
private ReorderConfig reorderConfig;

private List<Picture> calculateRecommendPictures(String type) {
    // ... 现有逻辑获取 pictures ...

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

## 五、新增文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `PictureReorderUtils.java` | 工具类 | 作者打散、分类打散方法 |
| `ReorderConfig.java` | 配置类 | 重排参数配置 |
| `PictureReorderUtilsTest.java` | 测试类 | 单元测试 |
| `application.yml` | 配置文件 | 添加重排配置项 |

## 六、边界处理

- 空列表直接返回
- 单条记录直接返回
- `category` 为 null 时跳过分类检查
- 找不到合适插入位置时放到末尾
- 打散失败时记录日志，返回原始列表

## 七、测试策略

### 单元测试

1. **作者打散测试**
   - 同作者超过限制时正确打散
   - 窗口外不受限制
   - 空列表/单条记录边界情况

2. **分类打散测试**
   - 连续同分类时正确打散
   - 分类为 null 时正确处理
   - 找不到插入位置时放到末尾

3. **组合测试**
   - 作者打散 + 分类打散组合

### 集成测试

- 调用 `/api/picture/recommend/list` 接口
- 验证返回结果顺序符合打散规则
- 检查缓存是否正常工作

## 八、工作量估算

| 任务 | 工作量 |
|------|--------|
| 工具类实现 | 2小时 |
| 配置类 + 配置文件 | 0.5小时 |
| 单元测试 | 1小时 |
| 集成测试 | 0.5小时 |
| **总计** | **0.5天** |

## 九、后续优化

1. **效果监控** - 收集打散前后的 CTR 对比数据
2. **参数调优** - 根据数据调整窗口大小和上限
3. **探索位插入** - 如果效果不理想，添加探索位
4. **预计算快照** - 如果数据量增长，考虑预计算
