#!/usr/bin/env bash
# 部署后自检：监听面、readiness、运行角色最小权限、Flyway 状态、业务表数量。
#
# 每项独立判定，失败项汇总成非零退出码；不修改任何状态。
# 需要 root（或能读 /opt/visionspace/app.env 与 /opt/visionspace/flyway.conf 的身份）。
set -uo pipefail

VS_APP_ENV="${VS_APP_ENV:-/opt/visionspace/app.env}"
VS_FLYWAY_HOME="${VS_FLYWAY_HOME:-/opt/visionspace/flyway}"
VS_FLYWAY_CONF="${VS_FLYWAY_CONF:-/opt/visionspace/flyway.conf}"
VS_DB_NAME="${VS_DB_NAME:-vision_space_prod}"
VS_APP_ROLE="${VS_APP_ROLE:-vision_app}"
VS_BUSINESS_PORT="${VS_BUSINESS_PORT:-8081}"
VS_MANAGEMENT_PORT="${VS_MANAGEMENT_PORT:-9081}"

FAILED=0

pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1"; FAILED=1; }

# 从 app.env 取运行角色口令：口令只进环境变量，不进 argv、不回显
if [ -r "$VS_APP_ENV" ]; then
  APP_PW=$(grep -E '^SPRING_DATASOURCE_PASSWORD=' "$VS_APP_ENV" | head -1 | cut -d= -f2-)
else
  APP_PW=""
fi

echo "== 1. 监听面 =="
if command -v ss >/dev/null 2>&1; then
  LISTEN=$(ss -lntp 2>/dev/null | grep -E "[:.]$VS_BUSINESS_PORT[[:space:]]" || true)
  if [ -z "$LISTEN" ]; then
    fail "业务端口 $VS_BUSINESS_PORT 没有监听"
  elif printf '%s' "$LISTEN" | grep -qE '127\.0\.0\.1:|\[::1\]:'; then
    pass "业务端口 $VS_BUSINESS_PORT 只绑回环"
  else
    fail "业务端口 $VS_BUSINESS_PORT 绑在非回环地址：$LISTEN"
  fi
  MGMT=$(ss -lntp 2>/dev/null | grep -E "[:.]$VS_MANAGEMENT_PORT[[:space:]]" || true)
  if [ -z "$MGMT" ]; then
    fail "管理端口 $VS_MANAGEMENT_PORT 没有监听"
  elif printf '%s' "$MGMT" | grep -qE '127\.0\.0\.1:|\[::1\]:'; then
    pass "管理端口 $VS_MANAGEMENT_PORT 只绑回环"
  else
    fail "管理端口 $VS_MANAGEMENT_PORT 绑在非回环地址：$MGMT"
  fi
else
  fail "没有 ss 命令，无法核对监听面"
fi

echo "== 2. readiness =="
READY=$(curl -sf -m 5 "http://127.0.0.1:${VS_MANAGEMENT_PORT}/actuator/health/readiness" || true)
if printf '%s' "$READY" | grep -q '"status":"UP"'; then
  pass "readiness UP"
else
  fail "readiness 不是 UP：$READY"
fi

echo "== 3. 运行角色与最小权限 =="
if [ -z "$APP_PW" ]; then
  fail "读不到运行角色口令（$VS_APP_ENV）"
elif ! command -v psql >/dev/null 2>&1; then
  fail "没有 psql 客户端，跳过数据库检查"
else
  WHOAMI_OUT=$(PGPASSWORD="$APP_PW" psql -h 127.0.0.1 -U "$VS_APP_ROLE" -d "$VS_DB_NAME" -tAc 'SELECT current_user' 2>&1)
  if [ "$WHOAMI_OUT" = "$VS_APP_ROLE" ]; then
    pass "运行身份是 $VS_APP_ROLE"
  else
    fail "运行身份异常：$WHOAMI_OUT"
  fi
  # DDL 负例：运行角色必须没有 CREATE 权限
  DDL_OUT=$(PGPASSWORD="$APP_PW" psql -h 127.0.0.1 -U "$VS_APP_ROLE" -d "$VS_DB_NAME" \
    -c 'CREATE TABLE vs_postflight_probe(id int)' 2>&1)
  if printf '%s' "$DDL_OUT" | grep -qiE 'permission denied|not the owner|42501'; then
    pass "运行角色执行 DDL 被拒绝"
  else
    fail "运行角色居然能建表：$DDL_OUT"
    PGPASSWORD="$APP_PW" psql -h 127.0.0.1 -U "$VS_APP_ROLE" -d "$VS_DB_NAME" \
      -c 'DROP TABLE IF EXISTS vs_postflight_probe' >/dev/null 2>&1 || true
  fi
  TABLES=$(PGPASSWORD="$APP_PW" psql -h 127.0.0.1 -U "$VS_APP_ROLE" -d "$VS_DB_NAME" -tAc \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'" 2>&1)
  if [ "$TABLES" = "31" ]; then
    pass "业务表 31 张"
  else
    fail "业务表数量异常：$TABLES（期望 31）"
  fi
fi

echo "== 4. Flyway 状态 =="
if [ -z "${FLYWAY_PASSWORD:-}" ]; then
  fail "没有 FLYWAY_PASSWORD，跳过 Flyway 状态检查（口令只经环境变量传）"
elif [ ! -x "$VS_FLYWAY_HOME/flyway" ]; then
  fail "找不到 Flyway CLI：$VS_FLYWAY_HOME/flyway"
else
  INFO=$("$VS_FLYWAY_HOME/flyway" -configFiles="$VS_FLYWAY_CONF" info 2>&1 || true)
  if printf '%s' "$INFO" | grep -q 'Success'; then
    pass "Flyway 迁移状态为 Success"
  else
    fail "Flyway 状态异常：$INFO"
  fi
fi

echo
if [ "$FAILED" = "0" ]; then
  echo "全部检查通过"
  exit 0
fi
echo "存在失败项（见上）"
exit 1
