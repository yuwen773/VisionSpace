# Spring Boot 3.5.8 + Java 21 升级实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将项目从 Spring Boot 2.7.6 + Java 8 升级到 Spring Boot 3.5.8 + Java 21，并引入 Spring AI + Spring AI Alibaba，使用虚拟线程。

**Architecture:** 在隔离的 worktree 分支中完成所有升级，然后合并回 main。核心变更包括 javax→jakarta 命名空间迁移、依赖版本升级、以及新增 Spring AI 集成。

**Tech Stack:** Spring Boot 3.5.8, Java 21, Spring AI 1.1.2, Spring AI Alibaba 1.1.2.2, MyBatis Plus 3.5.16, ShardingSphere 5.4.0

---

## 前置准备

### Task 0: 创建 Worktree 分支

**Files:**
- 创建: `.worktrees/spring-boot-3-ai/` (新 worktree)
- 分支: `feature/spring-boot-3-ai`

**Step 1: 创建 worktree 并切换**

Run: `git worktree add .worktrees/spring-boot-3-ai -b feature/spring-boot-3-ai`
Expected: Creating worktree output with new branch

**Step 2: 验证 worktree**

Run: `git worktree list`
Expected: 包含新的 spring-boot-3-ai worktree

---

## 第一阶段：基础环境升级

### Task 1: 更新 pom.xml 版本配置

**Files:**
- Modify: `pom.xml:10-16`

**Step 1: 修改 Java 和 Spring Boot 版本**

```xml
<properties>
    <java.version>21</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <spring-boot.version>3.5.8</spring-boot.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <spring-ai-alibaba.version>1.1.2.2</spring-ai-alibaba.version>
    <lombok.version>1.18.30</lombok.version>
</properties>
```

**Step 2: 修改 maven-compiler-plugin**

```xml
<configuration>
    <source>21</source>
    <target>21</target>
    <encoding>UTF-8</encoding>
    <annotationProcessorPaths>
        <path>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
        </path>
    </annotationProcessorPaths>
</configuration>
```

**Step 3: 添加 Spring AI BOM**

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring AI BOM -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- 其他现有 BOM... -->
    </dependencies>
</dependencyManagement>
```

**Step 4: 添加 Spring AI 依赖**

```xml
<!-- Spring AI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter</artifactId>
</dependency>

<!-- Spring AI Alibaba -->
<dependency>
    <groupId>com.alibaba.cloudai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
    <version>${spring-ai-alibaba.version}</version>
</dependency>
```

**Step 5: 升级 MyBatis Plus 版本**

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.16</version>
</dependency>
```

**Step 6: 升级 ShardingSphere 版本**

```xml
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc-core-spring-boot-starter</artifactId>
    <version>5.4.0</version>
</dependency>
```

**Step 7: 升级 Knife4j 版本**

```xml
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi2-spring-boot-starter</artifactId>
    <version>4.5.0</version>
</dependency>
```

**Step 8: 编译验证**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

---

## 第二阶段：javax → jakarta 迁移

### Task 2: 迁移 AuthInterceptor.java

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/aop/AuthInterceptor.java`

**Step 1: 替换 import 语句**

```java
// 删除
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

// 添加
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
```

---

### Task 3: 迁移 RequestWrapper.java

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/config/RequestWrapper.java`

**Step 1: 替换 import 语句**

```java
// 删除
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

// 添加
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
```

---

### Task 4: 迁移 HttpRequestWrapperFilter.java

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/config/HttpRequestWrapperFilter.java`

**Step 1: 替换 import 语句**

```java
// 删除
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

// 添加
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
```

---

### Task 5: 迁移 Controller 层

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/controller/FileController.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/AdminStatsController.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/PictureRecommendController.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/PictureActionController.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/UserController.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/SpaceController.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/SpaceUserController.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/SpaceAnalyzeController.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/StorageConfigController.java`
- Modify: `src/main/java/com/yuwen/visionspace/controller/PictureController.java`

**对每个文件执行相同替换:**

```java
// 删除 javax.annotation.Resource, javax.servlet.http.*
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// 添加 jakarta 对应
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
```

---

### Task 6: 迁移 Service/Manager 层

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/AdminStatsServiceImpl.java`
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureRecommendServiceImpl.java`
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/SpaceAnalyzeServiceImpl.java`
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java`
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureActionServiceImpl.java`
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureStatsAggregateServiceImpl.java`
- Modify: `src/main/java/com/yuwen/visionspace/manager/cache/RecommendCacheManager.java`
- Modify: `src/main/java/com/yuwen/visionspace/manager/job/RecommendCacheRefreshJob.java`
- Modify: `src/main/java/com/yuwen/visionspace/manager/job/PictureStatsAggregateJob.java`
- Modify: `src/main/java/com/yuwen/visionspace/manager/FileManager.java`

**对每个文件执行相同替换:**

```java
// 删除
import javax.annotation.Resource;

// 添加
import jakarta.annotation.Resource;
```

---

### Task 7: 迁移其他文件

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/manager/upload/PictureUploadTemplate.java`
- Modify: `src/main/java/com/yuwen/visionspace/manager/storage/PictureStorageService.java`
- Modify: `src/main/java/com/yuwen/visionspace/manager/upload/PictureUploadTemplate.java`

**Step 1: 替换 import**

```java
// 删除
import javax.annotation.Resource;

// 添加
import jakarta.annotation.Resource;
```

---

### Task 8: 编译验证

**Step 1: 运行编译**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

---

## 第三阶段：虚拟线程配置

### Task 9: 配置虚拟线程

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: 添加虚拟线程配置**

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Step 2: 验证配置**

Run: `mvn compile -DskipTests`
Expected: BUILD SUCCESS

---

## 第四阶段：Spring AI 集成

### Task 10: 配置 Spring AI

**Files:**
- Modify: `src/main/resources/application.yml`

**Step 1: 添加 Spring AI 配置**

```yaml
spring:
  ai:
    dashscope:
      api-key: ${AI_API_KEY:your-api-key}
    Alibaba:
      cloud:
        api-key: ${ALIYUN_AI_API_KEY:your-api-key}
```

---

### Task 11: 创建 AI Service 占位

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/service/AIService.java`

**Step 1: 创建基础 AI Service**

```java
package com.yuwen.visionspace.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

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

---

## 第五阶段：测试验证

### Task 12: 运行单元测试

**Step 1: 运行所有测试**

Run: `mvn test`
Expected: 测试结果

---

### Task 13: 手动功能验证

**验证清单:**
- [ ] 应用启动成功
- [ ] 图片上传功能正常
- [ ] 推荐系统正常
- [ ] 统计功能正常
- [ ] 管理后台正常

---

## 第六阶段：提交与合并

### Task 14: 提交所有更改

**Step 1: 查看更改状态**

Run: `git status`

**Step 2: 提交更改**

Run: `git add -A && git commit -m "feat: upgrade to Spring Boot 3.5.8 + Java 21

- Upgrade Java 8 to Java 21
- Upgrade Spring Boot 2.7.6 to 3.5.8
- Add Spring AI 1.1.2 and Spring AI Alibaba 1.1.2.2
- Enable virtual threads
- Migrate javax to jakarta namespace
- Upgrade MyBatis Plus to 3.5.16
- Upgrade ShardingSphere to 5.4.0
- Upgrade Knife4j to 4.5.0"`

---

### Task 15: 合并回 main

**Step 1: 切换到 main**

Run: `git checkout main`

**Step 2: 合并分支**

Run: `git merge feature/spring-boot-3-ai`
Expected: Merge success

---

## 风险与注意事项

| 风险 | 缓解措施 |
|------|---------|
| 第三方库不兼容 | 提前验证版本兼容性，查看官方兼容列表 |
| javax→jakarta 遗漏 | 使用 IDE 全局搜索确认无遗漏 |
| 虚拟线程问题 | 默认启用，可按需禁用 |
| Spring AI 配置 | 使用 application.yml 配置，支持环境变量 |
