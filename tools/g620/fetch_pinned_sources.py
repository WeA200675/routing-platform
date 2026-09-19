#!/usr/bin/env python3
"""Fetch exact G6.20 release inputs into CI workspace and verify them before use."""
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
subprocess.run(["git", "clone", "--filter=blob:none", "--no-checkout", lock["RUNTIME_REPOSITORY"], str(runtime)], check=True)
subprocess.run(["git", "-C", str(runtime), "checkout", "--detach", lock["RUNTIME_REVISION"]], check=True)
head = subprocess.check_output(["git", "-C", str(runtime), "rev-parse", "HEAD"], text=True).strip()
if head.lower() != lock["RUNTIME_REVISION"].lower():
    raise SystemExit("fetched runtime revision mismatch")

model = work / lock["MODEL_ARTIFACT"]
url = lock["MODEL_REPOSITORY"] + "/resolve/" + lock["MODEL_REVISION"] + "/" + lock["MODEL_ARTIFACT"]
h = hashlib.sha256()
size = 0
with urllib.request.urlopen(url) as response, model.open("wb") as output:
    while chunk := response.read(1024 * 1024):
        output.write(chunk)
        h.update(chunk)
        size += len(chunk)
if h.hexdigest().lower() != lock["MODEL_SHA256"].lower():
    model.unlink(missing_ok=True)
    raise SystemExit("fetched model SHA-256 mismatch")
if size != int(lock["MODEL_BYTES"]):
    model.unlink(missing_ok=True)
    raise SystemExit("fetched model size mismatch")
print(runtime)
print(model)
