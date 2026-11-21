#!/usr/bin/env python3
"""Build the local JNI adapter against the exact pinned llama.cpp checkout."""
import argparse
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--source", required=True)
parser.add_argument("--commit", required=True)
parser.add_argument("--ndk", required=True)
parser.add_argument("--build-dir", required=True)
parser.add_argument("--source-sha256", required=True)
args = parser.parse_args()
source = Path(args.source).resolve()
ndk = Path(args.ndk).resolve()
head = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
if head.lower() != args.commit.lower():
    raise SystemExit("runtime checkout does not match pinned llama.cpp commit")
root = Path(__file__).resolve().parent
build = Path(args.build_dir).resolve()
subprocess.run([
    "cmake", "-S", str(root / "native"), "-B", str(build),
    f"-DLLAMA_SRC={source}", f"-DSOCIAL_AI_RUNTIME_BUILD_ID={args.commit}",
    f"-DSOCIAL_AI_RUNTIME_ARTIFACT_SHA256={args.source_sha256}",
    f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
    "-DANDROID_ABI=arm64-v8a", "-DANDROID_PLATFORM=android-28", "-DCMAKE_BUILD_TYPE=Release",
], check=True)
subprocess.run(["cmake", "--build", str(build), "--target", "social_ai_llama", "-j2"], check=True)
artifacts = list(build.rglob("libsocial_ai_llama.so"))
if len(artifacts) != 1:
    raise SystemExit(f"expected exactly one libsocial_ai_llama.so, found {len(artifacts)}")
print(artifacts[0])
