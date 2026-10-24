#!/bin/bash
# 首次初始化时创建应用专用账号与探活账号（只在空数据目录执行一次）。
# MySQL 官方镜像不会对 .sql 文件做变量替换，因此用 .sh 经 docker_process_sql 执行，
# 口令全部来自容器环境变量，不落盘、不进日志。
set -euo pipefail

docker_process_sql --database="$MYSQL_DATABASE" <<-EOSQL
CREATE USER IF NOT EXISTS 'vision_app'@'%' IDENTIFIED BY '$MYSQL_APP_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$MYSQL_DATABASE\`.* TO 'vision_app'@'%';
-- 探活账号只有连接权：mysqladmin ping 无需任何库表授权，最小权限优先
CREATE USER IF NOT EXISTS 'healthcheck'@'localhost' IDENTIFIED BY '$MYSQL_APP_PASSWORD';
FLUSH PRIVILEGES;
EOSQL
