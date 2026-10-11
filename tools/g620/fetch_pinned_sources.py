#!/usr/bin/env python3
"""Fetch exact G6.20 release inputs and verify bytes before any build uses them."""
import argparse
import hashlib
import subprocess
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parent
p = argparse.ArgumentParser()
p.add_argument("--work-dir", required=True)
a = p.parse_args()
lock = {}
for raw in (root / "candidate.lock").read_text().splitlines():
    line = raw.strip()
    if line and not line.startswith("#"):
        key, value = line.split("=", 1)
        lock[key] = value

work = Path(a.work_dir).resolve()
work.mkdir(parents=True, exist_ok=True)
runtime = work / "llama.cpp"
if runtime.exists():
    raise SystemExit("runtime destination already exists")

# Do not clone a moving default branch and then hope the reviewed commit is present.
# Initialise an empty repository and request exactly the immutable reviewed object.
subprocess.run(["git", "init", "--quiet", str(runtime)], check=True)
subprocess.run(["git", "-C", str(runtime), "remote", "add", "origin", lock["RUNTIME_REPOSITORY"]], check=True)
subprocess.run([
    "git", "-C", str(runtime), "fetch", "--quiet", "--depth=1", "--no-tags",
    "origin", lock["RUNTIME_REVISION"],
], check=True)
subprocess.run(["git", "-C", str(runtime), "checkout", "--quiet", "--detach", "FETCH_HEAD"], check=True)
head = subprocess.check_output(["git", "-C", str(runtime), "rev-parse", "HEAD"], text=True).strip()
if head.lower() != lock["RUNTIME_REVISION"].lower():
    raise SystemExit(f"fetched runtime revision mismatch: expected {lock['RUNTIME_REVISION']}, got {head}")

model = work / lock["MODEL_ARTIFACT"]
url = lock["MODEL_REPOSITORY"] + "/resolve/" + lock["MODEL_REVISION"] + "/" + lock["MODEL_ARTIFACT"]
request = urllib.request.Request(url, headers={"User-Agent": "routing-platform-g620-candidate/1"})
h = hashlib.sha256()
size = 0
try:
    with urllib.request.urlopen(request, timeout=60) as response, model.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
            h.update(chunk)
            size += len(chunk)
except Exception:
    model.unlink(missing_ok=True)
    raise
actual_sha = h.hexdigest().lower()
expected_sha = lock["MODEL_SHA256"].lower()
if actual_sha != expected_sha:
    model.unlink(missing_ok=True)
    raise SystemExit(f"fetched model SHA-256 mismatch: expected {expected_sha}, got {actual_sha}")
expected_size = int(lock["MODEL_BYTES"])
if size != expected_size:
    model.unlink(missing_ok=True)
    raise SystemExit(f"fetched model size mismatch: expected {expected_size}, got {size}")
print(f"runtime={runtime} revision={head}")
print(f"model={model} bytes={size} sha256={actual_sha}")
