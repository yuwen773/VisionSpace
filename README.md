# VisionSpace

云端智能图片管理与协作平台，提供图片上传、管理、分享、颜色搜索、AI 扩图、智能推荐和 AI Agent 对话等功能。

## 技术栈

### 后端

| 类别 | 技术/版本 |
|------|-----------|
| 框架 | Spring Boot 3.5.8 |
| Java | 21 |
| 数据库 | MySQL 8.0 |
| ORM | MyBatis Plus 3.5.16 |
| 缓存 | Redis 7 + Spring Session |
| 认证 | Sa-Token 1.45.0 |
| AI | Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.2 |
| 接口文档 | Knife4j 4.4.0 + SpringDoc |
| 高性能队列 | Disruptor |
| 文件存储 | x-file-storage（COS/OSS/MinIO） |
| 虚拟线程 | Spring Virtual Threads |

### 前端

| 类别 | 技术/版本 |
|------|-----------|
| 框架 | Vue 3.5.13 + TypeScript 5.6.3 |
| 构建工具 | Vite 6.0.1 |
| UI 组件 | Ant Design Vue 4.2.6 |
| 状态管理 | Pinia 2.2.6 |
| 路由 | Vue Router 4.4.5 |
| 图表 | ECharts 6.0.0 |
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

### AI 能力
- **AI 扩图**：阿里云 Outpainting 集成
- **AI Agent**：多工具智能体，支持图片搜索、生成、特征分析、质量评估、相似图搜索
- **流式对话**：SSE 实时流式响应，工具调用可视化

### 空间协作
- 私有空间 / 团队空间
- 空间级别（普通/专业/旗舰）
- 成员角色权限（查看者/编辑/管理员）
- WebSocket 实时协作编辑

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
├── annotation/             # 自定义注解
├── api/                    # 外部 API 封装
│   ├── aliyunai/          # 阿里云 AI（扩图）
│   └── imagesearch/       # 图片搜索
├── aop/                   # AOP 切面
├── agent/                 # AI Agent 智能体
│   ├── ImageAgent.java    # 核心 Agent（ReactAgent 模式）
│   ├── tools/             # Agent 工具（搜索/生成/分析/评估）
│   ├── hooks/             # Agent 钩子（迭代控制/消息裁剪/摘要/偏好加载）
│   └── model/             # Agent 数据模型
├── common/                # 通用响应（ResultUtils, BaseResponse）
├── config/                # 配置类
├── constant/              # 常量定义
├── controller/            # REST 控制器
├── exception/             # 异常处理
├── job/                   # 定时任务
├── manager/               # 跨组件管理器
│   ├── auth/             # 权限管理（Sa-Token + 空间权限）
│   ├── upload/            # 图片上传（模板方法模式）
│   ├── websocket/         # 实时协作（Disruptor 事件处理）
│   ├── storage/           # 存储服务（COS/OSS/MinIO）
│   └── cache/             # 缓存管理（推荐缓存）
├── mapper/                # MyBatis Plus Mapper
├── model/                 # 数据模型
│   ├── dto/              # 请求 DTO
│   ├── entity/           # 实体类
│   ├── enums/            # 枚举
│   └── vo/               # 响应 VO
├── service/              # 服务层（13 个服务）
└── utils/                # 工具类（颜色/推荐/压缩/预览）

frontend/src/
├── api/                    # Axios API 接口（12 个模块）
├── components/            # 组件
│   ├── agent/            # AI Agent 对话组件（消息/工具/资源面板）
│   ├── analyze/          # 数据分析图表组件（6 种）
│   ├── admin/            # 管理后台组件
│   ├── user-center/      # 用户中心子组件
│   └── icons/            # 图标组件
├── composables/           # 组合式函数（useTheme / useAgentStream / useMarkdown）
├── constants/             # 常量
├── layouts/               # 布局（BasicLayout, AdminLayout）
├── pages/                 # 页面
│   ├── user/             # 登录/注册
│   └── admin/            # 管理后台（7 个页面）
├── router/               # 路由配置
├── stores/               # Pinia 状态管理
└── styles/               # 主题样式（深空暗黑 + 紫漾双主题）
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

### 空间权限控制

基于 Sa-Token 的多级权限体系：

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

| 表名 | 用途 |
|------|------|
| user | 用户信息 |
| picture | 图片信息 |
| picture_stats | 图片统计（浏览/点赞/收藏/下载/分享） |
| user_picture_action | 用户行为事件 |
| space | 空间 |
| space_user | 空间成员 |
| storage_config | 存储配置 |
| user_feedback | 用户反馈 |
| agent_session | Agent 会话 |
| agent_message | Agent 消息 |

### 数据规范

- 字段命名：**camelCase**（如 `userId`, `createdTime`）
- 逻辑删除：`isDelete` 字段
- 图片主色调：`picColor` 存储十六进制值

## 配置说明

| 配置文件 | 用途 |
|---------|------|
| `application.yml` | 主配置 |
| `application-local.yml` | 本地开发（不上版本库） |
| `application-prod.yml` | 生产环境 |

## API 模块

| 模块 | 路径 | 核心功能 |
|------|------|----------|
| 用户 | `/user` | 注册、登录、信息管理、VIP |
| 图片 | `/picture` | 上传、编辑、审核、搜索、扩图 |
| 空间 | `/space` | 空间管理、成员管理 |
| 空间分析 | `/space_analyze` | 使用量、分类、标签、排名分析 |
| 存储 | `/storage` | 存储平台配置管理 |
| 反馈 | `/feedback` | 反馈提交与管理 |
| 推荐引擎 | `/picture/recommend` | 首页推荐（热门/最新/质量/随机） |
| 行为追踪 | `/picture/action` | 曝光/点击/点赞/收藏/下载/分享 |
| AI Agent | `/agent` | 流式对话、会话管理、历史记录 |
| 管理统计 | `/admin` | 管理后台 Dashboard |

> **注意：** 控制器路径无需添加 `/api` 前缀，已在 `application.yml` 中配置 `server.servlet.context-path: /api`

## License

MIT
