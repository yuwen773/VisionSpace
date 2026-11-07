"""Record version numbers only, never command output, environment values or paths."""
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path

versions = {"os": platform.system(), "arch": platform.machine(), "python": platform.python_version()}
for name, arguments in {"java": ["-version"], "mvn": ["-v"], "node": ["--version"],
                        "npm": ["--version"], "trivy": ["--version"], "docker": ["version", "--format", "{{.Server.Version}}"],
                        "pwsh": ["-NoLogo", "-NoProfile", "-Command", "$PSVersionTable.PSVersion.ToString()"]}.items():
    executable = shutil.which(name)
    if name == "trivy" and len(sys.argv) > 2:
        executable = sys.argv[2]
    if name == "java" and os.environ.get("JAVA_HOME"):
        executable = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    try:
        result = subprocess.run([executable, *arguments], capture_output=True, text=True, errors="replace", timeout=20)
        match = re.search(r"(?<![0-9])\d+\.\d+\.\d+(?:[._+\-][A-Za-z0-9]+)*", result.stdout + result.stderr)
        versions[name] = match.group() if result.returncode == 0 and match else "NOT_RUN"
    except (OSError, TypeError, subprocess.TimeoutExpired):
        versions[name] = "NOT_RUN"
target = Path(sys.argv[1])
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(versions, indent=2), encoding="utf-8")
