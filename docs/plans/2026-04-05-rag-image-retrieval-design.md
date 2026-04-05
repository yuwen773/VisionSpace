# 站内图片 RAG 检索设计

> 日期: 2026-04-05
> 状态: 已批准
> 分支: feature/agent-dev

## 背景

VisionSpace 已有完整的 AI Agent 系统（ReactAgent + 7 个工具 + MCP），但图片搜索完全依赖外部 API（Pexels 关键词搜索、百度以图搜图），无法检索站内已有的图片资源。

站内图片库积累了大量经过审核、带丰富元数据（标签、描述、分类、颜色）的图片，用户在 Agent 聊天中搜索时无法利用这些站内资源。

## 目标

为 Agent 新增站内图片向量搜索能力（`searchInternalImages` 工具），支持双模态检索（文本搜图 + 以图搜图），让 Agent 能在站内图片库中找到语义匹配的图片。

## 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| 向量数据库 | ChromaDB (Docker) | 轻量级，Spring AI 原生支持，适合 MVP (< 10万图片) |
| Embedding 模型 | DashScope 多模态 Embedding | 已有 `DashScopeMultimodalEmbeddingApi`，与现有架构一致 |
| 向量维度 | 1024 | `multimodal-embedding-v1` 默认输出维度 |
| 存储方式 | Docker 容器 | 与现有 MySQL/Redis 一致，统一管理 |

## 架构设计

```
┌─────────────────────────────────────────────────────┐
│                    VisionSpace 后端                    │
│                                                       │
│  ┌──────────────┐    ┌────────────────────────────┐  │
│  │ 图片上传/编辑  │───→│  ImageEmbeddingService     │  │
│  │ (现有触发点)   │    │  (异步嵌入管道)              │  │
│  └──────────────┘    │  - 提取图片 URL + 元数据      │  │
│                       │  - 调用 DashScope 多模态 API  │  │
│                       │  - 写入 ChromaDB             │  │
│                       └────────┬───────────────────┘  │
│                                │                      │
│  ┌──────────────────┐    ┌─────▼──────────────────┐  │
│  │ InternalImageSearchTool│  │  ChromaDB             │  │
│  │ (Agent 新工具)         │  │  (Docker 服务 :8000)   │  │
│  │ - 文本搜图 (Phase 1)  │←─│  - picture_vectors 集合 │  │
│  │ - 以图搜图 (Phase 2)  │  │  - 1024 维向量          │  │
│  └──────────────────┘    └────────────────────────┘  │
│       │                                                │
│       ▼                                                │
│  ┌──────────────┐                                     │
│  │ ReactAgent   │ ← 现有 Agent 架构                    │
│  │ (image_assistant)                                  │
│  └──────────────┘                                     │
└─────────────────────────────────────────────────────┘
```

## 数据模型

### ChromaDB Collection: `picture_vectors`

```
Document ID: picture_{id}
Content: 图片描述文本（标题 + 标签 + 描述 + 分类拼接）
Embedding: 1024 维向量（DashScope multimodal-embedding-v1）
Metadata:
  - pictureId: Long        (站内图片 ID)
  - title: String           (图片标题)
  - tags: String[]          (标签列表)
  - category: String        (分类)
  - imageUrl: String        (原图 URL，用于以图搜图)
  - thumbnailUrl: String    (缩略图 URL)
  - picColor: String        (主色调)
  - spaceId: Long           (空间 ID)
  - spaceType: String       (空间类型，权限过滤用)
  - userId: Long            (上传者 ID)
  - reviewStatus: Integer   (审核状态)
  - indexedAt: Long         (索引时间戳)
```

## 嵌入管道

### 嵌入策略

| 策略 | 输入 | 用途 | Phase |
|------|------|------|-------|
| 文本嵌入 | 图片标题 + 标签 + 描述 + 分类 → 拼接为文本 | 文本搜图 | Phase 1 |
| 图片嵌入 | 图片 URL → DashScope 多模态 API | 以图搜图 | Phase 2 |

### 触发时机

| 事件 | 动作 |
|------|------|
| 图片上传/审核通过 | 异步生成嵌入 → 写入 ChromaDB |
| 图片编辑（标题/标签/描述变更） | 异步更新嵌入 → 更新 ChromaDB |
| 图片删除 | 从 ChromaDB 删除对应向量 |
| 系统启动/首次部署 | 全量同步：扫描所有公开图片 → 批量生成嵌入 |

### 嵌入内容拼接规则

```
文本内容 = "{title}。标签: {tags}。分类: {category}。颜色: {picColor}。描述: {description}"
```

## Agent 工具设计

### InternalImageSearchTool

```java
@Tool(name = "searchInternalImages", description = "从站内图片库中搜索图片")
public record InternalImageSearchTool(VectorStore vectorStore) {

    record Request(
        String query,           // 搜索文本描述
        Integer topK,           // 返回数量，默认 5
        String category         // 可选分类过滤
    ) {}

    record Response(
        List<ImageResult> images,
        int totalFound,
        String searchMode       // "text" 或 "image"
    ) {}

    record ImageResult(
        Long pictureId,
        String title,
        String imageUrl,
        String thumbnailUrl,
        String description,
        List<String> tags,
        Double score            // 相似度分数
    ) {}
}
```

### Agent 工具列表（更新后）

```
ReactAgent (image_assistant) 可用工具:
  searchContentImages        — Pexels 关键词搜索 (现有)
  searchSimilarImages        — 百度以图搜图 (现有)
  generateImages             — AI 生成图片 (现有)
  analyzeImageFeatures       — 图片特征分析 (现有)
  evaluateImageQuality       — 质量评估 (现有)
  searchInternalImages       — 站内向量搜索 (新增)
```

### System Prompt 更新

在 Agent 系统提示中添加 `searchInternalImages` 工具说明，搜索策略：用户未指定来源时，优先搜站内 → 不够再搜 Pexels。

## 依赖变更

### pom.xml 新增

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-chroma</artifactId>
</dependency>
```

### Docker Compose 新增

```yaml
chromadb:
  image: ghcr.io/chroma-core/chroma:1.0.0
  ports:
    - "8000:8000"
  volumes:
    - chroma-data:/chroma/chroma
```

### 配置变更 (application-local.yml)

```yaml
spring:
  ai:
    vectorstore:
      chroma:
        client:
          host: http://localhost
          port: 8000
        collection-name: picture_vectors
        initialize-schema: true
```

## 前端影响

- 搜索结果中展示图片来源标识（站内/Pexels/百度）
- 其他前端改动最小，工具结果走现有 SSE 流式渲染

## 分阶段路线图

### Phase 1: 公开图片 + 文本搜图（MVP）

- ChromaDB Docker 部署 + Spring AI 集成
- `ImageEmbeddingService` 异步嵌入管道（文本嵌入）
- `InternalImageSearchTool` 文本搜图工具
- 全量索引脚本（扫描现有公开图片）
- Agent System Prompt 更新
- 前端图片来源标识

### Phase 2: 以图搜图

- 图片嵌入管道（图片 URL → DashScope 多模态 API）
- `InternalImageSearchTool` 扩展支持图片输入
- 搜索结果混合排序（文本 + 图片向量融合）

### Phase 3: 权限过滤 + 私有空间

- 基于 spaceId/spaceType 的向量搜索元数据过滤
- 私有空间图片索引
- 增量同步优化（WebSocket 或事件驱动）

## 参考文档

- [Spring AI Alibaba RAG 文档](https://java2ai.com/docs/frameworks/agent-framework/advanced/rag)
- [ChromaDB 集成文档](https://java2ai.com/integration/rag/vectordbs/more/chroma)
- [DashScope 多模态 Embedding API](https://help.aliyun.com/document_detail/2714256.html)
- 项目现有设计: `docs/plans/2026-03-28-agentic-rag-design.md`
