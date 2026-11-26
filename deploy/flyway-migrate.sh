#!/usr/bin/env bash
# 对目标库执行 Flyway migrate + validate，并打印迁移状态。
#
# 建表只有这一个入口（ADR-0004 §1）：应用启动不做任何 DDL（spring.flyway.enabled=false）。
# 口令只经环境变量 FLYWAY_PASSWORD，不进 argv、不进日志、不进配置文件。
#
# 用法：
#   VS_DB_NAME=vision_space_prod FLYWAY_PASSWORD=<迁移角色口令> ./deploy/flyway-migrate.sh
#
# 依赖：Flyway CLI 13.13.0 解压在 VS_FLYWAY_HOME（默认 /opt/visionspace/flyway），
#       配置在 VS_FLYWAY_CONF（默认 /opt/visionspace/flyway.conf，权限 600）。
set -euo pipefail

VS_FLYWAY_HOME="${VS_FLYWAY_HOME:-/opt/visionspace/flyway}"
VS_FLYWAY_CONF="${VS_FLYWAY_CONF:-/opt/visionspace/flyway.conf}"
VS_DB_NAME="${VS_DB_NAME:-vision_space_prod}"
VS_PG_HOST="${VS_PG_HOST:-127.0.0.1}"
VS_PG_PORT="${VS_PG_PORT:-5432}"
VS_FLYWAY_USER="${VS_FLYWAY_USER:-vision_migrator}"

if [ -z "${FLYWAY_PASSWORD:-}" ]; then
  echo "FAIL: 必须通过环境变量 FLYWAY_PASSWORD 提供迁移角色口令（不进 argv、不进日志）" >&2
  exit 2
fi

flyway_bin="$VS_FLYWAY_HOME/flyway"
if [ ! -x "$flyway_bin" ]; then
  echo "FAIL: 找不到 Flyway CLI：$flyway_bin" >&2
  exit 2
fi

url="jdbc:postgresql://${VS_PG_HOST}:${VS_PG_PORT}/${VS_DB_NAME}"

echo "== migrate =="
"$flyway_bin" -configFiles="$VS_FLYWAY_CONF" -url="$url" -user="$VS_FLYWAY_USER" migrate

echo "== validate =="
"$flyway_bin" -configFiles="$VS_FLYWAY_CONF" -url="$url" -user="$VS_FLYWAY_USER" validate

echo "== info =="
"$flyway_bin" -configFiles="$VS_FLYWAY_CONF" -url="$url" -user="$VS_FLYWAY_USER" info

echo "OK: $VS_DB_NAME 迁移完成并通过校验"
