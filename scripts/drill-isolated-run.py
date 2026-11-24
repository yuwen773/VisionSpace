#!/usr/bin/env python3
"""Provision an isolated PostgreSQL 18 + Redis instance for a single acceptance test class.

This is the native-binaries equivalent of scripts/acceptance.ps1's isolation setup,
kept deliberately small so it can run without PowerShell in a worktree session.
It starts disposable `postgres:18.7` and `redis:7.2.4` containers on random ports,
seeds roles/database/extension the same way scripts/acceptance.ps1 does, applies the
Flyway baseline as `vision_migrator` (the only schema entry point), then runs
`mvn -Dtest=<class> test` with the env vars IsolationSettings requires, and finally
removes only the containers and named volume it created. It never reads existing
PostgreSQL/Redis services and never uses 5432/6379.
"""
import argparse
import os
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POSTGRES_IMAGE = "postgres:18.7"
REDIS_IMAGE = "redis:7.2.4"
APP_USER = "vision_app"
MIGRATOR_USER = "vision_migrator"
FLYWAY_PLUGIN = "org.flywaydb:flyway-maven-plugin:13.13.0"

# 与 scripts/acceptance.ps1 的初始化脚本保持同一形状：只建角色、库、扩展、时区与库注释，
# 业务表一律交给 Flyway。
INIT_SCRIPT = r"""#!/bin/bash
set -euo pipefail
psql --no-psqlrc -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -c "CREATE ROLE vision_migrator LOGIN PASSWORD '$ACCEPTANCE_NONCE' NOSUPERUSER NOCREATEDB NOCREATEROLE"
psql --no-psqlrc -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -c "CREATE ROLE vision_app LOGIN PASSWORD '$ACCEPTANCE_NONCE' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT"
psql --no-psqlrc -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -c "CREATE DATABASE \"$ACCEPTANCE_DB\" OWNER vision_migrator"
psql --no-psqlrc -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$ACCEPTANCE_DB" \
  -c "REVOKE CREATE ON SCHEMA public FROM PUBLIC" \
  -c "GRANT USAGE ON SCHEMA public TO vision_app" \
  -c "CREATE EXTENSION IF NOT EXISTS pgcrypto" \
  -c "ALTER DATABASE \"$ACCEPTANCE_DB\" SET timezone = 'UTC'" \
  -c "COMMENT ON DATABASE \"$ACCEPTANCE_DB\" IS '$ACCEPTANCE_NONCE'"
"""


def free_port():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def docker(*args, **kwargs):
    return subprocess.run(["docker", *args], **kwargs)


def published_port(container, internal):
    out = docker("port", container, str(internal), capture_output=True, text=True)
    if out.returncode != 0 or not out.stdout.strip():
        raise RuntimeError(f"cannot resolve published port for {container}:{internal}")
    return int(out.stdout.strip().splitlines()[-1].rsplit(":", 1)[1])


def psql(container, database, user, password, sql):
    return docker("exec", "-e", f"PGPASSWORD={password}", container,
                  "psql", "--no-psqlrc", "-tAc", sql, "-U", user, "-d", database,
                  capture_output=True, text=True)


def wait_postgres(container, database, password, deadline=180):
    end = time.time() + deadline
    while time.time() < end:
        result = psql(container, database, APP_USER, password, "SELECT 1")
        if result.returncode == 0:
            return
        time.sleep(0.5)
    raise RuntimeError("isolated PostgreSQL did not accept application-role connections")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--test", default=None,
                    help="Single test class (or comma-separated classes) for a focused run.")
    ap.add_argument("--reports", default=None)
    ap.add_argument("--full", action="store_true",
                    help="Run the whole backend test suite (mvn verify) with a real second JVM, "
                         "mirroring scripts/acceptance.ps1's dual-instance setup.")
    args = ap.parse_args()

    if docker("info", capture_output=True).returncode != 0:
        sys.exit("BLOCKED_ENV: Docker engine unavailable")

    nonce = uuid.uuid4().hex
    database = f"vs_acceptance_{nonce}"
    run_dir = Path(tempfile.gettempdir()) / f"visionspace-drill-{nonce}"
    run_dir.mkdir(parents=True, exist_ok=False)
    postgres_container = f"vs-drill-postgres-{nonce}"
    redis_container = f"vs-drill-redis-{nonce}"
    volume = f"vs-drill-pgdata-{nonce}"
    postgres_port = free_port()
    redis_port = free_port()
    while redis_port == postgres_port or postgres_port < 10000 or redis_port < 10000:
        postgres_port, redis_port = free_port(), free_port()

    created_containers = []
    created_volume = False
    exit_code = 1
    try:
        print(f"[drill] run dir: {run_dir}", flush=True)
        init_path = run_dir / "postgres-init.sh"
        init_path.write_text(INIT_SCRIPT, encoding="utf-8", newline="\n")

        # postgres:18 声明 VOLUME /var/lib/postgresql（PGDATA=/var/lib/postgresql/18/docker）：
        # 显式挂命名卷，避免匿名卷在容器删除后无法回收。
        volume_result = docker("volume", "create", "--label", f"vs.run={nonce}",
                               "--label", "vs.role=drill", volume, capture_output=True)
        if volume_result.returncode != 0:
            raise RuntimeError("isolated PostgreSQL volume creation failed")
        created_volume = True

        pg = docker("run", "-d", "--name", postgres_container,
                    "-p", f"127.0.0.1:{postgres_port}:5432",
                    "-e", f"POSTGRES_PASSWORD={nonce}", "-e", "POSTGRES_USER=postgres",
                    "-e", "POSTGRES_DB=postgres", "-e", f"ACCEPTANCE_DB={database}",
                    "-e", f"ACCEPTANCE_NONCE={nonce}", "-e", "TZ=UTC",
                    "--label", f"vs.run={nonce}", "--label", "vs.role=drill",
                    "-v", f"{volume}:/var/lib/postgresql",
                    "--mount", f"type=bind,source={init_path},target=/docker-entrypoint-initdb.d/01-acceptance.sh,readonly",
                    # 全量套件会同时存活多个 Spring 上下文（含双实例用例），每个池都要连接；
                    # 镜像默认 100 个连接会被用尽，双实例用例因此拿不到连接。测试实例放宽到 200。
                    POSTGRES_IMAGE, "-c", "max_connections=200", capture_output=True, text=True)
        if pg.returncode != 0:
            raise RuntimeError(f"isolated PostgreSQL startup failed: {pg.stderr.strip()}")
        created_containers.append(postgres_container)
        redis = docker("run", "-d", "--name", redis_container,
                       "-p", f"127.0.0.1:{redis_port}:6379",
                       "--label", f"vs.run={nonce}", "--label", "vs.role=drill",
                       REDIS_IMAGE, "redis-server", "--requirepass", nonce,
                       capture_output=True, text=True)
        if redis.returncode != 0:
            raise RuntimeError(f"isolated Redis startup failed: {redis.stderr.strip()}")
        created_containers.append(redis_container)

        wait_postgres(postgres_container, database, nonce)

        env = os.environ.copy()
        env["VS_ACCEPTANCE_NONCE"] = nonce
        env["VS_ACCEPTANCE_PG_PORT"] = str(postgres_port)
        env["VS_ACCEPTANCE_REDIS_PORT"] = str(redis_port)
        # PostgreSQL 容器、库与会话都用 UTC；测试 JVM 必须同区，否则租约/过期类断言
        # 会整体偏移本机时区（+08:00）。
        env["JAVA_TOOL_OPTIONS"] = "-Duser.timezone=UTC"
        env["npm_config_cache"] = str(Path(tempfile.gettempdir()) / "npm-cache-vs")

        # 唯一建表入口：Flyway 以迁移角色执行基线；口令走环境变量，不进 argv。
        flyway_env = env.copy()
        flyway_env["FLYWAY_URL"] = f"jdbc:postgresql://127.0.0.1:{postgres_port}/{database}"
        flyway_env["FLYWAY_USER"] = MIGRATOR_USER
        flyway_env["FLYWAY_PASSWORD"] = nonce
        mvn_exe = "mvn.cmd" if os.name == "nt" else "mvn"
        migrate = subprocess.run([mvn_exe, "-B", "-N", "--no-transfer-progress",
                                  f"{FLYWAY_PLUGIN}:migrate"],
                                 cwd=str(ROOT), env=flyway_env,
                                 capture_output=True, text=True)
        (run_dir / "flyway-migrate.log").write_text(migrate.stdout + migrate.stderr, encoding="utf-8")
        if migrate.returncode != 0:
            raise RuntimeError("Flyway migrate failed")

        marker = docker("exec", redis_container, "redis-cli", "-a", nonce, "-n", "15",
                        "SET", "acceptance:instance", nonce, capture_output=True, text=True)
        (run_dir / "redis-marker.log").write_text(marker.stdout + marker.stderr, encoding="utf-8")
        if marker.returncode != 0:
            raise RuntimeError("redis marker creation failed")

        reports = Path(args.reports) if args.reports else run_dir / "surefire"

        if args.full:
            # scripts/acceptance.ps1: clean test-compile, build a test classpath, then start a
            # real second JVM that shares this isolated PostgreSQL/Redis and hands back its port.
            pre = subprocess.run([mvn_exe, "-B", "--no-transfer-progress",
                       f"-Dacceptance.reportsDirectory={reports}", "clean", "test-compile"],
                      cwd=str(ROOT), env=env)
            if pre.returncode != 0:
                raise RuntimeError("clean test-compile failed")
            cp_file = run_dir / "test-cp.txt"
            cp = subprocess.run([mvn_exe, "-B", "--no-transfer-progress", "-q", "dependency:build-classpath",
                      f"-Dmdep.outputFile={cp_file}", "-DincludeScope=test"],
                     cwd=str(ROOT), env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            if cp.returncode != 0 or not cp_file.is_file():
                raise RuntimeError("dependency:build-classpath failed")
            sep = ";" if os.name == "nt" else ":"
            classpath = sep.join([
                str(ROOT / "target" / "classes"),
                str(ROOT / "target" / "test-classes"),
                cp_file.read_text(encoding="utf-8").strip(),
            ])
            java_bin = Path(env.get("JAVA_HOME", "")) / "bin" / (
                "java.exe" if os.name == "nt" else "java")
            if not java_bin.is_file():
                java_bin = "java"
            ready_file = run_dir / "second-instance.port"
            second = subprocess.Popen(
                [str(java_bin), "-cp", classpath,
                 "com.yuwen.visionspace.acceptance.VipSecondInstanceMain", str(ready_file)],
                cwd=str(ROOT), env=env,
                stdout=(run_dir / "second-instance-out.log").open("wb"),
                stderr=(run_dir / "second-instance-error.log").open("wb"),
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            owned_second = second
            deadline = time.time() + 120
            while time.time() < deadline and not ready_file.is_file():
                if second.poll() is not None:
                    raise RuntimeError("second instance exited early")
                time.sleep(0.5)
            if not ready_file.is_file():
                raise RuntimeError("BLOCKED_ENV: second instance did not become ready in 120s")
            second_port = ready_file.read_text(encoding="utf-8").strip()
            print(f"[drill] second instance ready on port {second_port}", flush=True)
            cmd = [mvn_exe, "-B", "--no-transfer-progress",
                   f"-Dacceptance.reportsDirectory={reports}",
                   f"-Dvs.acceptance.second-port={second_port}", "verify"]
        else:
            if not args.test:
                raise RuntimeError("--test is required unless --full is used")
            owned_second = None
            cmd = [mvn_exe, "-B", "--no-transfer-progress", f"-Dtest={args.test}",
                   f"-Dacceptance.reportsDirectory={reports}", "test"]
        print(f"[drill] {' '.join(cmd)}", flush=True)
        result = subprocess.run(cmd, cwd=str(ROOT), env=env)
        exit_code = result.returncode
        print(f"[drill] RESULT exit={exit_code} logs={run_dir} reports={reports}", flush=True)
    except Exception as exc:  # noqa: BLE001 - report and keep cleanup
        print(f"[drill] RUNNER_ERROR: {exc}", flush=True)
        exit_code = 99
        owned_second = locals().get("owned_second")
    finally:
        # 第二实例先写 stop 文件让它自行退出，再兜底强杀。
        try:
            second = locals().get("owned_second")
            if second is not None:
                Path(str(run_dir / "second-instance.port.stop")).write_text("stop", encoding="utf-8")
                try:
                    second.wait(timeout=15)
                except Exception:
                    second.kill()
        except Exception:
            pass
        # 只回收本轮创建的容器与命名卷：先删容器（pg 需要先停），再删卷。
        for container in reversed(created_containers):
            docker("rm", "-f", "-v", container, capture_output=True)
        if created_volume:
            docker("volume", "rm", "-f", volume, capture_output=True)
        # 日志很小、留作证据；数据在命名卷里，随卷删除一并回收。
        if not os.environ.get("VS_DRILL_KEEP_LOGS"):
            keep = {"flyway-migrate.log", "redis-marker.log"}
            for entry in run_dir.iterdir():
                if entry.is_file() and entry.name not in keep:
                    try:
                        entry.unlink()
                    except Exception:
                        pass
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
