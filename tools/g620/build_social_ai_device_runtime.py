#!/usr/bin/env python3
import argparse, subprocess
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("--source", required=True)
p.add_argument("--commit", required=True)
p.add_argument("--ndk", required=True)
p.add_argument("--build-dir", required=True)
p.add_argument("--source-sha256", required=True)
a=p.parse_args()
source=Path(a.source).resolve()
head=subprocess.check_output(["git","-C",str(source),"rev-parse","HEAD"],text=True).strip()
if head.lower()!=a.commit.lower(): raise SystemExit("source checkout does not match pinned runtime commit")
root=Path(__file__).resolve().parent
cmd=["cmake","-S",str(root/"native"),"-B",a.build_dir,
 f"-DLLAMA_SRC={source}",f"-DSOCIAL_AI_RUNTIME_BUILD_ID={a.commit}",
 f"-DSOCIAL_AI_RUNTIME_ARTIFACT_SHA256={a.source_sha256}",
 f"-DCMAKE_TOOLCHAIN_FILE={Path(a.ndk)/'build/cmake/android.toolchain.cmake'}",
 "-DANDROID_ABI=arm64-v8a","-DANDROID_PLATFORM=android-28","-DCMAKE_BUILD_TYPE=Release"]
subprocess.run(cmd,check=True)
subprocess.run(["cmake","--build",a.build_dir,"--target","social_ai_llama","-j2"],check=True)
matches=list(Path(a.build_dir).rglob("libsocial_ai_llama.so"))
if len(matches)!=1: raise SystemExit(f"expected one libsocial_ai_llama.so, found {len(matches)}")
print(matches[0])
