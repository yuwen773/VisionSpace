# VisionSpace

云端智能图片管理与协作平台，提供图片上传、管理、分享、颜色搜索、AI 扩图、智能推荐和 AI Agent 对话等功能。

## 技术栈

### 后端

| 类别 | 技术/版本 |
|------|-----------|
| 框架 | Spring Boot 3.5.8 |
| Java | 21 (虚拟线程) |
| 数据库 | MySQL 8.0 |
| ORM | MyBatis Plus 3.5.16 |
| 缓存 | Redis 7 + Caffeine |
| 认证 | Sa-Token 1.45.0 |
| AI | Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.2 (DashScope) |
| 接口文档 | Knife4j 4.4.0 |
| 高性能队列 | LMAX Disruptor 3.4.2 |
| 文件存储 | x-file-storage 2.2.1 (COS/OSS/MinIO) |
| MCP 客户端 | Spring AI MCP Client |

### 前端

| 类别 | 技术/版本 |
|------|-----------|
| 框架 | Vue 3.5 + TypeScript 5.6 |
| 构建工具 | Vite 6 |
| UI 组件 | Ant Design Vue 4 |
| 状态管理 | Pinia |
| 路由 | Vue Router 4 |
| 图表 | ECharts 6 |
| 图标 | lucide-vue-next |
| 3D 效果 | vue-bits (Three.js) |
| Markdown | markdown-it + highlight.js |

### 部署

Docker Compose（MySQL 8.0 + Redis 7 + Spring Boot）

## 功能特性

### 用户系统
- 注册、登录、注销
- VIP 兑换码激活
- 个人中心（资料/VIP/反馈/图片）

### 图片管理
- 多方式上传：本地上传、URL 上传、批量上传
- 图片编辑、裁剪、标签/分类管理
- 图片审核机制（待审核/通过/拒绝）
- 自动提取主色调、生成预览缩略图

### 智能搜索
- **颜色搜索**：K-Means / Median Cut 双策略提取主色调，按颜色检索相似图片
- **以图搜图**：基于图像特征的相似图片搜索

### 推荐系统
- 首页瀑布流推荐（热门/最新/质量/随机）
- 多维加权评分（互动/时效/质量/转化/手动）
- 多样性重排（作者去重、分类打散）
- Redis + Caffeine 二级缓存

### AI 能力
- **AI 扩图**：阿里云 Outpainting 集成
- **AI Agent**：基于 ReactAgent 的多工具智能体，支持图片搜索、生成、特征分析、质量评估、相似图搜索、Mermaid 图表生成
- **流式对话**：SSE 实时流式响应，工具调用可视化
- **MCP 支持**：动态加载 MCP 服务器工具

### 空间协作
- 私有空间 / 团队空间
- 空间级别（普通/专业/旗舰）
- 成员角色权限（查看者/编辑/管理员）
- WebSocket 实时协作编辑（Disruptor 事件驱动）

### 数据分析
- 空间使用量、分类、标签、大小、用户、排名 6 维图表
- 用户行为追踪（曝光/点击/浏览/点赞/收藏/下载/分享）
- 统计数据自动聚合

### 其他
- 双主题切换：极光主题（深空暗色）/ 紫漾主题（梦幻紫粉）
- 多平台存储（腾讯云 COS / 阿里云 OSS / MinIO）
- 用户反馈系统
- 管理后台（Dashboard、用户/图片/空间/存储/反馈管理）

## 项目结构

```
src/main/java/com/yuwen/visionspace/
├── annotation/             # 自定义注解（@AuthCheck, @SaSpaceCheckPermission）
├── aop/                    # AOP 切面（AuthInterceptor 权限拦截）
├── config/                 # 配置类（Knife4j, MyBatis Plus, Sa-Token 等）
├── constant/               # 常量定义
├── controller/             # REST 控制器（13 个）
│   ├── AgentController.java          # AI Agent 对话
│   ├── PictureController.java         # 图片 CRUD
│   ├── SpaceController.java           # 空间管理
│   ├── UserController.java           # 用户管理
│   ├── McpServerController.java      # MCP 服务器配置
│   ├── SpaceAnalyzeController.java   # 空间分析
│   ├── PictureRecommendController.java # 推荐
│   ├── PictureActionController.java   # 行为追踪
│   ├── StorageConfigController.java   # 存储配置
│   ├── FeedbackController.java        # 反馈
│   └── AdminStatsController.java      # 管理统计
├── exception/               # 异常处理（BusinessException, ThrowUtils）
├── manager/                 # 跨切面管理器
│   ├── auth/               # 权限管理
│   │   ├── StpKit.java              # Sa-Token 门面（双 StpLogic）
│   │   ├── SpaceUserAuthManager.java # 空间权限管理
│   │   └── StpInterfaceImpl.java     # 权限接口实现
│   ├── upload/             # 图片上传（模板方法模式）
│   │   ├── PictureUploadTemplate.java # 上传流程模板
│   │   ├── FilePictureUpload.java     # 本地文件上传
│   │   └── UrlPictureUpload.java       # URL 图片上传
│   ├── websocket/          # 实时协作（Disruptor）
│   │   ├── PictureEditEvent.java      # 事件对象
│   │   ├── PictureEditEventProducer.java # 生产者
│   │   └── PictureEditEventWorkHandler.java # 消费者
│   ├── storage/           # 存储服务（COS/OSS/MinIO）
│   └── cache/             # 缓存管理
│       └── RecommendCacheManager.java # Redis + Caffeine 二级缓存
├── agent/                   # AI Agent 智能体
│   ├── ImageAgent.java    # 核心 Agent（ReactAgent）
│   ├── tools/             # Agent 工具
│   │   ├── ImageSearchTool.java       # 图片搜索
│   │   ├── ImageGeneratorTool.java    # 图片生成
│   │   ├── ImageFeatureAnalyzerTool.java # 特征分析
│   │   ├── QualityEvaluatorTool.java  # 质量评估
│   │   ├── SimilarImageSearchTool.java # 相似图搜索
│   │   ├── MermaidDiagramTool.java    # Mermaid 图表
│   │   └── UndrawIllustrationTool.java # 插画生成
│   ├── hooks/             # Agent 钩子
│   │   ├── IterationControlHook.java  # 迭代控制
│   │   ├── MessageTrimmingHook.java    # 消息裁剪
│   │   ├── SummarizationHook.java     # 对话摘要
│   │   └── UserPreferenceLoaderHook.java # 偏好加载
│   └── mcp/               # MCP 协议支持
│       ├── TextMcpModelInterceptor.java # MCP 回调解析
│       ├── McpToolCallback.java        # 工具回调
│       └── McpToolCallbackProvider.java # 回调提供者
├── model/                  # 数据模型
│   ├── dto/               # 请求 DTO
│   ├── entity/            # 实体类
│   ├── enums/             # 枚举
│   └── vo/                # 响应 VO
├── mapper/                 # MyBatis Plus Mapper
├── service/                # 服务层（接口 + Impl）
└── utils/                  # 工具类

frontend/src/
├── api/                    # API 接口
│   ├── index.ts           # 聚合导出
│   ├── pictureController.ts
│   ├── userController.ts
│   ├── spaceController.ts
│   ├── agentController.ts  # SSE 流式调用
│   ├── pictureRecommend.ts # 推荐接口
│   ├── pictureAction.ts   # 行为接口
│   └── typings.d.ts       # OpenAPI 生成类型
├── components/
│   ├── agent/             # Agent 对话组件
│   │   ├── AgentChatInput.vue
│   │   ├── AgentMessageList.vue
│   │   └── LeftSidebar.vue
│   ├── analyze/           # 数据分析图表
│   │   ├── SpaceCategoryAnalyze.vue
│   │   ├── SpaceRankAnalyze.vue
│   │   └── SpaceUsageAnalyze.vue
│   ├── admin/             # 管理后台组件
│   ├── user-center/       # 用户中心组件
│   ├── vue-bits/          # 3D 效果组件
│   └── GlobalHeader.vue, PictureList.vue 等
├── composables/           # 组合式函数
│   ├── useTheme.ts        # 双主题切换
│   ├── useAgentStream.ts   # SSE 流式处理
│   ├── useMarkdown.ts     # Markdown 渲染
│   └── useDiagramRenderer.ts # 图表渲染
├── layouts/
│   ├── BasicLayout.vue     # 用户端布局
│   └── AdminLayout.vue     # 管理端布局
├── pages/
│   ├── HomePage.vue        # 首页
│   ├── AddPicturePage.vue # 图片上传
│   ├── PictureDetailPage.vue # 图片详情
│   ├── SpaceDetailPage.vue # 空间详情
│   ├── AgentChatPage.vue  # AI Agent 对话
│   └── admin/             # 管理后台页面
├── stores/
│   └── userLogin.ts       # 登录用户状态
├── styles/
│   ├── design-tokens.css  # 极光主题
│   └── design-tokens-ziyan.css # 紫漾主题
├── request.ts             # Axios 实例
├── access.ts              # 路由鉴权
└── main.ts                # 入口
```

## 快速开始

### 前置条件

- JDK 21+
- Node.js 18+
- Maven 3.9+
- MySQL 8.0+
- Redis 7+

### Docker Compose 一键启动

```bash
git clone https://github.com/yuwen773/VisionSpace.git
cd VisionSpace

# 启动 MySQL + Redis + 后端
docker-compose up -d

# 启动前端
cd frontend
npm install
npm run dev
```

### 手动启动

#### 后端

```bash
# 克隆项目
git clone https://github.com/yuwen773/VisionSpace.git
cd VisionSpace

# 配置数据库和 Redis
# 编辑 src/main/resources/application-local.yml

# 启动后端
mvn spring-boot:run
```

#### 前端

```bash
cd frontend
npm install
npm run dev
```

### 接口文档

启动后访问：`http://localhost:8081/api/doc.html`

## 核心模块

### 颜色搜索

支持双策略提取主色调并按颜色检索：

```java
// K-Means 策略
String dominantColor = ColorExtractUtils.extractDominantColor(image);

// 相似度计算
double similarity = ColorSimilarUtils.calculateSimilarity("#FF5733", "#FF0000");
```

### 推荐引擎

多维加权评分 + 多样性重排：

```
综合得分 = 互动分×35% + 时效分×25% + 质量分×20% + 转化分×10% + 手动分×10%
→ 作者去重 → 分类打散 → 最终推荐列表
```

### AI Agent

基于 Spring AI Alibaba 的多工具智能体：

| 工具 | 功能 |
|------|------|
| ImageSearchTool | 图片搜索 |
| ImageGeneratorTool | 图片生成 |
| ImageFeatureAnalyzerTool | 特征分析 |
| QualityEvaluatorTool | 质量评估 |
| SimilarImageSearchTool | 相似图搜索 |
| MermaidDiagramTool | Mermaid 图表生成 |
| UndrawIllustrationTool | 插画生成 |

**Agent Hooks：**

| Hook | 作用 |
|------|------|
| IterationControlHook | 探索/生成阶段切换和迭代次数控制 |
| MessageTrimmingHook | 裁剪过长对话历史 |
| SummarizationHook | 对话摘要压缩 |
| UserPreferenceLoaderHook | 用户偏好加载 |

### 空间权限控制

基于 Sa-Token 的多级权限体系：

**双 StpLogic 架构：**
```java
public static final StpLogic DEFAULT = new StpLogic();      // 默认会话
public static final StpLogic SPACE = new StpLogic("space"); // 空间会话
```

**权限注解：**

| 注解 | 作用 |
|------|------|
| `@AuthCheck(mustRole = "admin")` | 全局角色校验 |
| `@SaSpaceCheckPermission("picture:upload")` | 空间级权限校验 |

**空间成员角色：**

| 角色 | 权限 |
|------|------|
| viewer | 查看空间图片 |
| editor | 上传、编辑图片 |
| admin | 空间管理、成员管理 |

### 存储架构

基于 x-file-storage 的多平台存储统一抽象：

- 腾讯云 COS
- 阿里云 OSS
- MinIO

管理端可动态切换存储平台、配置参数。

## 数据库

### 核心表

| 表名 | 用途 | 主键 |
|------|------|------|
| user | 用户信息 | id (bigint) |
| picture | 图片信息 | id (bigint) |
| picture_stats | 图片统计（浏览/点赞/收藏/下载/分享） | id |
| user_picture_action | 用户行为事件（曝光/点击/浏览/点赞/收藏/下载/分享） | id |
| space | 空间 | id (bigint) |
| space_user | 空间成员 | id |
| storage_config | 存储配置（COS/OSS/MinIO） | id |
| user_feedback | 用户反馈 | id (bigint) |
| agent_session | Agent 会话 | id |
| agent_message | Agent 消息 | id |

### 数据规范

- 字段命名：**camelCase**（如 `userId`, `createdTime`）
- 逻辑删除：`isDelete` 字段（值为 1 表示删除）
- 图片主色调：`picColor` 存储十六进制值
- 所有表使用 InnoDB 引擎，`utf8mb4_unicode_ci` 字符集

## 配置说明

| 配置文件 | 用途 |
|---------|------|
| `application.yml` | 主配置 |
| `application-local.yml` | 本地开发（不上版本库） |
| `application-prod.yml` | 生产环境 |

## API 模块

| 模块 | 路径 | 核心功能 |
|------|------|----------|
| 用户 | /api/user | 注册、登录、信息管理、VIP |
| 图片 | /api/picture | 上传、编辑、审核、搜索、扩图 |
| 空间 | /api/space | 空间管理、成员管理 |
| 空间分析 | /api/space_analyze | 使用量、分类、标签、排名分析 |
| 存储 | /api/storage | 存储平台配置管理 |
| 反馈 | /api/feedback | 反馈提交与管理 |
| 推荐引擎 | /api/picture/recommend | 首页推荐（热门/最新/质量/随机） |
| 行为追踪 | /api/picture/action | 曝光/点击/点赞/收藏/下载/分享 |
| AI Agent | /api/agent | 流式对话、会话管理、历史记录 |
| MCP | /api/mcp | MCP 服务器配置管理 |
| 管理统计 | /api/admin | 管理后台 Dashboard |

> **注意：** 所有接口已配置 `/api` 前缀，无需在控制器路径中手动添加

## License

MIT
