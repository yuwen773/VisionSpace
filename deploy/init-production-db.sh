#!/usr/bin/env bash
# 独立空库初始化：建运行/迁移角色、建目标库、装扩展、写最小权限授权。
#
# 边界（重要）：
#   * 本脚本**只新建**目标库，绝不 drop、truncate 或改动任何既有库；
#   * 目标库已存在时直接拒绝退出（避免把「重复执行」变成「覆盖」）；
#   * 角色已存在时不改它的口令（口令由部署者自己掌握，脚本不碰）；
#   * 业务表不在这里建：建表只有 Flyway 一个入口（deploy/flyway-migrate.sh）。
#
# 用法（服务器上，peer 认证，不需要口令）：
#   sudo VS_DB_NAME=vision_space_prod ./deploy/init-production-db.sh
#
# 用法（容器/远端，走 TCP，口令只经环境变量，不进 argv）：
#   VS_PG_SUPERUSER_PASSWORD=... VS_PG_HOST=127.0.0.1 VS_PG_PORT=5432 \
#     VS_DB_NAME=vision_space_prod ./deploy/init-production-db.sh
#
# 可选：VS_MIGRATOR_PASSWORD / VS_APP_PASSWORD 只在**角色还不存在**时用来设置它们的初始口令。
set -euo pipefail

VS_DB_NAME="${VS_DB_NAME:-vision_space_prod}"
VS_DB_TIMEZONE="${VS_DB_TIMEZONE:-Asia/Shanghai}"
VS_DB_COMMENT="${VS_DB_COMMENT:-vision-space-production}"
VS_PG_HOST="${VS_PG_HOST:-127.0.0.1}"
VS_PG_PORT="${VS_PG_PORT:-5432}"
VS_MIGRATOR_ROLE="vision_migrator"
VS_APP_ROLE="vision_app"
VS_PG_SUPERUSER_PASSWORD="${VS_PG_SUPERUSER_PASSWORD:-}"

# 库名/角色名会进 SQL 文本，先卡死字符集，避免任何拼接注入
if ! printf '%s' "$VS_DB_NAME" | grep -Eq '^[a-z_][a-z0-9_]{0,62}$'; then
  echo "FAIL: 库名只允许小写字母、数字与下划线，且以字母或下划线开头：$VS_DB_NAME" >&2
  exit 2
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "FAIL: 找不到 psql 客户端" >&2
  exit 2
fi

# 超级用户连接两条路：
#   有口令 → TCP + PGPASSWORD（口令在子进程环境里，不在 argv、不在日志）；
#   无口令 → 本机 peer 认证（服务器上 `sudo -u postgres psql`）。
psql_super() {
  if [ -n "$VS_PG_SUPERUSER_PASSWORD" ]; then
    PGPASSWORD="$VS_PG_SUPERUSER_PASSWORD" psql --no-psqlrc -v ON_ERROR_STOP=1 \
      --host "$VS_PG_HOST" --port "$VS_PG_PORT" --username postgres "$@"
  else
    sudo -u postgres psql --no-psqlrc -v ON_ERROR_STOP=1 "$@"
  fi
}

scalar() {
  psql_super --tuples-only --no-align --command "$1"
}

echo "== 目标库 =="
echo "database=$VS_DB_NAME host=$VS_PG_PORT timezone=$VS_DB_TIMEZONE"

existing=$(scalar "SELECT 1 FROM pg_database WHERE datname = '$VS_DB_NAME'")
if [ "$existing" = "1" ]; then
  echo "FAIL: 库 $VS_DB_NAME 已存在。本脚本只新建库，不覆盖既有库；" \
       "如需在既有库上迁移，请直接跑 deploy/flyway-migrate.sh。" >&2
  exit 1
fi

echo "== 1. 角色 =="
# 口令只经 stdin 的 SQL 文本传给 psql：`--set=secret=...` 会出现在进程列表里，不能用。
create_role() {
  local role="$1" secret="$2" inherit="$3"
  local escaped
  escaped=$(printf '%s' "$secret" | sed "s/'/''/g")
  psql_super --dbname postgres <<SQL
CREATE ROLE "$role" LOGIN PASSWORD '$escaped' NOSUPERUSER NOCREATEDB NOCREATEROLE $inherit;
SQL
}

for role in "$VS_MIGRATOR_ROLE" "$VS_APP_ROLE"; do
  if [ "$(scalar "SELECT 1 FROM pg_roles WHERE rolname = '$role'")" = "1" ]; then
    echo "  $role 已存在：保留现有口令，不做修改"
    continue
  fi
  case "$role" in
    "$VS_MIGRATOR_ROLE") secret="${VS_MIGRATOR_PASSWORD:-}"; inherit="" ;;
    *) secret="${VS_APP_PASSWORD:-}"; inherit="NOINHERIT" ;;
  esac
  if [ -z "$secret" ]; then
    echo "FAIL: 角色 $role 不存在，且没有给出初始口令（VS_MIGRATOR_PASSWORD / VS_APP_PASSWORD）。" \
         "角色口令不要写进命令行，用环境变量传。" >&2
    exit 2
  fi
  create_role "$role" "$secret" "$inherit"
  echo "  $role 已创建"
done

echo "== 2. 建库 =="
psql_super --command "CREATE DATABASE \"$VS_DB_NAME\" OWNER \"$VS_MIGRATOR_ROLE\""
echo "  $VS_DB_NAME 已创建（owner=$VS_MIGRATOR_ROLE）"

echo "== 3. 库级设置与授权 =="
psql_super --dbname "$VS_DB_NAME" --command \
  "REVOKE CREATE ON SCHEMA public FROM PUBLIC" \
  --command "GRANT USAGE ON SCHEMA public TO \"$VS_APP_ROLE\"" \
  --command "CREATE EXTENSION IF NOT EXISTS pgcrypto" \
  --command "ALTER DATABASE \"$VS_DB_NAME\" SET timezone = '$VS_DB_TIMEZONE'" \
  --command "COMMENT ON DATABASE \"$VS_DB_NAME\" IS '$VS_DB_COMMENT'"

echo "== 4. 结果 =="
psql_super --dbname "$VS_DB_NAME" --command \
  "SELECT d.datname, pg_get_userbyid(d.datdba) AS owner, shobj_description(d.oid, 'pg_database') AS comment
   FROM pg_database d WHERE d.datname = '$VS_DB_NAME'" \
  --command "SELECT rolname, rolsuper, rolcreatedb, rolinherit FROM pg_roles WHERE rolname IN ('$VS_MIGRATOR_ROLE', '$VS_APP_ROLE')"

cat <<EOF

下一步：
  1) 建表（唯一入口是 Flyway）：
       VS_DB_NAME=$VS_DB_NAME FLYWAY_PASSWORD=<迁移角色口令> ./deploy/flyway-migrate.sh
  2) 切换应用连接串：app.env 的 SPRING_DATASOURCE_URL 指向 $VS_DB_NAME，然后重启并检查 readiness
  3) 部署后自检：./deploy/postflight-check.sh
EOF
