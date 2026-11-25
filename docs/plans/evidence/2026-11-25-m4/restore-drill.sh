#!/usr/bin/env bash
# §5.10 / §8.5-8.6 备份恢复演练：
#   1) pg_dump -Fc 备份现有业务库
#   2) 恢复到新库 vision_space_restore（不动生产库）
#   3) 逐表对比行数
#   4) 应用临时指向新库 -> readiness UP -> 真实业务请求成功 -> 切回
# 口令只经环境变量 PGPASSWORD，不进 argv/日志。
set -euo pipefail
TS=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR=/opt/visionspace/backup
DUMP="$BACKUP_DIR/vision_space-$TS.dump"
APP_ENV=/opt/visionspace/app.env
WORK=/root/m4

mkdir -p "$BACKUP_DIR"
chown visionspace:visionspace "$BACKUP_DIR" 2>/dev/null || true

echo "== 1. 备份 =="
PGPASSWORD=$(cat /root/.vs-secrets/migrator.pw) \
  /usr/lib/postgresql/18/bin/pg_dump -h 127.0.0.1 -p 5432 -U vision_migrator \
  -d vision_space -Fc -f "$DUMP"
chmod 600 "$DUMP"
ls -l "$DUMP"
/usr/lib/postgresql/18/bin/pg_restore --list "$DUMP" | tail -3

echo "== 2. 建恢复库并恢复 =="
if sudo -u postgres psql -tAc "select 1 from pg_database where datname='vision_space_restore'" | grep -q 1; then
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c "DROP DATABASE vision_space_restore" >/dev/null
fi
sudo -u postgres psql -v ON_ERROR_STOP=1 -c \
  "CREATE DATABASE vision_space_restore OWNER vision_migrator" >/dev/null
START=$(date +%s)
# 保留 ACL：vision_app 的授权随 dump 一起恢复，否则应用连上恢复库也读不到表。
PGPASSWORD=$(cat /root/.vs-secrets/migrator.pw) \
  /usr/lib/postgresql/18/bin/pg_restore -h 127.0.0.1 -p 5432 -U vision_migrator \
  -d vision_space_restore --no-owner "$DUMP"
echo "restore_seconds=$(( $(date +%s) - START ))"

echo "== 3. 行数对比（源库 vs 恢复库） =="
TABLES="app_user space picture user_picture_action picture_stats storage_config agent_session flyway_schema_history"
printf 'table\tvision_space\tvision_space_restore\tmatch\n' > "$WORK/restore-counts.tsv"
MISMATCH=0
for t in $TABLES; do
  a=$(PGPASSWORD=$(cat /root/.vs-secrets/app.pw) psql -h 127.0.0.1 -U vision_app -d vision_space -tAc "select count(*) from $t")
  b=$(PGPASSWORD=$(cat /root/.vs-secrets/app.pw) psql -h 127.0.0.1 -U vision_app -d vision_space_restore -tAc "select count(*) from $t" 2>/dev/null || echo ERR)
  ok=YES; [ "$a" = "$b" ] || { ok=NO; MISMATCH=1; }
  printf '%s\t%s\t%s\t%s\n' "$t" "$a" "$b" "$ok" >> "$WORK/restore-counts.tsv"
done
cat "$WORK/restore-counts.tsv"

echo "== 4. 应用临时指向恢复库 =="
cp -p "$APP_ENV" "$WORK/app.env.preswitch"
python3 - <<'PY'
import re
path = "/opt/visionspace/app.env"
text = open(path).read()
new, n = re.subn(r'(?m)^SPRING_DATASOURCE_URL=.*$',
                 'SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/vision_space_restore',
                 text)
assert n == 1, f"SPRING_DATASOURCE_URL 行数异常: {n}"
open(path, "w").write(new)
PY
chmod 600 "$APP_ENV"
RESTART_AT=$(date +%s)
systemctl restart visionspace
UP=NO
for _ in $(seq 1 240); do
  if curl -sf -m 5 http://127.0.0.1:9081/actuator/health/readiness | grep -q '"status":"UP"'; then UP=YES; break; fi
  sleep 0.5
done
echo "readiness_on_restore_db=$UP cold_start_seconds=$(( $(date +%s) - RESTART_AT ))"
curl -s http://127.0.0.1:9081/actuator/health/readiness; echo
# 恢复库里的真实业务读：登录 + 列表
/opt/m4/venv/bin/python - <<'PY'
import json, requests, urllib3
urllib3.disable_warnings()
creds = json.load(open("/root/m4/creds.json"))
pw = creds["accounts"]["vs_m4_u2"]["password"]
s = requests.Session(); s.verify = False
s.get("https://127.0.0.1:18443/api/user/login", timeout=30)
tok = s.cookies.get("VS_CSRF")
r = s.post("https://127.0.0.1:18443/api/user/login",
           json={"userAccount": "vs_m4_u2", "userPassword": pw},
           headers={"X-CSRF-Token": tok, "Content-Type": "application/json"}, timeout=60)
print("login_http", r.status_code, json.dumps(r.json(), ensure_ascii=False)[:160])
tok = s.cookies.get("VS_CSRF")
r = s.post("https://127.0.0.1:18443/api/picture/list/page/vo",
           json={"current": 1, "pageSize": 5, "reviewStatus": 1},
           headers={"X-CSRF-Token": tok, "Content-Type": "application/json"}, timeout=60)
body = r.json(); total = body.get("data", {}).get("total") if isinstance(body.get("data"), dict) else None
print("list_http", r.status_code, "total", total)
PY

echo "== 5. 切回生产库 =="
cp -p "$WORK/app.env.preswitch" "$APP_ENV"
chmod 600 "$APP_ENV"
SWITCH_AT=$(date +%s)
systemctl restart visionspace
for _ in $(seq 1 240); do
  if curl -sf -m 5 http://127.0.0.1:9081/actuator/health/readiness | grep -q '"status":"UP"'; then break; fi
  sleep 0.5
done
echo "switched_back_seconds=$(( $(date +%s) - SWITCH_AT ))"
curl -s http://127.0.0.1:9081/actuator/health/readiness; echo
grep -c . "$APP_ENV" >/dev/null && echo "app.env restored (600)"
echo "MISMATCH=$MISMATCH DUMP=$DUMP"
