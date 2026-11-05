#!/usr/bin/env python3
"""Provision an isolated MySQL 8 + Redis instance for a single acceptance test class.

This is the native-binaries equivalent of scripts/acceptance.ps1's isolation setup,
kept deliberately small so it can run without PowerShell in a worktree session.
It creates a fresh temp datadir, random ports, a dedicated DB/user and the two
instance markers, then runs `mvn -Dtest=<class> test` with the three env vars the
IsolatedApplicationInitializer requires, and finally shuts down only its own
processes. It never reads existing MySQL/Redis config and never uses 3306/6379.
"""
import argparse
import os
import re
import socket
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def free_port():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def wait_port(port, deadline=90):
    end = time.time() + deadline
    while time.time() < end:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.3)
    raise RuntimeError(f"isolated service did not open port {port}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mysql-bin", required=True)
    ap.add_argument("--redis-bin", required=True)
    ap.add_argument("--test", default=None,
                    help="Single test class (or comma-separated classes) for a focused run.")
    ap.add_argument("--reports", default=None)
    ap.add_argument("--full", action="store_true",
                    help="Run the whole backend test suite (mvn verify) with a real second JVM, "
                         "mirroring scripts/acceptance.ps1's dual-instance setup.")
    args = ap.parse_args()

    mysql_bin = Path(args.mysql_bin)
    redis_bin = Path(args.redis_bin)
    mysqld = mysql_bin / "mysqld.exe"
    mysql = mysql_bin / "mysql.exe"
    redis_server = redis_bin / "redis-server.exe"
    redis_cli = redis_bin / "redis-cli.exe"
    for exe in (mysqld, mysql, redis_server, redis_cli):
        if not exe.is_file():
            sys.exit(f"Missing executable: {exe}")

    nonce = uuid.uuid4().hex
    database = f"vs_acceptance_{nonce}"
    run_dir = Path(tempfile.gettempdir()) / f"visionspace-drill-{nonce}"
    run_dir.mkdir(parents=True, exist_ok=False)
    data_dir = run_dir / "mysql"
    mysql_port = free_port()
    redis_port = free_port()
    while redis_port == mysql_port or mysql_port < 10000 or redis_port < 10000:
        mysql_port, redis_port = free_port(), free_port()

    owned = []

    def spawn(exe, argv, out_name, err_name):
        out = (run_dir / out_name).open("wb")
        err = (run_dir / err_name).open("wb")
        # CREATE_NO_WINDOW keeps Windows from flashing a console; harmless elsewhere.
        flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        proc = subprocess.Popen([str(exe)] + argv, stdout=out, stderr=err,
                                creationflags=flags)
        owned.append((proc, out, err))
        return proc

    mysql_proc = None
    redis_proc = None
    exit_code = 1
    try:
        print(f"[drill] run dir: {run_dir}", flush=True)
        init = subprocess.run([str(mysqld), "--no-defaults", "--initialize-insecure",
                    f"--datadir={data_dir}", "--console"],
                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (run_dir / "mysql-init.log").write_bytes(init.stdout or b"")
        if init.returncode != 0:
            raise RuntimeError("mysqld initialize failed")

        mysql_proc = spawn(mysqld, ["--no-defaults", f"--datadir={data_dir}",
                                    "--bind-address=127.0.0.1", f"--port={mysql_port}",
                                    "--mysqlx=OFF", "--console"],
                           "mysql-out.log", "mysql-error.log")
        redis_proc = spawn(redis_server, ["--bind", "127.0.0.1", "--port", str(redis_port),
                                          "--requirepass", nonce, "--save", "", "--appendonly", "no",
                                          "--dir", str(run_dir)],
                           "redis-out.log", "redis-error.log")
        wait_port(mysql_port)
        wait_port(redis_port)

        # Verify we are talking to OUR datadir before creating anything.
        actual = subprocess.run([str(mysql), "--no-defaults", "--host=127.0.0.1", f"--port={mysql_port}",
                      "--user=root", "--batch", "--skip-column-names",
                      "--execute=SELECT @@datadir"],
                     stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        got = (actual.stdout or b"").decode().strip()
        if actual.returncode != 0 or os.path.normcase(os.path.normpath(got)) != \
                os.path.normcase(os.path.normpath(str(data_dir))):
            raise RuntimeError(f"isolated MySQL identity could not be verified: {got}")

        # Same bootstrap as scripts/acceptance.ps1: only CREATE TABLE statements.
        schema = (ROOT / "sql" / "create_vision_space.sql").read_text(encoding="utf-8")
        tables = re.findall(r"(?ms)^CREATE TABLE .*?^\)[^\r\n]*;", schema)
        if not tables:
            raise RuntimeError("no CREATE TABLE statements found")
        ddl = "\n".join(tables)
        bootstrap = (
            f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n"
            f"CREATE USER 'vs_acceptance'@'%' IDENTIFIED BY '{nonce}';\n"
            f"GRANT ALL ON {database}.* TO 'vs_acceptance'@'%';\n"
            f"USE {database};\n"
            "CREATE TABLE acceptance_instance (nonce varchar(32) NOT NULL);\n"
            f"INSERT INTO acceptance_instance VALUES ('{nonce}');\n"
            f"{ddl}\n"
        )
        boot_path = run_dir / "bootstrap.sql"
        boot_path.write_text(bootstrap, encoding="utf-8")
        boot = subprocess.run([str(mysql), "--no-defaults", "--host=127.0.0.1", f"--port={mysql_port}",
                    "--user=root", "--default-character-set=utf8mb4"],
                   input=bootstrap.encode("utf-8"), stdout=subprocess.PIPE,
                   stderr=subprocess.STDOUT)
        (run_dir / "bootstrap.log").write_bytes(boot.stdout or b"")
        if boot.returncode != 0:
            raise RuntimeError("isolated schema bootstrap failed")

        marker = subprocess.run([str(redis_cli), "-h", "127.0.0.1", "-p", str(redis_port), "-a", nonce,
                      "-n", "15", "SET", "acceptance:instance", nonce],
                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (run_dir / "redis-marker.log").write_bytes(marker.stdout or b"")
        if marker.returncode != 0:
            raise RuntimeError("redis marker creation failed")

        env = os.environ.copy()
        env["VS_ACCEPTANCE_NONCE"] = nonce
        env["VS_ACCEPTANCE_MYSQL_PORT"] = str(mysql_port)
        env["VS_ACCEPTANCE_REDIS_PORT"] = str(redis_port)
        env["MYSQL_PWD"] = ""
        env["npm_config_cache"] = str(Path(tempfile.gettempdir()) / "npm-cache-vs")
        reports = Path(args.reports) if args.reports else run_dir / "surefire"
        mvn_exe = "mvn.cmd" if os.name == "nt" else "mvn"

        if args.full:
            # scripts/acceptance.ps1: clean test-compile, build a test classpath, then start a
            # real second JVM that shares this isolated MySQL/Redis and hands back its port.
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
            second_out = (run_dir / "second-instance-out.log").open("wb")
            second_err = (run_dir / "second-instance-error.log").open("wb")
            second = subprocess.Popen(
                [str(java_bin), "-cp", classpath,
                 "com.yuwen.visionspace.acceptance.VipSecondInstanceMain", str(ready_file)],
                cwd=str(ROOT), env=env, stdout=second_out, stderr=second_err,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            owned.append((second, second_out, second_err))
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
            # On Windows `mvn` is a shell script; subprocess needs the .cmd entrypoint.
            cmd = [mvn_exe, "-B", "--no-transfer-progress", f"-Dtest={args.test}",
                   f"-Dacceptance.reportsDirectory={reports}", "test"]
        print(f"[drill] {' '.join(cmd)}", flush=True)
        result = subprocess.run(cmd, cwd=str(ROOT), env=env)
        exit_code = result.returncode
        print(f"[drill] RESULT exit={exit_code} logs={run_dir} reports={reports}", flush=True)
    except Exception as exc:  # noqa: BLE001 - report and keep cleanup
        print(f"[drill] RUNNER_ERROR: {exc}", flush=True)
        exit_code = 99
    finally:
        # Ask the second JVM to exit on its own (stop-file handshake), then MySQL to
        # shut down cleanly, then force-stop anything still alive.
        try:
            (run_dir / "second-instance.port.stop").write_text("stop", encoding="utf-8")
        except Exception:
            pass
        try:
            subprocess.run([str(mysql), "--no-defaults", "--host=127.0.0.1", f"--port={mysql_port}",
                 "--user=root", "--execute=SHUTDOWN"], timeout=20,
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception:
            pass
        for proc, out, err in owned:
            try:
                proc.wait(timeout=20)
            except Exception:
                proc.kill()
            out.close()
            err.close()
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
