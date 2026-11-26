#!/usr/bin/env bash
# 数据库备份：pg_dump -Fc 自定义格式，落到备份目录，权限 600。
#
# 边界：
#   * 只读：只做 pg_dump，不删库、不改表、不动数据；
#   * 口令只经环境变量 PGPASSWORD，不进 argv、不进日志；
#   * 备份默认长期保留，清理由人工按保留策略单独决定
#     （验收库的备份属于验收期证据，任何情况下都不由脚本自动删除）。
#
# 用法：
#   VS_DB_NAME=vision_space_prod PGPASSWORD=<运行角色口令> ./deploy/backup-db.sh
#
# 可选环境变量：
#   VS_BACKUP_DIR     备份目录（默认 /opt/visionspace/backup）
#   VS_PG_HOST        数据库地址（默认 127.0.0.1）
#   VS_PG_PORT        数据库端口（默认 5432）
#   VS_APP_ROLE       连接角色（默认 vision_app）
#   VS_BACKUP_PREFIX  文件名前缀（默认取库名）
set -euo pipefail

VS_DB_NAME="${VS_DB_NAME:-vision_space_prod}"
VS_BACKUP_DIR="${VS_BACKUP_DIR:-/opt/visionspace/backup}"
VS_PG_HOST="${VS_PG_HOST:-127.0.0.1}"
VS_PG_PORT="${VS_PG_PORT:-5432}"
VS_APP_ROLE="${VS_APP_ROLE:-vision_app}"
VS_BACKUP_PREFIX="${VS_BACKUP_PREFIX:-$VS_DB_NAME}"

if ! printf '%s' "$VS_DB_NAME" | grep -Eq '^[a-z_][a-z0-9_]{0,62}$'; then
  echo "FAIL: 库名只允许小写字母、数字与下划线：$VS_DB_NAME" >&2
  exit 2
fi

if ! command -v pg_dump >/dev/null 2>&1; then
  echo "FAIL: 找不到 pg_dump 客户端" >&2
  exit 2
fi

if [ -z "${PGPASSWORD:-}" ]; then
  echo "FAIL: 必须通过环境变量 PGPASSWORD 提供连接口令（不进 argv、不进日志）" >&2
  exit 2
fi

mkdir -p "$VS_BACKUP_DIR"
chmod 700 "$VS_BACKUP_DIR" 2>/dev/null || true

TS=$(date +%Y%m%d-%H%M%S)
OUT="$VS_BACKUP_DIR/${VS_BACKUP_PREFIX}-${TS}.dump"

# umask 077：文件一落盘就是 600，不依赖事后再 chmod 的那一瞬间
(
  umask 077
  pg_dump -h "$VS_PG_HOST" -p "$VS_PG_PORT" -U "$VS_APP_ROLE" \
    --format=custom --file="$OUT" "$VS_DB_NAME"
)
chmod 600 "$OUT"

# pg_dump 成功退出不代表 dump 文件一定完整，再验一次目录项
if [ ! -s "$OUT" ]; then
  echo "FAIL: 备份文件为空：$OUT" >&2
  exit 1
fi

SIZE=$(du -h "$OUT" | cut -f1)
echo "OK: 备份完成 $OUT（$SIZE）"
echo "提醒：这份备份默认长期保留，清理是单独的人工决定。"
