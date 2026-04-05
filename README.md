# VisionSpace

云端智能图片管理与协作平台，提供图片上传、管理、分享等功能，支持多主题切换、颜色搜索和 AI 扩图。

## 技术栈

### 后端

| 类别 | 技术/版本 |
|------|-----------|
| 框架 | Spring Boot 3.5.8 |
| Java | 21 |
| 数据库 | MySQL 5.7+ |
| ORM | MyBatis Plus 3.5.16 |
| 缓存 | Redis + Spring Session |
| 认证 | Sa-Token 1.45.0 |
| AI | Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.2 |
| 接口文档 | Knife4j 4.4.0 + SpringDoc |
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

## 功能特性

- **用户管理**: 注册、登录、VIP 兑换、用户列表
- **空间管理**: 团队空间、空间级别（普通/专业/旗舰）、权限控制
- **图片管理**: 本地上传、URL 上传、批量上传、标签编辑、分类管理、审核机制
- **颜色搜索**: K-Means 提取主色调，按颜色检索相似图片
- **以图搜图**: 基于图像特征的相似图片搜索
- **主题切换**: 极光主题（深空暗色）/ 紫漾主题（梦幻紫粉）
- **数据分析**: 空间使用统计、分类分析、标签分析、用户排名
- **AI 扩图**: 阿里云 Outpainting 能力集成
- **实时协作**: WebSocket 图片编辑同步
- **用户反馈**: 反馈提交与管理

## 项目结构

```
src/main/java/com/yuwen/visionspace/
├── annotation/             # 自定义注解
├── api/                    # 外部 API 封装
│   ├── aliyunai/          # 阿里云 AI（扩图）
│   └── imagesearch/       # 图片搜索
├── aop/                   # AOP 切面
├── common/                # 通用响应 (ResultUtils, BaseResponse)
├── config/                # 配置类
├── constant/              # 常量定义
├── controller/            # REST 控制器
├── exception/             # 异常处理
├── job/                   # 定时任务
├── manager/               # 跨组件管理器
│   ├── auth/             # 权限管理
│   ├── upload/            # 图片上传
│   ├── websocket/         # 实时协作
│   └── storage/           # 存储服务（COS/OSS/MinIO）
├── mapper/                # MyBatis Plus Mapper
├── model/                 # 数据模型
│   ├── dto/              # 请求 DTO
│   ├── entity/           # 实体类
│   ├── enums/            # 枚举
│   └── vo/               # 响应 VO
├── service/              # 服务层
└── utils/                # 工具类

frontend/src/
├── api/                    # Axios API 接口
├── components/            # 组件
│   ├── analyze/          # 数据分析组件
│   ├── admin/            # 管理后台组件
│   └── icons/            # 图标组件
├── composables/           # 组合式函数 (useTheme)
├── constants/             # 常量
├── layouts/               # 布局 (BasicLayout, AdminLayout)
├── pages/                 # 页面
│   ├── user/             # 用户页面
│   └── admin/            # 管理后台页面
├── router/               # 路由配置
├── stores/               # Pinia 状态管理
└── styles/               # 主题样式
```

## 快速开始

### 前置条件

- JDK 21+
- Node.js 18+
- Maven 3.9+
- MySQL 5.7+
- Redis 6.0+

### 后端启动

```bash
# 克隆项目
git clone https://github.com/yuwen773/VisionSpace-.git
cd VisionSpace

# 配置数据库和 Redis
# 编辑 src/main/resources/application-local.yml

# 启动后端
mvn spring-boot:run
```

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

### 接口文档

启动后访问：`http://localhost:8081/api/doc.html`

## 核心模块

### 图片上传

图片上传自动提取主色调并存储：

```java
// ColorExtractUtils.java - K-Means 颜色提取
String dominantColor = ColorExtractUtils.extractDominantColor(image);
// 返回格式: #FF5733
```

### 颜色搜索

支持按颜色搜索相似图片：

```java
// 使用 ColorSimilarUtils 计算相似度
double similarity = ColorSimilarUtils.calculateSimilarity("#FF5733", "#FF0000");
// 返回 0-1 之间的相似度
```

### 空间权限控制

基于 Sa-Token 的多级权限体系：

| 角色 | 权限 |
|------|------|
| viewer | 查看空间图片 |
| editor | 上传、编辑图片 |
| admin | 空间管理、成员管理 |

### 存储架构

支持多平台存储：

- 腾讯云 COS
- 阿里云 OSS
- MinIO

## 数据库

### 核心表

| 表名 | 用途 |
|------|------|
| t_user | 用户 |
| t_picture | 图片 |
| t_space | 空间 |
| t_space_user | 空间成员 |
| t_storage_config | 存储配置 |
| t_user_feedback | 用户反馈 |

### 数据规范

- 字段命名: **camelCase** (如 `userId`, `createTime`)
- 逻辑删除: `isDelete` 字段
- 图片主色调: `picColor` 存储十六进制值

## 配置说明

主要配置文件：

- `application.yml` - 主配置
- `application-local.yml` - 本地开发配置（不上版本库）
- `application-prod.yml` - 生产环境配置

## 接口文档

| 模块 | 路径 | 核心功能 |
|------|------|----------|
| 用户 | `/api/user/*` | 注册、登录、信息管理 |
| 图片 | `/api/picture/*` | 上传、编辑、审核、搜索 |
| 空间 | `/api/space/*` | 空间管理、成员管理 |
| 空间分析 | `/api/space_analyze/*` | 使用量、分类、标签分析 |
| 存储 | `/api/storage/*` | 存储配置管理 |
| 反馈 | `/api/feedback/*` | 反馈提交与管理 |

## License

MIT
