#!/bin/bash
# 首次初始化时创建应用专用账号与探活账号（只在空数据目录执行一次）。
# MySQL 官方镜像不会对 .sql 文件做变量替换，因此用 .sh 经 docker_process_sql 执行，
# 口令全部来自容器环境变量，不落盘、不进日志。
# 本脚本由官方 entrypoint source 进同一 shell。不能开启 nounset（set -u）：
# entrypoint 内部的 _mysql_passfile 会无参调用并读取 $1，一旦 nounset 生效，
# 账号创建就会以 "$1: unbound variable" 失败（容器验收实测）。
set -eo pipefail

: "${MYSQL_APP_PASSWORD:?MYSQL_APP_PASSWORD is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"

# 口令嵌入 SQL 字符串前做转义：反斜杠与单引号都会改变语句含义或实际保存值
sql_quote() {
    local s=${1//\\/\\\\}
    s=${s//\'/\'\'}
    printf "'%s'" "$s"
}

APP_PASSWORD_SQL=$(sql_quote "$MYSQL_APP_PASSWORD")

docker_process_sql --database="$MYSQL_DATABASE" <<-EOSQL
CREATE USER IF NOT EXISTS 'vision_app'@'%' IDENTIFIED BY $APP_PASSWORD_SQL;
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$MYSQL_DATABASE\`.* TO 'vision_app'@'%';
-- 探活账号只有连接权：mysqladmin ping 无需任何库表授权，最小权限优先
CREATE USER IF NOT EXISTS 'healthcheck'@'localhost' IDENTIFIED BY $APP_PASSWORD_SQL;
FLUSH PRIVILEGES;
EOSQL
