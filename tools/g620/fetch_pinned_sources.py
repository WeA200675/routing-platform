#!/usr/bin/env python3
"""Fetch exact llama.cpp and Qwen inputs and verify all pinned bytes."""
import argparse
import hashlib
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument("--work-dir", required=True)
args = parser.parse_args()
lock = {}
for raw in (ROOT / "candidate.lock").read_text().splitlines():
    line = raw.strip()
    if line and not line.startswith("#"):
        key, value = line.split("=", 1)
        lock[key] = value
work = Path(args.work_dir).resolve()
work.mkdir(parents=True, exist_ok=True)
runtime = work / "llama.cpp"
if runtime.exists():
    raise SystemExit("runtime destination already exists; use a fresh work directory")
subprocess.run(["git", "init", "--quiet", str(runtime)], check=True)
subprocess.run(["git", "-C", str(runtime), "remote", "add", "origin", lock["RUNTIME_REPOSITORY"]], check=True)
subprocess.run(["git", "-C", str(runtime), "fetch", "--quiet", "--depth=1", "--no-tags", "origin", lock["RUNTIME_REVISION"]], check=True)
subprocess.run(["git", "-C", str(runtime), "checkout", "--quiet", "--detach", "FETCH_HEAD"], check=True)
head = subprocess.check_output(["git", "-C", str(runtime), "rev-parse", "HEAD"], text=True).strip()
if head.lower() != lock["RUNTIME_REVISION"].lower():
    raise SystemExit("fetched runtime revision mismatch")
model = work / lock["MODEL_ARTIFACT"]
url = f"{lock['MODEL_REPOSITORY']}/resolve/{lock['MODEL_REVISION']}/{lock['MODEL_ARTIFACT']}"
request = urllib.request.Request(url, headers={"User-Agent": "routing-platform-local-llm/1"})
digest = hashlib.sha256()
size = 0
try:
    with urllib.request.urlopen(request, timeout=90) as response, model.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
            digest.update(chunk)
            size += len(chunk)
except Exception:
    model.unlink(missing_ok=True)
    raise
if digest.hexdigest().lower() != lock["MODEL_SHA256"].lower() or size != int(lock["MODEL_BYTES"]):
    model.unlink(missing_ok=True)
    raise SystemExit("downloaded GGUF failed pinned SHA-256 or byte-size verification")
print(f"runtime={runtime} revision={head}")
print(f"model={model} bytes={size} sha256={digest.hexdigest()}")
