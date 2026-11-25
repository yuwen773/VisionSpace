#!/usr/bin/env python3
"""soak 期间的常压低速业务流量：每 5 秒一次列表查询，只验证「长时间跑着会不会坏」。

单独成文件而不是嵌在 soak.sh 的 heredoc 里：`<<'PY' ... PY < /dev/null &`
会让 `< /dev/null` 覆盖 heredoc，python 读到空程序直接退出（2026-11-25 实测踩过）。
"""
import json
import sys
import time

import requests
import urllib3

urllib3.disable_warnings()

SECONDS_TOTAL = int(sys.argv[1])
LOG_PATH = sys.argv[2]
BASE = "https://127.0.0.1:18443"

creds = json.load(open("/root/m4/creds.json"))
password = creds["accounts"]["vs_m4_u2"]["password"]

session = requests.Session()
session.verify = False
session.get(BASE + "/api/user/login", timeout=30)
token = session.cookies.get("VS_CSRF")
resp = session.post(
    BASE + "/api/user/login",
    json={"userAccount": "vs_m4_u2", "userPassword": password},
    headers={"X-CSRF-Token": token, "Content-Type": "application/json"},
    timeout=60,
)

ok = fail = 0
end = time.time() + SECONDS_TOTAL
# buffering=1：行缓冲。`python -u` 只影响 stdout/stderr，不影响 open() 的文件对象，
# 不加这一条，链路中途挂掉时日志里一行都留不下（2026-11-25 实测踩过）。
with open(LOG_PATH, "a", encoding="utf-8", buffering=1) as log:
    log.write(f"# traffic start login_http={resp.status_code} at={time.strftime('%FT%T')}\n")
    while time.time() < end:
        started = time.time()
        try:
            token = session.cookies.get("VS_CSRF") or ""
            resp = session.post(
                BASE + "/api/picture/list/page/vo",
                json={"current": 1, "pageSize": 10, "reviewStatus": 1},
                headers={"X-CSRF-Token": token, "Content-Type": "application/json"},
                timeout=60,
            )
            elapsed_ms = int((time.time() - started) * 1000)
            if resp.status_code == 200 and resp.json().get("code") == 0:
                ok += 1
                log.write(f"{time.strftime('%FT%T')} {resp.status_code} {elapsed_ms}ms\n")
            else:
                fail += 1
                log.write(f"{time.strftime('%FT%T')} BAD {resp.status_code} {elapsed_ms}ms {resp.text[:200]}\n")
        except Exception as exc:  # 服务重启或被断连会走到这里
            fail += 1
            log.write(f"{time.strftime('%FT%T')} EXC {type(exc).__name__}: {str(exc)[:200]}\n")
        time.sleep(5)
    log.write(f"# traffic done ok={ok} fail={fail} at={time.strftime('%FT%T')}\n")
print(f"ok={ok} fail={fail}")
