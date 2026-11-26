#!/usr/bin/env python3
"""生产数据库初始化方案的隔离验证。

验证 deploy/init-production-db.sh 的四条承诺：

  1. **独立空库**：目标库是从零建出来的，业务表只有 Flyway 建的那些，
     运行角色 `vision_app` 能读、但不能建表（最小权限，SQLSTATE 42501）；
  2. **不碰既有库**：脚本跑之前存在的验收库（带数据），跑完之后行数、表数、
     种子行一条不少——本轮方案明确「保留验收库备份、清理另需授权」；
  3. **不覆盖**：对已存在的库再跑一次必须失败退出，而不是当成重复执行顺手重建；
  4. **角色创建**：在没有任何角色的干净集群里，脚本自己建角色，
     初始口令经环境变量交给 psql 的 \\getenv——不进 SQL 文本、不进 argv，
     且新角色的口令确实能登录。

隔离环境是 Docker 里的 postgres:18.7（命名卷 + vs.* 标签，与仓库其他隔离脚本同一套），
Flyway 用仓库固定的 13.13.0 maven 插件。退出码：0 通过 / 1 断言失败 / 2 环境不可用。
"""
import os
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POSTGRES_IMAGE = "postgres:18.7"
MIGRATOR_USER = "vision_migrator"
APP_USER = "vision_app"
ACCEPTANCE_DB = "vision_space"
PRODUCTION_DB = "vision_space_prod"
FLYWAY_PLUGIN = "org.flywaydb:flyway-maven-plugin:13.13.0"
INIT_SCRIPT = ROOT / "deploy" / "init-production-db.sh"
BASELINE = ROOT / "src" / "main" / "resources" / "db" / "migration" / "V1__baseline.sql"

# 与 scripts/acceptance.ps1 / drill-isolated-run.py 同一形状：只建角色、库、扩展、
# 时区与库注释，业务表一律交给 Flyway。
CONTAINER_INIT = r"""#!/bin/bash
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
  -c "ALTER DATABASE \"$ACCEPTANCE_DB\" SET timezone = 'UTC'"
"""


class CheckFailed(Exception):
    pass


def expected_table_count():
    """业务表数量从基线迁移脚本数出来，不写死字面量。

    写死的数字一定会过期：加了 V2 迁移后表变多，门禁会一边红一边让人以为
    迁移坏了。这里跟着迁移目录走。
    """
    text = BASELINE.read_text(encoding="utf-8")
    return sum(1 for line in text.splitlines() if line.startswith("CREATE TABLE "))


EXPECTED_TABLES = expected_table_count()


def docker(*args, **kwargs):
    return subprocess.run(["docker", *args], **kwargs)


def free_port():
    import socket
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def psql(container, database, user, password, sql, database_override=True):
    return docker("exec", "-e", f"PGPASSWORD={password}", container,
                  "psql", "--no-psqlrc", "-tAc", sql, "-U", user, "-d", database,
                  capture_output=True, encoding="utf-8", errors="replace")


def psql_verbose(container, database, user, password, sql):
    """带 VERBOSITY=verbose 的 psql：错误里带 SQLSTATE，便于断言具体错误码。"""
    return docker("exec", "-e", f"PGPASSWORD={password}", container,
                  "psql", "--no-psqlrc", "-v", "VERBOSITY=verbose", "-tAc", sql,
                  "-U", user, "-d", database,
                  capture_output=True, encoding="utf-8", errors="replace")


def wait_postgres(container, database, password, deadline=180):
    end = time.time() + deadline
    while time.time() < end:
        result = psql(container, database, APP_USER, password, "SELECT 1")
        if result.returncode == 0:
            return
        time.sleep(0.5)
    raise CheckFailed("isolated PostgreSQL did not accept application-role connections")


def wait_superuser(container, password, deadline=180):
    end = time.time() + deadline
    while time.time() < end:
        result = psql(container, "postgres", "postgres", password, "SELECT 1")
        if result.returncode == 0:
            return
        time.sleep(0.5)
    raise CheckFailed("isolated PostgreSQL did not accept superuser connections")


def flyway_migrate(port, database, password, user=MIGRATOR_USER):
    """用仓库固定的 Flyway 13.13.0 插件建表（唯一建表入口）。"""
    env = os.environ.copy()
    env["FLYWAY_URL"] = f"jdbc:postgresql://127.0.0.1:{port}/{database}"
    env["FLYWAY_USER"] = user
    env["FLYWAY_PASSWORD"] = password
    mvn_exe = "mvn.cmd" if os.name == "nt" else "mvn"
    result = subprocess.run([mvn_exe, "-B", "-N", "--no-transfer-progress",
                             f"{FLYWAY_PLUGIN}:migrate"],
                            cwd=str(ROOT), env=env, capture_output=True, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        tail = (result.stdout + result.stderr).strip().splitlines()[-15:]
        raise CheckFailed(f"Flyway migrate failed for {database}:\n" + "\n".join(tail))


def count(container, database, user, password, sql):
    result = psql(container, database, user, password, sql)
    if result.returncode != 0:
        raise CheckFailed(f"query failed: {sql} -> {result.stderr.strip()}")
    return result.stdout.strip()


def business_tables(container, database, user, password):
    return count(container, database, user, password,
                 "SELECT count(*) FROM information_schema.tables "
                 "WHERE table_schema='public' AND table_type='BASE TABLE' "
                 "AND table_name <> 'flyway_schema_history'")


def start_container(name, nonce, port, init_path=None):
    """起一个隔离的 postgres:18.7。

    给了 init_path 就顺带建好两个角色与验收库（供「角色已存在」分支）；
    不给就留一个一个角色都没有的干净集群（供「角色由脚本创建」分支）。
    """
    args = ["run", "-d", "--name", name,
            "-p", f"127.0.0.1:{port}:5432",
            "-e", f"POSTGRES_PASSWORD={nonce}", "-e", "POSTGRES_USER=postgres",
            "-e", "POSTGRES_DB=postgres", "-e", f"ACCEPTANCE_DB={ACCEPTANCE_DB}",
            "-e", f"ACCEPTANCE_NONCE={nonce}", "-e", "TZ=UTC",
            "--label", "vs.owner=vision-space", "--label", f"vs.run-id={nonce}",
            "--label", "vs.kind=test",
            "-v", f"{name}-pgdata:/var/lib/postgresql"]
    if init_path is not None:
        args += ["--mount", f"type=bind,source={init_path},target=/docker-entrypoint-initdb.d/01-acceptance.sh,readonly"]
    args += [POSTGRES_IMAGE]
    return docker(*args, capture_output=True, encoding="utf-8", errors="replace")


def run_init_script(container, password, database, migrator_password=None, app_password=None):
    """把初始化脚本送进容器执行：超级用户走 TCP + PGPASSWORD（口令不进 argv）。"""
    copy = docker("cp", str(INIT_SCRIPT), f"{container}:/tmp/init-production-db.sh",
                  capture_output=True, encoding="utf-8", errors="replace")
    if copy.returncode != 0:
        raise CheckFailed(f"docker cp failed: {copy.stderr.strip()}")
    env_args = ["-e", f"VS_PG_SUPERUSER_PASSWORD={password}",
                "-e", "VS_PG_HOST=127.0.0.1",
                "-e", "VS_PG_PORT=5432",
                "-e", f"VS_DB_NAME={database}"]
    if migrator_password is not None:
        env_args += ["-e", f"VS_MIGRATOR_PASSWORD={migrator_password}"]
    if app_password is not None:
        env_args += ["-e", f"VS_APP_PASSWORD={app_password}"]
    return docker("exec", "-i", *env_args, container, "bash", "/tmp/init-production-db.sh",
                  capture_output=True, encoding="utf-8", errors="replace")


def verify_existing_role_branch(run_dir, nonce):
    """分支一：角色已存在 —— 脚本只建库，不碰既有角色与既有库。"""
    container = f"vs-proddb-postgres-{nonce}"
    volume = f"{container}-pgdata"
    port = free_port()
    created_volume = False
    try:
        volume_result = docker("volume", "create",
                                "--label", "vs.owner=vision-space", "--label", f"vs.run-id={nonce}",
                                "--label", "vs.kind=test", volume,
                                capture_output=True, encoding="utf-8", errors="replace")
        if volume_result.returncode != 0:
            raise CheckFailed("named volume creation failed")
        created_volume = True

        init_path = run_dir / "init.sh"
        init_path.write_text(CONTAINER_INIT, encoding="utf-8", newline="\n")

        pg = start_container(container, nonce, port, init_path=str(init_path))
        if pg.returncode != 0:
            raise CheckFailed(f"container startup failed: {pg.stderr.strip()}")

        wait_postgres(container, ACCEPTANCE_DB, nonce)

        # ---- 先造一个「验收库」：有表、有数据 ----
        flyway_migrate(port, ACCEPTANCE_DB, nonce)
        marker = psql(container, ACCEPTANCE_DB, "postgres", nonce,
                      "INSERT INTO app_user (user_account, user_password) "
                      "VALUES ('acceptance_marker', 'marker') RETURNING id")
        if marker.returncode != 0:
            raise CheckFailed(f"cannot seed acceptance row: {marker.stderr.strip()}")
        before_tables = business_tables(container, ACCEPTANCE_DB, MIGRATOR_USER, nonce)
        before_users = count(container, ACCEPTANCE_DB, MIGRATOR_USER, nonce, "SELECT count(*) FROM app_user")
        before_switch = count(container, ACCEPTANCE_DB, MIGRATOR_USER, nonce,
                               "SELECT count(*) FROM storage_switch_state")
        if before_tables != str(EXPECTED_TABLES):
            raise CheckFailed(f"验收库业务表数量异常：{before_tables}（期望 {EXPECTED_TABLES}）")
        if before_users != "1":
            raise CheckFailed(f"验收库种子行异常：{before_users}")

        # ---- 跑初始化脚本：建独立空库 ----
        result = run_init_script(container, nonce, PRODUCTION_DB)
        (run_dir / "init-output.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        if result.returncode != 0:
            raise CheckFailed(f"init-production-db.sh failed:\n{result.stdout}\n{result.stderr}")

        exists = psql(container, "postgres", "postgres", nonce,
                      f"SELECT 1 FROM pg_database WHERE datname='{PRODUCTION_DB}'")
        if exists.stdout.strip() != "1":
            raise CheckFailed("生产库没有被创建")

        owner = psql(container, "postgres", "postgres", nonce,
                     f"SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='{PRODUCTION_DB}'")
        if owner.stdout.strip() != MIGRATOR_USER:
            raise CheckFailed(f"生产库 owner 不对：{owner.stdout.strip()}")

        # ---- 生产库建表（唯一入口仍是 Flyway）----
        flyway_migrate(port, PRODUCTION_DB, nonce)

        prod_tables = business_tables(container, PRODUCTION_DB, MIGRATOR_USER, nonce)
        if prod_tables != str(EXPECTED_TABLES):
            raise CheckFailed(f"生产库业务表数量异常：{prod_tables}（期望 {EXPECTED_TABLES}）")
        prod_history = count(container, PRODUCTION_DB, MIGRATOR_USER, nonce,
                             "SELECT count(*) FROM flyway_schema_history")
        if prod_history != "1":
            raise CheckFailed(f"生产库 Flyway 历史行数异常：{prod_history}")
        prod_users = count(container, PRODUCTION_DB, APP_USER, nonce, "SELECT count(*) FROM app_user")
        if prod_users != "0":
            raise CheckFailed(f"生产库不是空库：app_user 有 {prod_users} 行")

        # 运行角色可读、不可建表：必须是权限不足（42501），不能是别的错
        ddl = psql_verbose(container, PRODUCTION_DB, APP_USER, nonce,
                           "CREATE TABLE vs_proddb_probe(id int)")
        if ddl.returncode == 0:
            raise CheckFailed("运行角色竟然能在生产库建表")
        ddl_text = (ddl.stderr + ddl.stdout)
        if "42501" not in ddl_text:
            raise CheckFailed(f"DDL 负例的 SQLSTATE 不是 42501：{ddl_text.strip()}")
        if "permission denied" not in ddl_text.lower():
            raise CheckFailed(f"DDL 负例的原因不是权限不足：{ddl_text.strip()}")

        # ---- 验收库必须原封不动 ----
        after_tables = business_tables(container, ACCEPTANCE_DB, MIGRATOR_USER, nonce)
        after_users = count(container, ACCEPTANCE_DB, MIGRATOR_USER, nonce, "SELECT count(*) FROM app_user")
        after_switch = count(container, ACCEPTANCE_DB, MIGRATOR_USER, nonce,
                              "SELECT count(*) FROM storage_switch_state")
        if after_tables != before_tables or after_users != before_users or after_switch != before_switch:
            raise CheckFailed(
                f"验收库被改动了：表 {before_tables}->{after_tables}，"
                f"用户 {before_users}->{after_users}，开关行 {before_switch}->{after_switch}")

        # ---- 对已存在的库再跑一次：必须拒绝，不覆盖 ----
        again = run_init_script(container, nonce, PRODUCTION_DB)
        if again.returncode == 0:
            raise CheckFailed("初始化脚本对已存在的库没有拒绝执行")
        tables_after_retry = business_tables(container, PRODUCTION_DB, MIGRATOR_USER, nonce)
        if tables_after_retry != str(EXPECTED_TABLES):
            raise CheckFailed(f"重跑之后生产库结构变了：{tables_after_retry}")
    finally:
        docker("rm", "-f", "-v", container, capture_output=True)
        if created_volume:
            docker("volume", "rm", "-f", volume, capture_output=True)


def verify_role_creation_branch(run_dir, nonce):
    """分支二：干净集群 —— 角色由脚本创建，口令经环境变量交给 psql。"""
    container = f"vs-proddb-fresh-{nonce}"
    volume = f"{container}-pgdata"
    port = free_port()
    created_volume = False
    # 带特殊字符，验证口令确实原样传下去（也要证明它没被截断或转义坏）
    migrator_secret = "Proddb-Migrator-9f2c!x"
    app_secret = "Proddb-App-7a1e#y"
    try:
        volume_result = docker("volume", "create",
                                "--label", "vs.owner=vision-space", "--label", f"vs.run-id={nonce}",
                                "--label", "vs.kind=test", volume,
                                capture_output=True, encoding="utf-8", errors="replace")
        if volume_result.returncode != 0:
            raise CheckFailed("named volume creation failed")
        created_volume = True

        pg = start_container(container, nonce, port)
        if pg.returncode != 0:
            raise CheckFailed(f"container startup failed: {pg.stderr.strip()}")
        wait_superuser(container, nonce)

        roles_before = count(container, "postgres", "postgres", nonce,
                             f"SELECT count(*) FROM pg_roles WHERE rolname IN ('{MIGRATOR_USER}', '{APP_USER}')")
        if roles_before != "0":
            raise CheckFailed(f"干净集群里不该有角色，却有 {roles_before} 个")

        result = run_init_script(container, nonce, PRODUCTION_DB,
                                 migrator_password=migrator_secret,
                                 app_password=app_secret)
        (run_dir / "init-fresh-output.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        if result.returncode != 0:
            raise CheckFailed(f"init-production-db.sh 在干净集群上失败：\n{result.stdout}\n{result.stderr}")

        # 口令不许出现在脚本输出里（不进日志）
        combined = result.stdout + result.stderr
        for secret in (migrator_secret, app_secret):
            if secret in combined:
                raise CheckFailed("初始化脚本把角色口令打印到了输出里")

        roles = count(container, "postgres", "postgres", nonce,
                      "SELECT rolname || '|' || rolcanlogin || '|' || rolinherit || '|' || rolsuper "
                      f"|| '|' || rolcreatedb || '|' || rolcreaterole FROM pg_roles "
                      f"WHERE rolname IN ('{MIGRATOR_USER}', '{APP_USER}') ORDER BY rolname")
        expected = {
            MIGRATOR_USER: f"{MIGRATOR_USER}|true|true|false|false|false",
            APP_USER: f"{APP_USER}|true|false|false|false|false",
        }
        for line in roles.splitlines():
            if line not in expected.values():
                raise CheckFailed(f"角色属性不对：{line}")
        if len(roles.splitlines()) != 2:
            raise CheckFailed(f"角色数量不对：{roles}")

        # 新角色的口令必须真能登录
        login = psql(container, "postgres", APP_USER, app_secret, "SELECT current_user")
        if login.stdout.strip() != APP_USER:
            raise CheckFailed(f"运行角色用新口令登录失败：{login.stdout.strip()} {login.stderr.strip()}")
        migrator_login = psql(container, "postgres", MIGRATOR_USER, migrator_secret, "SELECT current_user")
        if migrator_login.stdout.strip() != MIGRATOR_USER:
            raise CheckFailed(f"迁移角色用新口令登录失败：{migrator_login.stdout.strip()} {migrator_login.stderr.strip()}")

        # 迁移角色应当能建表（它是 owner），运行角色不行
        flyway_migrate(port, PRODUCTION_DB, migrator_secret, user=MIGRATOR_USER)
        prod_tables = business_tables(container, PRODUCTION_DB, MIGRATOR_USER, migrator_secret)
        if prod_tables != str(EXPECTED_TABLES):
            raise CheckFailed(f"新迁移角色建表后的表数异常：{prod_tables}（期望 {EXPECTED_TABLES}）")
    finally:
        docker("rm", "-f", "-v", container, capture_output=True)
        if created_volume:
            docker("volume", "rm", "-f", volume, capture_output=True)


def main():
    if docker("info", capture_output=True).returncode != 0:
        print("BLOCKED_ENV: Docker engine unavailable", file=sys.stderr)
        return 2
    if not INIT_SCRIPT.is_file():
        print(f"BLOCKED_ENV: 找不到 {INIT_SCRIPT}", file=sys.stderr)
        return 2
    if not BASELINE.is_file():
        print(f"BLOCKED_ENV: 找不到 {BASELINE}", file=sys.stderr)
        return 2

    nonce = uuid.uuid4().hex
    run_dir = Path(tempfile.gettempdir()) / f"visionspace-proddb-{nonce}"
    run_dir.mkdir(parents=True, exist_ok=True)
    exit_code = 1

    try:
        verify_existing_role_branch(run_dir, nonce)
        verify_role_creation_branch(run_dir, nonce)
        print("PASS: 独立空库初始化成立，验收库未被改动，已存在的库不会被覆盖，"
              "干净集群里角色由脚本创建且口令可用")
        exit_code = 0
    except CheckFailed as exc:
        print(f"FAIL: {exc}")
        exit_code = 1
    except Exception as exc:  # noqa: BLE001 - 报告并保留清理
        print(f"BLOCKED_ENV: 初始化验证异常中断（RUNNER_ERROR）：{exc}", file=sys.stderr)
        exit_code = 2
    finally:
        if os.environ.get("VS_PRODDB_KEEP_LOGS"):
            print(f"[proddb] logs: {run_dir}")
        else:
            for entry in run_dir.glob("*"):
                try:
                    entry.unlink()
                except OSError:
                    pass
            try:
                run_dir.rmdir()
            except OSError:
                pass
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
