#!/usr/bin/env python3
"""Fetch the pinned llama.cpp source and build the isolated Android JNI runtime."""
import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMMIT = "b23701f77d47dad9de834d59ebfcbe25c9e8b46f"
SOURCE_SHA256 = "c443bb6cef2e1779592c6b854145f6ec5611c9fb8825284c2883424af94de77e"
parser = argparse.ArgumentParser()
parser.add_argument("--work-dir", required=True)
parser.add_argument("--ndk", required=True)
parser.add_argument("--output", required=True)
args = parser.parse_args()
work = Path(args.work_dir).resolve()
ndk = Path(args.ndk).resolve()
output = Path(args.output).resolve()
source = work / "llama.cpp"
if source.exists():
    head = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if head.lower() != COMMIT:
        raise SystemExit("Existing llama.cpp checkout is not the pinned revision; remove build/local-llm to refresh.")
else:
    work.mkdir(parents=True, exist_ok=True)
    subprocess.run(["git", "init", "--quiet", str(source)], check=True)
    subprocess.run(["git", "-C", str(source), "remote", "add", "origin", "https://github.com/ggml-org/llama.cpp"], check=True)
    subprocess.run(["git", "-C", str(source), "fetch", "--quiet", "--depth=1", "--no-tags", "origin", COMMIT], check=True)
    subprocess.run(["git", "-C", str(source), "checkout", "--quiet", "--detach", "FETCH_HEAD"], check=True)
subprocess.run([
    "python3", str(ROOT / "build_social_ai_device_runtime.py"),
    "--source", str(source), "--commit", COMMIT, "--ndk", str(ndk),
    "--build-dir", str(work / "android-build"), "--source-sha256", SOURCE_SHA256,
], check=True, stdout=subprocess.PIPE, text=True)
artifacts = list((work / "android-build").rglob("libsocial_ai_llama.so"))
if len(artifacts) != 1:
    raise SystemExit(f"expected exactly one native runtime, found {len(artifacts)}")
target = output / "arm64-v8a" / "libsocial_ai_llama.so"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(artifacts[0].read_bytes())
print(target)
