# 站内图片 RAG 检索 - Phase 1 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 Agent 新增站内图片向量搜索工具（`searchInternalImages`），支持文本描述搜索站内公开图片。

**Architecture:** ChromaDB（Docker）作为向量存储，DashScope 多模态 Embedding API 生成 1024 维向量，异步嵌入管道在图片上传/编辑/删除时触发，新的 `InternalImageSearchTool` 作为 Agent 独立工具注册。

**Tech Stack:** Spring AI VectorStore (ChromaDB), DashScope Multimodal Embedding (`DashScopeMultimodalEmbeddingApi`), Spring @Async, MyBatis Plus

---

### Task 1: 添加 ChromaDB Maven 依赖

**Files:**
- Modify: `pom.xml`

**Step 1: 添加 spring-ai-starter-vector-store-chroma 依赖**

在 `pom.xml` 的 `<dependencies>` 中，`spring-ai-starter-mcp-client` 依赖之后添加：

```xml
<!-- ChromaDB Vector Store -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-chroma</artifactId>
</dependency>
```

版本由 `spring-ai-bom` (1.1.2) 管理，无需指定。

**Step 2: 验证依赖解析**

Run: `mvn dependency:resolve -q 2>&1 | grep chroma`
Expected: 看到 chroma 相关依赖成功下载

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: 添加 spring-ai-starter-vector-store-chroma 依赖"
```

---

### Task 2: ChromaDB Docker 服务 + 应用配置

**Files:**
- Modify: `docker-compose.yml`
- Modify: `src/main/resources/application-local.yml`
- Modify: `src/main/java/com/yuwen/visionspace/VisionSpaceApplication.java`

**Step 1: 在 docker-compose.yml 中添加 ChromaDB 服务**

在 `redis` 服务之后、`backend` 服务之前插入：

```yaml
  chromadb:
    image: ghcr.io/chroma-core/chroma:1.0.0
    container_name: vision-space-chromadb
    restart: unless-stopped
    ports:
      - "8000:8000"
    volumes:
      - chroma-data:/chroma/chroma
    networks:
      - vision-space
```

在 `volumes:` 部分添加 `chroma-data:`：

```yaml
volumes:
  mysql-data:
  redis-data:
  chroma-data:
```

**Step 2: 在 application-local.yml 中添加 ChromaDB 配置**

在 `spring.ai.dashscope` 配置之后添加：

```yaml
    vectorstore:
      chroma:
        client:
          host: http://localhost
          port: 8000
        collection-name: picture_vectors
        initialize-schema: true
```

**Step 3: 修改 VisionSpaceApplication.java 启用 Embedding**

当前 `DashScopeEmbeddingAutoConfiguration` 被排除。需要移除此排除以让 DashScope 的多模态 Embedding 自动配置生效。

将第 14 行从：
```java
@SpringBootApplication(exclude = { OpenAiAudioSpeechAutoConfiguration.class, DashScopeChatAutoConfiguration.class, DashScopeEmbeddingAutoConfiguration.class})
```
改为：
```java
@SpringBootApplication(exclude = { OpenAiAudioSpeechAutoConfiguration.class, DashScopeChatAutoConfiguration.class})
```

移除 `DashScopeEmbeddingAutoConfiguration.class` 的排除。

同时移除不再需要的 import：
```java
// 删除此行 import
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration;
```

**Step 4: 启动 ChromaDB 并验证应用能启动**

Run:
```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
docker-compose up -d chromadb
docker-compose ps chromadb
```
Expected: chromadb 容器状态为 running

然后启动 Spring Boot 应用，确认无 bean 冲突。

**Step 5: Commit**

```bash
git add docker-compose.yml src/main/resources/application-local.yml src/main/java/com/yuwen/visionspace/VisionSpaceApplication.java
git commit -m "feat: 添加 ChromaDB Docker 服务和 Spring AI VectorStore 配置"
```

---

### Task 3: 添加 Embedding 异步执行器

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/config/AsyncConfig.java`

**Step 1: 在 AsyncConfig 中添加 embeddingExecutor**

在 `picturePreviewExecutor` Bean 之后（第 52 行后）添加：

```java
    @Bean("embeddingExecutor")
    public Executor embeddingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("embedding-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/config/AsyncConfig.java
git commit -m "feat: 添加 embedding 异步执行器"
```

---

### Task 4: 创建 PictureEmbeddingService

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/service/PictureEmbeddingService.java`
- Create: `src/main/java/com/yuwen/visionspace/service/impl/PictureEmbeddingServiceImpl.java`

**Step 1: 创建 PictureEmbeddingService 接口**

```java
package com.yuwen.visionspace.service;

import com.yuwen.visionspace.model.entity.Picture;

import java.util.List;

/**
 * 图片嵌入向量服务
 * 负责将图片元数据转化为向量并存储到 ChromaDB
 */
public interface PictureEmbeddingService {

    /**
     * 为单张图片生成嵌入并存储
     */
    void generateAndStoreEmbedding(Picture picture);

    /**
     * 更新图片的嵌入向量（元数据变更时）
     */
    void updateEmbedding(Picture picture);

    /**
     * 从向量库中删除图片嵌入
     */
    void deleteEmbedding(Long pictureId);

    /**
     * 全量同步：为所有符合条件的图片生成嵌入
     *
     * @return 处理的图片数量
     */
    int fullSync();
}
```

**Step 2: 创建 PictureEmbeddingServiceImpl 实现**

```java
package com.yuwen.visionspace.service.impl;

import com.yuwen.visionspace.model.entity.Picture;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PictureEmbeddingServiceImpl implements PictureEmbeddingService {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private PictureServiceImpl pictureService;

    @Override
    @Async("embeddingExecutor")
    public void generateAndStoreEmbedding(Picture picture) {
        try {
            Document doc = buildDocument(picture);
            vectorStore.add(List.of(doc));
            log.info("图片嵌入生成成功, pictureId={}", picture.getId());
        } catch (Exception e) {
            log.error("图片嵌入生成失败, pictureId={}", picture.getId(), e);
        }
    }

    @Override
    @Async("embeddingExecutor")
    public void updateEmbedding(Picture picture) {
        try {
            String docId = "picture_" + picture.getId();
            vectorStore.delete(List.of(docId));
            Document doc = buildDocument(picture);
            vectorStore.add(List.of(doc));
            log.info("图片嵌入更新成功, pictureId={}", picture.getId());
        } catch (Exception e) {
            log.error("图片嵌入更新失败, pictureId={}", picture.getId(), e);
        }
    }

    @Override
    @Async("embeddingExecutor")
    public void deleteEmbedding(Long pictureId) {
        try {
            String docId = "picture_" + pictureId;
            vectorStore.delete(List.of(docId));
            log.info("图片嵌入删除成功, pictureId={}", pictureId);
        } catch (Exception e) {
            log.error("图片嵌入删除失败, pictureId={}", pictureId, e);
        }
    }

    @Override
    public int fullSync() {
        // 查询所有审核通过的公开图片（spaceId 为空）
        List<Picture> pictures = pictureService.lambdaQuery()
                .isNull(Picture::getSpaceId)
                .eq(Picture::getReviewStatus, 1)
                .select(Picture::getId, Picture::getUrl, Picture::getThumbnailUrl,
                        Picture::getName, Picture::getIntroduction, Picture::getCategory,
                        Picture::getTags, Picture::getPicColor, Picture::getUserId)
                .list();

        if (pictures.isEmpty()) {
            log.info("没有需要索引的公开图片");
            return 0;
        }

        log.info("开始全量同步, 共 {} 张图片", pictures.size());
        int successCount = 0;
        int batchSize = 50;

        for (int i = 0; i < pictures.size(); i += batchSize) {
            List<Picture> batch = pictures.subList(i, Math.min(i + batchSize, pictures.size()));
            List<Document> docs = batch.stream()
                    .map(this::buildDocument)
                    .collect(Collectors.toList());
            try {
                vectorStore.add(docs);
                successCount += batch.size();
                log.info("全量同步进度: {}/{}", Math.min(i + batchSize, pictures.size()), pictures.size());
            } catch (Exception e) {
                log.error("批量嵌入失败, offset={}", i, e);
            }
        }

        log.info("全量同步完成, 成功 {}/{}", successCount, pictures.size());
        return successCount;
    }

    /**
     * 构建嵌入文档
     * Content = 图片的文本描述（用于文本嵌入和搜索）
     * Metadata = 图片元数据（用于过滤和结果展示）
     */
    private Document buildDocument(Picture picture) {
        // 拼接文本内容用于嵌入
        StringBuilder content = new StringBuilder();
        if (picture.getName() != null) {
            content.append(picture.getName());
        }
        if (picture.getIntroduction() != null) {
            content.append("。").append(picture.getIntroduction());
        }
        if (picture.getCategory() != null) {
            content.append("。分类: ").append(picture.getCategory());
        }
        if (picture.getTags() != null && !picture.getTags().equals("[]")) {
            content.append("。标签: ").append(picture.getTags());
        }
        if (picture.getPicColor() != null) {
            content.append("。颜色: ").append(picture.getPicColor());
        }

        // 构建 metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pictureId", picture.getId());
        metadata.put("imageUrl", picture.getUrl() != null ? picture.getUrl() : "");
        metadata.put("thumbnailUrl", picture.getThumbnailUrl() != null ? picture.getThumbnailUrl() : "");
        metadata.put("title", picture.getName() != null ? picture.getName() : "");
        metadata.put("tags", picture.getTags() != null ? picture.getTags() : "[]");
        metadata.put("category", picture.getCategory() != null ? picture.getCategory() : "");
        metadata.put("picColor", picture.getPicColor() != null ? picture.getPicColor() : "");
        metadata.put("spaceId", picture.getSpaceId() != null ? picture.getSpaceId() : 0L);
        metadata.put("userId", picture.getUserId() != null ? picture.getUserId() : 0L);
        metadata.put("indexedAt", System.currentTimeMillis());

        return new Document("picture_" + picture.getId(), content.toString(), metadata);
    }
}
```

**Step 3: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/service/PictureEmbeddingService.java src/main/java/com/yuwen/visionspace/service/impl/PictureEmbeddingServiceImpl.java
git commit -m "feat: 创建 PictureEmbeddingService 图片嵌入向量服务"
```

---

### Task 5: 创建 InternalImageSearchTool

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/tools/InternalImageSearchTool.java`

**Step 1: 创建站内图片搜索工具**

```java
package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 站内图片向量搜索工具
 * 使用 DashScope 多模态 Embedding + ChromaDB 实现语义搜索
 */
@Slf4j
@Component
public class InternalImageSearchTool {

    @Resource
    private VectorStore vectorStore;

    @Tool(name = "searchInternalImages", description = "从站内图片库中通过语义搜索查找图片。适用于用户需要特定风格、主题、颜色或类型的站内已有图片。优先使用此工具搜索站内资源，结果不足时再使用外部搜索。")
    public List<ImageResource> searchInternalImages(
            @ToolParam(description = "搜索描述，使用自然语言描述想要的图片特征，例如：'科技感蓝色背景图'、'温暖的自然风光'、'简洁的商务插图'") String query,
            @ToolParam(description = "返回结果数量，默认 5，最大 10", required = false) Integer topK
    ) {
        int k = (topK != null && topK > 0 && topK <= 10) ? topK : 5;
        List<ImageResource> results = new ArrayList<>();

        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(k)
                            .similarityThreshold(0.5)
                            .build()
            );

            for (Document doc : docs) {
                Map<String, Object> meta = doc.getMetadata();
                String imageUrl = (String) meta.getOrDefault("imageUrl", "");
                String title = (String) meta.getOrDefault("title", "");
                String description = (String) meta.getOrDefault("tags", "") + " " + doc.getText();

                if (!imageUrl.isEmpty()) {
                    results.add(ImageResource.builder()
                            .category(ImageCategoryEnum.CONTENT)
                            .url(imageUrl)
                            .description(description.trim())
                            .build());
                }
            }

            log.info("站内图片搜索完成, query='{}', 结果数={}", query, results.size());
        } catch (Exception e) {
            log.error("站内图片搜索失败: {}", e.getMessage(), e);
        }

        return results;
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/agent/tools/InternalImageSearchTool.java
git commit -m "feat: 创建 InternalImageSearchTool 站内图片向量搜索工具"
```

---

### Task 6: 在 PictureServiceImpl 中嵌入触发钩子

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java`

**Step 1: 注入 PictureEmbeddingService**

在 PictureServiceImpl 类的属性声明区域（其他 `@Resource` 注入附近）添加：

```java
    @Resource
    private PictureEmbeddingService pictureEmbeddingService;
```

**Step 2: 上传后触发嵌入生成**

在 `uploadPicture()` 方法中，找到 `picturePreviewService.generateAndUpdatePreview(...)` 调用（约第 206 行），在其后添加：

```java
        // 异步生成向量嵌入（仅审核通过的公开图片）
        if (picture.getReviewStatus() != null && picture.getReviewStatus() == 1 && picture.getSpaceId() == null) {
            pictureEmbeddingService.generateAndStoreEmbedding(picture);
        }
```

**Step 3: 编辑后触发嵌入更新**

在 `editPicture()` 方法中，找到 `this.updateById(picture)` 调用（约第 534 行），在其后添加：

```java
        // 异步更新向量嵌入
        Picture updatedPicture = this.getById(id);
        if (updatedPicture != null && updatedPicture.getReviewStatus() != null
                && updatedPicture.getReviewStatus() == 1 && updatedPicture.getSpaceId() == null) {
            pictureEmbeddingService.updateEmbedding(updatedPicture);
        }
```

**Step 4: 删除后触发嵌入删除**

在 `deletePicture()` 方法中，找到 `this.clearPictureFile(oldPicture)` 调用（约第 511 行），在其前添加：

```java
        // 异步删除向量嵌入
        pictureEmbeddingService.deleteEmbedding(pictureId);
```

**Step 5: 审核通过时触发嵌入生成**

在 `doPictureReview()` 方法中，找到 `this.updateById(updatePicture)` 调用（约第 378 行），在其后添加：

```java
        // 审核通过且为公开图片时，异步生成向量嵌入
        if (pictureReviewRequest.getReviewStatus() == 1 && oldPicture.getSpaceId() == null) {
            Picture reviewedPicture = this.getById(id);
            if (reviewedPicture != null) {
                pictureEmbeddingService.generateAndStoreEmbedding(reviewedPicture);
            }
        }
```

**Step 6: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java
git commit -m "feat: 在图片上传/编辑/删除/审核流程中嵌入向量触发钩子"
```

---

### Task 7: 注册 Agent 工具 + 更新 System Prompt

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java`

**Step 1: 注入 InternalImageSearchTool**

在 ImageAgent 类的属性区域（第 76 行之后）添加：

```java
    @Resource
    private InternalImageSearchTool internalImageSearchTool;
```

添加 import：
```java
import com.yuwen.visionspace.agent.tools.InternalImageSearchTool;
```

**Step 2: 在 init() 方法中注册工具**

在 `init()` 方法中，将 `internalImageSearchTool` 添加到 `.methodTools(...)` 调用中（第 208 行）：

```java
                .methodTools(imageSearchTool, imageGeneratorTool, qualityEvaluatorTool,
                        similarImageSearchTool, imageFeatureAnalyzerTool, internalImageSearchTool)
```

**Step 3: 在 buildMcpAgent() 方法中也注册**

同样更新 `buildMcpAgent()` 方法中的 `.methodTools(...)` 调用（第 440 行）：

```java
                .methodTools(imageSearchTool, imageGeneratorTool, qualityEvaluatorTool,
                        similarImageSearchTool, imageFeatureAnalyzerTool, internalImageSearchTool)
```

**Step 4: 更新 AGENT_INSTRUCTION System Prompt**

在 `AGENT_INSTRUCTION` 常量中（第 104-193 行），更新 EXPLORATION 阶段的搜索策略说明。

找到 `#### 用户用文字描述需求` 部分（约第 121 行），将内容替换为：

```
            #### 用户用文字描述需求
            1. 首先调用 searchInternalImages 在站内图片库中搜索匹配的图片
            2. 如果站内搜索结果不足，再调用 searchContentImages 从 Pexels 搜索补充
            3. 可按需调用 searchIllustrations 搜索插画
```

同时，在 `#### 用户提供了参考图片 URL` 部分（约第 112 行），在步骤 3 的搜索策略中添加站内搜索选项：

```
            3. 根据分析结果选择搜索策略：
               a. 调用 searchInternalImages 用分析结果中的关键词搜索站内图片库
               b. 调用 searchSimilarImages 以图搜图（寻找视觉相似的图片）
               c. 用分析结果中的 searchKeywords 调用 searchContentImages 关键词搜索（寻找同类型图片）
               d. 以上方式可结合使用，优先站内搜索
```

**Step 5: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/agent/ImageAgent.java
git commit -m "feat: 注册 InternalImageSearchTool 并更新 Agent 搜索策略"
```

---

### Task 8: 创建全量索引管理端点

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/controller/VectorStoreController.java`

**Step 1: 创建向量库管理控制器**

```java
package com.yuwen.visionspace.controller;

import com.yuwen.visionspace.common.BaseResponse;
import com.yuwen.visionspace.common.ResultUtils;
import com.yuwen.visionspace.service.PictureEmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向量库管理接口
 */
@Slf4j
@RestController
@RequestMapping("/vector-store")
public class VectorStoreController {

    @Resource
    private PictureEmbeddingService pictureEmbeddingService;

    /**
     * 全量同步站内图片到向量库
     * 仅管理员可调用
     */
    @PostMapping("/full-sync")
    public BaseResponse<Integer> fullSync() {
        log.info("开始全量同步站内图片到向量库");
        int count = pictureEmbeddingService.fullSync();
        return ResultUtils.success(count);
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/controller/VectorStoreController.java
git commit -m "feat: 创建向量库全量同步管理端点"
```

---

### Task 9: 前端 - 搜索结果来源标识

**Files:**
- Modify: `frontend/src/components/agent/types.ts`
- Modify: `frontend/src/components/agent/message/AssistantMessage.vue`

**Step 1: 在 ImageResource 类型中添加 source 字段**

在 `frontend/src/components/agent/types.ts` 中，给 `ImageResource` 接口添加可选 `source` 字段：

```typescript
export interface ImageResource {
  url: string
  title?: string
  source?: 'internal' | 'external' | 'generated'  // 来源标识
}
```

**Step 2: 在 AssistantMessage.vue 中展示来源标识**

在 AssistantMessage.vue 的图片缩略图区域，为每张图片添加来源标签。

找到缩略图渲染部分，在 `<img>` 标签上添加来源角标：

```html
<!-- 站内图片角标 -->
<span v-if="img.source === 'internal'" class="image-source-badge internal">站内</span>
```

添加对应 CSS：
```css
.image-source-badge {
  position: absolute;
  top: 4px;
  left: 4px;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  color: #fff;
}
.image-source-badge.internal {
  background: #52c41a;
}
```

**Step 3: Commit**

```bash
git add frontend/src/components/agent/types.ts frontend/src/components/agent/message/AssistantMessage.vue
git commit -m "feat: 前端搜索结果添加站内图片来源标识"
```

---

### Task 10: 端到端集成测试

**Files:** 无新文件

**Step 1: 启动所有服务**

```bash
cd D:/Work/code/VisionSpace/.worktrees/feature-agent-dev
docker-compose up -d
```

确认 MySQL、Redis、ChromaDB 均正常运行。

**Step 2: 启动 Spring Boot 应用**

确认日志中无错误，特别关注：
- ChromaDB VectorStore bean 初始化成功
- DashScope EmbeddingModel bean 初始化成功
- Agent 工具注册包含 `searchInternalImages`

**Step 3: 执行全量同步**

```bash
curl -X POST http://localhost:8081/api/vector-store/full-sync \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>"
```

检查返回的同步数量是否合理。

**Step 4: 在 Agent 聊天中测试**

在 Agent 聊天界面发送：
- "找一张科技感的蓝色背景图"
- "搜索站内的自然风光图片"

验证 Agent 是否调用了 `searchInternalImages` 工具，是否返回了站内图片结果。

**Step 5: Commit 最终状态（如有修复）**

```bash
git add -A
git commit -m "fix: 集成测试修复"
```

---

## 文件变更总览

| 操作 | 文件路径 |
|------|----------|
| Modify | `pom.xml` |
| Modify | `docker-compose.yml` |
| Modify | `src/main/resources/application-local.yml` |
| Modify | `src/main/java/com/yuwen/visionspace/VisionSpaceApplication.java` |
| Modify | `src/main/java/com/yuwen/visionspace/config/AsyncConfig.java` |
| Modify | `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java` |
| Modify | `src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java` |
| Modify | `frontend/src/components/agent/types.ts` |
| Modify | `frontend/src/components/agent/message/AssistantMessage.vue` |
| Create | `src/main/java/com/yuwen/visionspace/service/PictureEmbeddingService.java` |
| Create | `src/main/java/com/yuwen/visionspace/service/impl/PictureEmbeddingServiceImpl.java` |
| Create | `src/main/java/com/yuwen/visionspace/agent/tools/InternalImageSearchTool.java` |
| Create | `src/main/java/com/yuwen/visionspace/controller/VectorStoreController.java` |

## 参考文档

- 设计文档: `docs/plans/2026-04-05-rag-image-retrieval-design.md`
- Spring AI ChromaDB: https://java2ai.com/integration/rag/vectordbs/more/chroma
- DashScope 多模态 Embedding: https://help.aliyun.com/document_detail/2714256.html
- Spring AI Alibaba RAG: https://java2ai.com/docs/frameworks/agent-framework/advanced/rag
