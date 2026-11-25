#!/usr/bin/env bash
# 整机资源采样：只看 JVM 会漏掉 PG/Redis/系统层面的真实压力，这里按进程族全量取。
# 用法: sample.sh <秒数> <输出文件> [间隔秒]
set -uo pipefail
DURATION=$1
OUT=$2
INTERVAL=${3:-10}
END=$(( $(date +%s) + DURATION ))

if [ ! -s "$OUT" ]; then
  printf 'ts\tload1\tmem_total_mb\tmem_avail_mb\tswap_total_mb\tswap_used_mb\tjava_rss_mb\tpostgres_rss_mb\tredis_rss_mb\tnginx_rss_mb\tmoto_rss_mb\tpg_conns\treadiness\toom_kill_total\n' >> "$OUT"
fi

rss_mb() {
  local kb=0 p
  for p in $1; do
    [ -r "/proc/$p/status" ] && kb=$(( kb + $(awk '/^VmRSS:/{print $2}' "/proc/$p/status" 2>/dev/null || echo 0) ))
  done
  echo $(( kb / 1024 ))
}

while [ "$(date +%s)" -lt "$END" ]; do
  ts=$(date +%Y-%m-%dT%H:%M:%S%:z)
  load1=$(awk '{print $1}' /proc/loadavg)
  read -r mt ma st sf < <(awk '/^MemTotal:/{t=$2}/^MemAvailable:/{a=$2}/^SwapTotal:/{s=$2}/^SwapFree:/{f=$2}END{print t, a, s, f}' /proc/meminfo)
  app_pid=$(systemctl show -p MainPID --value visionspace 2>/dev/null || echo 0)
  java=$(rss_mb "$app_pid")
  pg=$(rss_mb "$(pgrep -d' ' -x postgres || true)")
  redis=$(rss_mb "$(pgrep -d' ' -x redis-server || true)")
  nginx=$(rss_mb "$(pgrep -d' ' -x nginx || true)")
  moto=$(rss_mb "$(pgrep -d' ' -f moto_server || true)")
  conns=$(sudo -u postgres psql -tAc "select count(*) from pg_stat_activity where datname = 'vision_space'" 2>/dev/null || echo NA)
  ready=$(curl -s -m 5 http://127.0.0.1:9081/actuator/health/readiness \
    | python3 -c 'import sys,json;print(json.load(sys.stdin).get("status","NA"))' 2>/dev/null || echo NA)
  oom=$(awk '/^oom_kill /{print $2}' /proc/vmstat)
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$ts" "$load1" $((mt / 1024)) $((ma / 1024)) $((st / 1024)) $(((st - sf) / 1024)) \
    "$java" "$pg" "$redis" "$nginx" "$moto" "$conns" "$ready" "$oom" >> "$OUT"
  sleep "$INTERVAL"
done
