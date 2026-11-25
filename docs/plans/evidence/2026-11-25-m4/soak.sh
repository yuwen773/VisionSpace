#!/usr/bin/env bash
# §5.9-4 1 小时 soak：整机采样（30s 间隔）+ 常压低速业务流量（每 5s 一次列表查询）。
# 目的不是压吞吐，而是看长时间运行下 RSS / swap / 连接数 / readiness 是否漂移。
# 用法: soak.sh <秒数> <采样文件> <流量日志>
set -uo pipefail
SECONDS_TOTAL=${1:-3600}
OUT=${2:-/root/m4/soak-samples.tsv}
TRAFFIC=${3:-/root/m4/soak-traffic.log}

systemctl show -p NRestarts --value visionspace > /root/m4/soak.nrestarts.before
systemctl show -p MainPID --value visionspace > /root/m4/soak.pid.before

setsid nohup /root/m4/sample.sh "$SECONDS_TOTAL" "$OUT" 30 \
  > /root/m4/soak-sampler.out 2>&1 < /dev/null &
SAMPLER_PID=$!

setsid nohup /opt/m4/venv/bin/python /root/m4/soak-traffic.py "$SECONDS_TOTAL" "$TRAFFIC" \
  > /root/m4/soak-traffic.out 2>&1 < /dev/null &
TRAFFIC_PID=$!

echo "sampler_pid=$SAMPLER_PID traffic_pid=$TRAFFIC_PID"
