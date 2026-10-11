#!/usr/bin/env python3
"""Fail-closed recipe verifier for a pinned llama.cpp Android source checkout."""
import argparse
import subprocess
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument("--source", required=True)
p.add_argument("--commit", required=True)
p.add_argument("--ndk", required=True)
p.add_argument("--build-dir", required=True)
a=p.parse_args()
if len(a.commit)!=40 or any(c not in "0123456789abcdefABCDEF" for c in a.commit):
    raise SystemExit("commit must be a full 40-character Git SHA")
source=Path(a.source).resolve()
head=subprocess.check_output(["git","-C",str(source),"rev-parse","HEAD"],text=True).strip()
if head.lower()!=a.commit.lower():
    raise SystemExit("source checkout does not match pinned runtime commit")
cmd=[
    "cmake","-S",str(source),"-B",a.build_dir,
    f"-DCMAKE_TOOLCHAIN_FILE={Path(a.ndk)/'build/cmake/android.toolchain.cmake'}",
    "-DANDROID_ABI=arm64-v8a","-DANDROID_PLATFORM=android-28",
    "-DCMAKE_BUILD_TYPE=Release","-DGGML_NATIVE=OFF","-DGGML_OPENMP=OFF",
    "-DGGML_LLAMAFILE=OFF","-DLLAMA_OPENSSL=OFF",
]
print(" ".join(cmd))
subprocess.run(cmd,check=True)
subprocess.run(["cmake","--build",a.build_dir,"--config","Release","-j2"],check=True)
