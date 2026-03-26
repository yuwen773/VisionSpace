# VisionSpace

云端智能图片管理与协作平台，提供图片上传、管理、分享等功能，支持多主题切换和颜色搜索。

## 技术栈

### 后端

- **框架**: Spring Boot 2.7.6
- **Java**: 1.8
- **数据库**: MyBatis Plus + 分库分表
- **缓存**: Redis + Spring Session
- **认证**: Sa-Token
- **图片存储**: 腾讯云 COS
- **实时协作**: WebSocket + Disruptor

### 前端

- **框架**: Vue 3 + TypeScript
- **构建工具**: Vite 6
- **UI 组件**: Ant Design Vue 4
- **状态管理**: Pinia
- **图表**: ECharts

## 功能特性

- **用户管理**: 注册、登录、VIP 兑换
- **空间管理**: 团队空间、权限控制
- **图片管理**: 上传、编辑、批量操作
- **颜色搜索**: K-Means 提取主色调，按颜色检索图片
- **主题切换**: 深度空间暗色 / 波普艺术
- **数据分析**: 空间使用统计、分类分析
- **AI 扩图**: 阿里云 AI 能力集成
- **实时协作**: WebSocket 图片编辑同步

## 项目结构

```
src/main/java/com/yuwen/visionspace/
├── api/                    # 外部 API 封装
│   ├── aliyunai/          # 阿里云 AI
│   └── imagesearch/       # 图片搜索
├── annotation/             # 自定义注解
├── aop/                   # AOP 切面
├── common/                # 通用响应
├── config/                # 配置类
├── constant/              # 常量
├── controller/            # 控制器
├── exception/             # 异常处理
├── manager/               # 管理层
│   ├── auth/             # 权限管理
│   ├── upload/            # 图片上传
│   ├── websocket/         # 实时协作
│   └── storage/           # 存储服务
├── mapper/                # MyBatis Mapper
├── model/                 # 数据模型
│   ├── dto/              # 数据传输对象
│   ├── entity/           # 实体类
│   ├── enums/            # 枚举
│   └── vo/               # 视图对象
├── service/              # 服务层
└── utils/                # 工具类
```

## 快速开始

### 前置条件

- JDK 1.8+
- Node.js 18+
- Maven 3.6+
- MySQL 8.0+
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

### 权限控制

基于 Sa-Token 的多级权限体系：

- 系统管理员
- 空间管理员
- 普通用户

## 配置说明

主要配置文件：

- `application.yml` - 主配置
- `application-local.yml` - 本地开发配置（不上版本库）
- `application-prod.yml` - 生产环境配置

## 接口文档

| 模块 | 说明 |
|------|------|
| `/api/user/*` | 用户相关 |
| `/api/picture/*` | 图片管理 |
| `/api/space/*` | 空间管理 |
| `/api/file/*` | 文件上传 |

## License

MIT
