#!/usr/bin/env python3
"""Validate that the reviewed G6.20 candidate lock contains no floating inputs."""
from pathlib import Path
import re

lock = {}
for raw in Path(__file__).with_name("candidate.lock").read_text().splitlines():
    line = raw.strip()
    if not line or line.startswith("#"):
        continue
    key, value = line.split("=", 1)
    lock[key] = value

required = {
    "RUNTIME_REPOSITORY", "RUNTIME_REVISION", "RUNTIME_LICENSE",
    "MODEL_FAMILY", "MODEL_REPOSITORY", "MODEL_ARTIFACT", "MODEL_FORMAT", "MODEL_QUANTIZATION", "MODEL_LICENSE",
    "ANDROID_NDK", "CMAKE_VERSION",
}
missing = required - lock.keys()
if missing:
    raise SystemExit(f"candidate lock missing keys: {sorted(missing)}")

if not re.fullmatch(r"[0-9a-f]{40}", lock["RUNTIME_REVISION"]):
    raise SystemExit("runtime revision must be an immutable 40-character Git SHA")
if lock["RUNTIME_REPOSITORY"] != "https://github.com/ggml-org/llama.cpp":
    raise SystemExit("runtime repository differs from the reviewed candidate")
if lock["MODEL_FAMILY"] != "SmolLM2-360M-Instruct":
    raise SystemExit("model family differs from the selected integration candidate")
if lock["MODEL_REPOSITORY"] != "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF":
    raise SystemExit("model repository differs from the selected integration candidate")
if lock["MODEL_ARTIFACT"] != "smollm2-360m-instruct-q4_k_m.gguf":
    raise SystemExit("model artifact differs from the selected integration candidate")
if lock["RUNTIME_LICENSE"] != "MIT" or lock["MODEL_LICENSE"] != "Apache-2.0":
    raise SystemExit("candidate licenses differ from the reviewed permissive set")
if lock["MODEL_FORMAT"] != "GGUF" or lock["MODEL_QUANTIZATION"] != "Q4_K_M":
    raise SystemExit("model packaging differs from the reviewed candidate")
if lock["ANDROID_NDK"] != "28.2.13676358" or lock["CMAKE_VERSION"] != "3.22.1":
    raise SystemExit("candidate toolchain must match the repository-pinned Android toolchain")

for value in lock.values():
    if value.lower() in {"main", "master", "head", "latest", "stable", "nightly", "snapshot"}:
        raise SystemExit("floating candidate input rejected")

print("G6.20 candidate lock is immutable and matches the reviewed candidate envelope.")
