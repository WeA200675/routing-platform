#!/usr/bin/env python3
"""Prepare G6.20 candidate artifacts from exact pinned local inputs; never downloads or guesses."""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parent
p = argparse.ArgumentParser()
p.add_argument("--runtime-source", required=True)
p.add_argument("--model", required=True)
p.add_argument("--ndk", required=True)
p.add_argument("--work-dir", required=True)
a = p.parse_args()

lock = {}
for raw in (root / "candidate.lock").read_text().splitlines():
    line = raw.strip()
    if line and not line.startswith("#"):
        key, value = line.split("=", 1)
        lock[key] = value

source = Path(a.runtime_source).resolve()
model = Path(a.model).resolve()
work = Path(a.work_dir).resolve()
work.mkdir(parents=True, exist_ok=True)

head = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
if head.lower() != lock["RUNTIME_REVISION"].lower():
    raise SystemExit("runtime checkout does not match candidate.lock")
if model.name != lock["MODEL_ARTIFACT"]:
    raise SystemExit("model filename does not match candidate.lock")

model_hash = hashlib.sha256()
model_bytes = 0
with model.open("rb") as handle:
    while chunk := handle.read(1024 * 1024):
        model_hash.update(chunk)
        model_bytes += len(chunk)
if model_hash.hexdigest().lower() != lock["MODEL_SHA256"].lower():
    raise SystemExit("model bytes do not match candidate.lock SHA-256")
if model_bytes != int(lock["MODEL_BYTES"]):
    raise SystemExit("model bytes do not match candidate.lock size")

model_capture = work / "model-capture.json"
subprocess.run([
    sys.executable, str(root / "capture_candidate_artifact.py"),
    "--runtime-commit", lock["RUNTIME_REVISION"],
    "--model-revision", lock["MODEL_REVISION"],
    "--gguf", str(model),
    "--output", str(model_capture),
], check=True)

build_dir = work / "llama-android"
subprocess.run([
    sys.executable, str(root / "build_llama_android.py"),
    "--source", str(source),
    "--commit", lock["RUNTIME_REVISION"],
    "--ndk", str(Path(a.ndk).resolve()),
    "--build-dir", str(build_dir),
], check=True)

candidates = sorted(build_dir.rglob("libllama.so"))
if len(candidates) != 1:
    raise SystemExit(f"expected exactly one libllama.so, found {len(candidates)}")
print(json.dumps({
    "modelCapture": str(model_capture),
    "builtRuntime": str(candidates[0]),
    "runtimeCommitSha": lock["RUNTIME_REVISION"],
}, sort_keys=True))
