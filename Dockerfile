# 构建阶段
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# 先复制 pom.xml，再下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN mvn package -DskipTests -B

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 从构建阶段复制 jar 包
COPY --from=builder /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
