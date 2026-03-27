# 图片颜色提取策略模式重构 - 实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 引入 color-thief-java 作为主要颜色提取实现，保留 K-Means 作为降级备选，通过配置切换。

**Architecture:** 策略模式 — `ColorExtractStrategyFactory` 根据配置创建策略，`PictureUploadTemplate` 使用策略接口。Primary: Median Cut (color-thief)，Fallback: K-Means。

**Tech Stack:** Java 8, Spring Boot 2.7.6, color-thief-java 1.1.2

---

## 依赖预检

先确认 color-thief 依赖可用：

```bash
mvn dependency:get -Dartifact=de.androidpit:color-thief:1.1.2
```

预期：下载成功，无报错。

---

## Task 1: 添加 Maven 依赖

**File:** `pom.xml`

**Step 1: 添加依赖**

在 `</dependencies>` 前（第 143 行）插入：

```xml
        <!-- Color Thief: 图片主色调提取（Median Cut 算法）-->
        <dependency>
            <groupId>de.androidpit</groupId>
            <artifactId>color-thief</artifactId>
            <version>1.1.2</version>
        </dependency>
```

**Step 2: 验证依赖**

```bash
mvn dependency:tree | grep color-thief
```

预期输出包含 `de.androidpit:color-thief:1.1.2`

**Step 3: 提交**

```bash
git add pom.xml && git commit -m "deps: 引入 color-thief-java 1.1.2"
```

---

## Task 2: 创建策略接口

**File:** Create `src/main/java/com/yuwen/visionspace/utils/strategy/ColorExtractStrategy.java`

**Step 1: 写实现**

```java
package com.yuwen.visionspace.utils.strategy;

import java.awt.image.BufferedImage;

/**
 * 颜色提取策略接口
 */
public interface ColorExtractStrategy {

    /**
     * 从 BufferedImage 提取主色调
     *
     * @param image BufferedImage 对象
     * @return 主色调 HEX 字符串（如 "#FF5733"），提取失败返回 null
     */
    String extractDominantColor(BufferedImage image);
}
```

**Step 2: 验证编译**

```bash
mvn compile -q
```

预期：无报错

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/utils/strategy/ColorExtractStrategy.java && git commit -m "feat: 添加 ColorExtractStrategy 策略接口"
```

---

## Task 3: 创建 ColorThiefStrategy

**File:** Create `src/main/java/com/yuwen/visionspace/utils/strategy/ColorThiefStrategy.java`

**Step 1: 写实现**

```java
package com.yuwen.visionspace.utils.strategy;

import de.androidpit.colorthief.ColorThief;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

/**
 * 基于 color-thief-java 的颜色提取策略（Median Cut 算法）
 */
@Slf4j
public class ColorThiefStrategy implements ColorExtractStrategy {

    private static final int QUALITY = 10;

    private static final boolean IGNORE_WHITE = true;

    @Override
    public String extractDominantColor(BufferedImage image) {
        if (image == null) {
            return null;
        }
        try {
            int[] rgb = ColorThief.getColor(image, QUALITY, IGNORE_WHITE);
            if (rgb == null) {
                return null;
            }
            return String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
        } catch (Exception e) {
            log.warn("ColorThief 提取主色调失败", e);
            return null;
        }
    }
}
```

**Step 2: 验证编译**

```bash
mvn compile -q
```

预期：无报错

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/utils/strategy/ColorThiefStrategy.java && git commit -m "feat: 实现 ColorThiefStrategy (Median Cut)"
```

---

## Task 4: 创建 KMeansStrategy

**File:** Create `src/main/java/com/yuwen/visionspace/utils/strategy/KMeansStrategy.java`

**Step 1: 写实现**

```java
package com.yuwen.visionspace.utils.strategy;

import com.yuwen.visionspace.utils.ColorExtractUtils;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;

/**
 * 基于 K-Means 算法的颜色提取策略（保留原实现作为降级备选）
 */
@Slf4j
public class KMeansStrategy implements ColorExtractStrategy {

    @Override
    public String extractDominantColor(BufferedImage image) {
        try {
            return ColorExtractUtils.extractDominantColor(image);
        } catch (Exception e) {
            log.warn("KMeans 提取主色调失败", e);
            return null;
        }
    }
}
```

**Step 2: 验证编译**

```bash
mvn compile -q
```

预期：无报错

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/utils/strategy/KMeansStrategy.java && git commit -m "feat: 实现 KMeansStrategy (K-Means 降级备选)"
```

---

## Task 5: 创建配置属性类

**File:** Create `src/main/java/com/yuwen/visionspace/config/ColorExtractProperties.java`

**Step 1: 写实现**

```java
package com.yuwen.visionspace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 颜色提取配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "vision-space.color-extract")
public class ColorExtractProperties {

    /**
     * 颜色提取方法: median-cut（默认）或 kmeans
     */
    private String method = "median-cut";

    /**
     * 是否启用降级（primary 失败时使用 K-Means）
     */
    private boolean fallbackEnabled = true;
}
```

**Step 2: 验证编译**

```bash
mvn compile -q
```

预期：无报错

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/config/ColorExtractProperties.java && git commit -m "feat: 添加 ColorExtractProperties 配置属性类"
```

---

## Task 6: 创建策略工厂

**File:** Create `src/main/java/com/yuwen/visionspace/utils/strategy/ColorExtractStrategyFactory.java`

**Step 1: 写实现**

```java
package com.yuwen.visionspace.utils.strategy;

import com.yuwen.visionspace.config.ColorExtractProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.awt.image.BufferedImage;

/**
 * 颜色提取策略工厂
 */
@Slf4j
@Component
public class ColorExtractStrategyFactory {

    private static final String METHOD_MEDIAN_CUT = "median-cut";

    private static final String METHOD_KMEANS = "kmeans";

    @Resource
    private ColorExtractProperties properties;

    @Resource
    private ColorThiefStrategy colorThiefStrategy;

    @Resource
    private KMeansStrategy kMeansStrategy;

    /**
     * 创建主要的颜色提取策略
     */
    public ColorExtractStrategy createStrategy() {
        String method = properties.getMethod();
        if (METHOD_KMEANS.equalsIgnoreCase(method)) {
            log.info("使用 K-Means 颜色提取策略");
            return kMeansStrategy;
        }
        log.info("使用 Median Cut 颜色提取策略（color-thief）");
        return colorThiefStrategy;
    }

    /**
     * 提取主色调，支持降级
     *
     * @param image BufferedImage 对象
     * @return 主色调 HEX 字符串，提取失败返回 null
     */
    public String extractDominantColor(BufferedImage image) {
        if (image == null) {
            return null;
        }

        ColorExtractStrategy primary = createStrategy();

        // 尝试主策略
        String color = primary.extractDominantColor(image);
        if (color != null) {
            return color;
        }

        // 降级处理
        if (properties.isFallbackEnabled()) {
            log.info("主策略提取失败，降级到 K-Means");
            return kMeansStrategy.extractDominantColor(image);
        }

        return null;
    }
}
```

**Step 2: 验证编译**

```bash
mvn compile -q
```

预期：无报错

**Step 3: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/utils/strategy/ColorExtractStrategyFactory.java && git commit -m "feat: 实现 ColorExtractStrategyFactory 策略工厂"
```

---

## Task 7: 修改配置文件

**File:** `src/main/resources/application.yml`

**Step 1: 添加配置项**

在文件末尾（第 101 行后）添加：

```yaml
## 颜色提取配置
vision-space:
  color-extract:
    method: median-cut  # median-cut（默认）或 kmeans
    fallback-enabled: true
```

**Step 2: 提交**

```bash
git add src/main/resources/application.yml && git commit -m "feat: 添加 vision-space.color-extract 配置项"
```

---

## Task 8: 修改 PictureUploadTemplate

**File:** `src/main/java/com/yuwen/visionspace/manager/upload/PictureUploadTemplate.java`

**Step 1: 修改注入**

在类中注入工厂：

```java
@Resource
private ColorExtractStrategyFactory colorExtractStrategyFactory;
```

**Step 2: 修改调用（第 109 行）**

原来：
```java
String picColor = ColorExtractUtils.extractDominantColor(image);
```

改为：
```java
String picColor = colorExtractStrategyFactory.extractDominantColor(image);
```

删除 import `com.yuwen.visionspace.utils.ColorExtractUtils`（如果不再使用）

**Step 3: 验证编译**

```bash
mvn compile -q
```

预期：无报错

**Step 4: 提交**

```bash
git add src/main/java/com/yuwen/visionspace/manager/upload/PictureUploadTemplate.java && git commit -m "feat: 接入 ColorExtractStrategyFactory 策略工厂"
```

---

## Task 9: 验证测试

**Step 1: 运行现有测试**

```bash
mvn test -Dtest=ColorExtractUtilsTest -q
```

预期：PASS（K-Means 逻辑未变）

**Step 2: 运行编译**

```bash
mvn compile test-compile -q
```

预期：无报错

---

## Task 10: 功能验证

**Step 1: 启动应用**

```bash
mvn spring-boot:run -q
```

预期：正常启动，无报错

**Step 2: 上传图片测试**

通过前端或 curl 上传一张图片，验证 `pic_color` 字段正常填充。

**Step 3: 测试降级**

修改 `application.yml`：

```yaml
vision-space:
  color-extract:
    method: kmeans
    fallback-enabled: false
```

重启应用，再次上传图片，验证 `pic_color` 正常。

---

## 验收标准检查

| 验收项 | 状态 |
|--------|------|
| 1. `mvn test -Dtest=ColorExtractUtilsTest` 通过 | ⬜ |
| 2. 图片上传后 `picColor` 正常填充 | ⬜ |
| 3. `method=median-cut` 使用 Median Cut | ⬜ |
| 4. `method=kmeans` 使用 K-Means | ⬜ |
| 5. Median Cut 异常时降级到 K-Means | ⬜ |

---

## 变更文件汇总

| 文件 | 操作 |
|------|------|
| `pom.xml` | 修改 |
| `src/main/resources/application.yml` | 修改 |
| `src/main/java/com/yuwen/visionspace/config/ColorExtractProperties.java` | 新增 |
| `src/main/java/com/yuwen/visionspace/utils/strategy/ColorExtractStrategy.java` | 新增 |
| `src/main/java/com/yuwen/visionspace/utils/strategy/ColorThiefStrategy.java` | 新增 |
| `src/main/java/com/yuwen/visionspace/utils/strategy/KMeansStrategy.java` | 新增 |
| `src/main/java/com/yuwen/visionspace/utils/strategy/ColorExtractStrategyFactory.java` | 新增 |
| `src/main/java/com/yuwen/visionspace/manager/upload/PictureUploadTemplate.java` | 修改 |
