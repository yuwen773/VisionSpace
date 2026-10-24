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

# 运行身份：非 root 专用 UID/GID。镜像内可写范围只有应用临时目录。
RUN addgroup -g 10001 app && adduser -u 10001 -G app -D -H -s /sbin/nologin app \
    && mkdir -p /app/tmp \
    && chown -R app:app /app/tmp \
    && chmod 750 /app/tmp \
    && chown root:root /app /app/app.jar \
    && chmod 750 /app /app/app.jar

USER 10001:10001

ENV TMPDIR=/app/tmp

# 只声明业务端口；管理端口 9081 绑定 127.0.0.1，不对外
EXPOSE 8081

# 探活走 readiness：包含 db/redis 组件，依赖故障时报告 503
HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:9081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
