#!/usr/bin/env python3
"""部署模板与代码的一致性门禁。

实机验收暴露过一个典型缺陷：运行手册的 app.env 少写了模型端点键，
应用照手册装完能起来，但 Agent 请求打向 api.openai.com（大陆不可达），
故障点在首帧之后，只能等真实用户踩到。散文形式的清单挡不住这类漂移，
所以把「键名对照表」做成机器校验：

  * 生产守卫（ProductionSecretsGuard）要求的每个键，都必须出现在
    deploy/app.env.example 与 .env.example 里；
  * compose 必须把同一批键传给 backend；
  * 模板里的键必须全部登记在册（多一个少一个都红），杜绝「改了模板忘了改对照表」；
  * 生产 profile 的关键默认值必须落在 application-prod.yml 上：
    业务端口绑回环、模型端点与模型名有可达的默认值、SSE 异步超时有兜底。

纯文件检查，不需要数据库、容器或网络。退出码：0 通过 / 1 有发现 / 2 环境不可用。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

GUARD = ROOT / "src" / "main" / "java" / "com" / "yuwen" / "visionspace" / "config" / "ProductionSecretsGuard.java"
PROD_YML = ROOT / "src" / "main" / "resources" / "application-prod.yml"
COMPOSE = ROOT / "docker-compose.yml"
ENV_EXAMPLE = ROOT / ".env.example"
TEMPLATE = ROOT / "deploy" / "app.env.example"
DEPLOY_DIR = ROOT / "deploy"

# Spring 属性 -> 环境变量名。
# 属性名来自 application-prod.yml 的 ${VAR:} 占位（不是靠 relaxed binding 猜的），
# 所以这张表是「代码事实」的映射，改代码时必须同步改这里（门禁会红）。
ENV_FOR_PROPERTY = {
    "spring.datasource.url": "SPRING_DATASOURCE_URL",
    "spring.datasource.username": "SPRING_DATASOURCE_USERNAME",
    "spring.datasource.password": "SPRING_DATASOURCE_PASSWORD",
    "spring.data.redis.host": "SPRING_DATA_REDIS_HOST",
    "spring.data.redis.password": "SPRING_DATA_REDIS_PASSWORD",
    "spring.ai.dashscope.api-key": "SPRING_AI_DASHSCOPE_API_KEY",
    "spring.ai.openai.api-key": "SPRING_AI_OPENAI_API_KEY",
    "spring.ai.openai.base-url": "SPRING_AI_OPENAI_BASE_URL",
    "spring.ai.openai.chat.options.model": "SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL",
    "aliYunAi.apiKey": "ALI_YUN_AI_API_KEY",
    "vision-space.pexels.api-key": "VISION_SPACE_PEXELS_API_KEY",
    "vision-space.vision-recognition.api-key": "VISION_SPACE_VISION_RECOGNITION_API_KEY",
    "vision-space.security.default-password": "VISION_SPACE_DEFAULT_PASSWORD",
}

# 原生部署模板允许出现的全部键。新增键必须先登记在这里（= 承认它属于部署契约）。
TEMPLATE_KEYS = {
    "SPRING_PROFILES_ACTIVE",
    "SERVER_ADDRESS",
    "SPRING_DATASOURCE_URL",
    "SPRING_DATASOURCE_USERNAME",
    "SPRING_DATASOURCE_PASSWORD",
    "SPRING_DATA_REDIS_HOST",
    "SPRING_DATA_REDIS_PORT",
    "SPRING_DATA_REDIS_PASSWORD",
    "SPRING_AI_DASHSCOPE_API_KEY",
    "SPRING_AI_OPENAI_API_KEY",
    "SPRING_AI_OPENAI_BASE_URL",
    "SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL",
    "ALI_YUN_AI_API_KEY",
    "VISION_SPACE_PEXELS_API_KEY",
    "VISION_SPACE_VISION_RECOGNITION_API_KEY",
    "VISION_SPACE_VISION_RECOGNITION_BASE_URL",
    "VISION_SPACE_VISION_RECOGNITION_MODEL",
    "VISION_SPACE_DEFAULT_PASSWORD",
    "VISION_SPACE_TRUSTED_ORIGINS",
    "SPRING_MVC_ASYNC_REQUEST_TIMEOUT",
    "SPRING_AI_VECTORSTORE_TYPE",
    "SPRING_AI_MODEL_EMBEDDING_TEXT",
    "VISION_SPACE_WX_APPID",
    "VISION_SPACE_WX_SECRET",
    "TZ",
    "PGTZ",
    "LOGGING_FILE_NAME",
}

# application-prod.yml 里必须出现的默认配置：(说明, 必须出现的片段)
REQUIRED_PROD_YML = [
    ("业务端口默认绑回环", "address: ${SERVER_ADDRESS:127.0.0.1}"),
    ("模型端点有可达默认值", "base-url: ${SPRING_AI_OPENAI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}"),
    ("模型名有默认值", "model: ${SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL:qwen-plus}"),
    ("SSE 异步超时有兜底", "request-timeout: ${SPRING_MVC_ASYNC_REQUEST_TIMEOUT:300000}"),
]

REQUIRED_DEPLOY_FILES = [
    "README.md",
    "app.env.example",
    "visionspace.service",
    "nginx-visionspace.conf",
    "postgresql-production.conf",
    "init-production-db.sh",
    "flyway-migrate.sh",
    "postflight-check.sh",
]


def guard_required_keys():
    text = GUARD.read_text(encoding="utf-8")
    match = re.search(r"REQUIRED_KEYS\s*=\s*List\.of\((.*?)\);", text, re.S)
    if not match:
        raise RuntimeError(f"无法从 {GUARD} 解析 REQUIRED_KEYS")
    return re.findall(r'"([^"]+)"', match.group(1))


def env_file_keys(path):
    """读取 KEY=VALUE 形式的文件，返回 (键列表, 重复键)。"""
    keys = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        keys.append(stripped.split("=", 1)[0].strip())
    duplicates = sorted({k for k in keys if keys.count(k) > 1})
    return keys, duplicates


def findings():
    required = guard_required_keys()

    # 1. 守卫新增了键，映射表没跟上
    for prop in required:
        if prop not in ENV_FOR_PROPERTY:
            yield f"守卫新增必填项 {prop}，但 ENV_FOR_PROPERTY 没登记它"

    # 2. 守卫的每个键，模板与 .env.example 都要有
    template_keys, template_duplicates = env_file_keys(TEMPLATE)
    for prop in required:
        var = ENV_FOR_PROPERTY.get(prop)
        if var is None:
            continue
        if var not in template_keys:
            yield f"deploy/app.env.example 缺少生产必填键 {var}（{prop}）"
        if var not in ENV_EXAMPLE.read_text(encoding="utf-8"):
            yield f".env.example 缺少生产必填键 {var}（{prop}）"
        if var not in COMPOSE.read_text(encoding="utf-8"):
            yield f"docker-compose.yml 没有把 {var} 传给 backend"

    # 3. 模板键必须全部登记（差异要显式登记，不能悄悄多一个）
    for key in sorted(set(template_keys) - TEMPLATE_KEYS):
        yield f"deploy/app.env.example 出现未登记的键 {key}：要么登记进 TEMPLATE_KEYS，要么删掉"
    for key in sorted(TEMPLATE_KEYS - set(template_keys)):
        yield f"deploy/app.env.example 缺少登记过的键 {key}"
    for key in template_duplicates:
        yield f"deploy/app.env.example 出现重复键 {key}"

    # 4. 生产 profile 的关键默认值
    prod_text = PROD_YML.read_text(encoding="utf-8")
    for label, fragment in REQUIRED_PROD_YML:
        if fragment not in prod_text:
            yield f"application-prod.yml 缺少{label}：{fragment}"

    # 5. compose 必须显式覆盖监听面：容器内要 0.0.0.0，宿主机只发布到回环
    compose_text = COMPOSE.read_text(encoding="utf-8")
    if "SERVER_ADDRESS: 0.0.0.0" not in compose_text:
        yield "docker-compose.yml 没有显式设置 SERVER_ADDRESS: 0.0.0.0（容器内要监听所有接口才能被端口映射）"
    if not re.search(r'"127\.0\.0\.1:\$\{BACKEND_PORT[^"]*\}:8081"', compose_text):
        yield "docker-compose.yml 的 backend 端口没有只发布到 127.0.0.1"

    # 6. 部署目录的文件齐全
    for name in REQUIRED_DEPLOY_FILES:
        if not (DEPLOY_DIR / name).is_file():
            yield f"deploy/ 缺少 {name}"

    # 7. 模板里不许出现看起来像真实凭据的值
    for line_no, line in enumerate(TEMPLATE.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        value = stripped.split("=", 1)[1].strip()
        if re.search(r"(?i)\bsk-[A-Za-z0-9]{16,}", value) or re.search(r"(?i)LTAI[0-9A-Za-z]{12,}", value):
            yield f"deploy/app.env.example:{line_no} 看起来像真实凭据，模板里只能放占位说明"


def main():
    try:
        problems = list(findings())
    except (OSError, RuntimeError) as exc:
        print(f"BLOCKED_ENV: 无法检查部署模板：{exc}", file=sys.stderr)
        return 2
    if problems:
        print(f"FAIL: 部署模板有 {len(problems)} 处与代码不一致：")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("PASS: 部署模板与守卫、compose、生产默认值一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
