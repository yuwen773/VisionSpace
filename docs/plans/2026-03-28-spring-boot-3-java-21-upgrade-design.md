# Spring Boot 3.5.8 + Java 21 升级设计文档

## 1. 概述

**目标：** 将项目从 Spring Boot 2.7.6 + Java 8 升级到 Spring Boot 3.5.8 + Java 21，并引入 Spring AI + Spring AI Alibaba，使用虚拟线程。

**分支策略：** 使用 worktree 创建隔离分支 `feature/spring-boot-3-ai`

## 2. 当前状态

| 组件 | 当前版本 |
|------|---------|
| Java | 1.8 |
| Spring Boot | 2.7.6 |
| MyBatis Plus | 3.5.9 |
| Sa-Token | 1.39.0 |
| ShardingSphere | 5.2.0 |
| Knife4j | 4.4.0 |

## 3. 目标状态

| 组件 | 目标版本 |
|------|---------|
| Java | 21 |
| Spring Boot | 3.5.8 |
| Spring AI | 1.1.2 |
| Spring AI Alibaba | 1.1.2.2 |
| MyBatis Plus | 3.5.16+ |
| Sa-Token | 1.39.0+ (确认兼容) |
| ShardingSphere | 5.4.0+ |
| Knife4j | 4.5.0+ |

## 4. 核心变更

### 4.1 javax → jakarta 命名空间迁移

Spring Boot 3.x 基于 Jakarta EE 9+，需要将 `javax.*` 迁移到 `jakarta.*`。

**受影响文件（20+）：**

| 文件 | 变更 |
|------|------|
| `AuthInterceptor.java` | `javax.annotation.Resource` → `jakarta.annotation.Resource` |
| `AuthInterceptor.java` | `javax.servlet.http.HttpServletRequest` → `jakarta.servlet.http.HttpServletRequest` |
| `FileController.java` | `javax.annotation.Resource`, `javax.servlet.http` |
| `PictureController.java` | `javax.annotation.Resource`, `javax.servlet.http` |
| `UserController.java` | `javax.annotation.Resource`, `javax.servlet.http` |
| `SpaceController.java` | `javax.annotation.Resource`, `javax.servlet.http` |
| `RequestWrapper.java` | `javax.servlet.*` → `jakarta.servlet.*` |
| `HttpRequestWrapperFilter.java` | `javax.servlet.*` → `jakarta.servlet.*` |
| 其他 Controller 和 Manager | 同上 |

**注意：** `javax.imageio.ImageIO` 保持不变（JDK 内部 API）

### 4.2 依赖版本升级

#### pom.xml 变更

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.5.8</spring-boot.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <spring-ai-alibaba.version>1.1.2.2</spring-ai-alibaba.version>
    <lombok.version>1.18.30</lombok.version>
</properties>
```

#### 新增依赖

```xml
<!-- Spring AI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter</artifactId>
    <version>${spring-ai.version}</version>
</dependency>

<!-- Spring AI Alibaba -->
<dependency>
    <groupId>com.alibaba.cloudai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
    <version>${spring-ai-alibaba.version}</version>
</dependency>
```

#### 升级依赖版本

| 依赖 | 当前 | 建议 |
|------|-----|------|
| mybatis-plus-boot-starter | 3.5.9 | 3.5.16 |
| shardingsphere-jdbc-core | 5.2.0 | 5.4.0 |
| knife4j-openapi2-spring-boot-starter | 4.4.0 | 4.5.0 |

### 4.3 虚拟线程配置

Spring Boot 3.5 支持虚拟线程，可通过配置启用：

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

或按需使用：

```java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

### 4.4 Spring AI 集成

#### 配置 AI 模型（application.yml）

```yaml
spring:
  ai:
    dashscope:
      api-key: ${AI_API_KEY}
    Alibaba:
      cloud:
        api-key: ${ALIYUN_AI_API_KEY}
```

#### 创建 AI Service

```java
@Service
public class AIService {
    private final ChatModel chatModel;

    public AIService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String chat(String message) {
        return chatModel.call(message);
    }
}
```

## 5. 实施步骤

1. 创建 worktree 分支 `feature/spring-boot-3-ai`
2. 更新 pom.xml 版本配置
3. 执行 javax → jakarta 全局替换
4. 升级各依赖版本
5. 添加 Spring AI 依赖和配置
6. 配置虚拟线程
7. 运行测试验证
8. 手动测试核心功能

## 6. 测试策略

- 运行现有测试：`mvn test`
- 手动验证核心功能（图片上传、推荐、统计）
- Spring AI 功能单独测试

## 7. 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| 第三方库不兼容 | 提前验证版本兼容性 |
| javax→jakarta 遗漏 | 全局搜索 + IDE 重构工具 |
| 虚拟线程性能问题 | 按需启用，不强制全局 |
| Spring AI Alibaba 版本 | 使用指定版本 1.1.2.2 |
