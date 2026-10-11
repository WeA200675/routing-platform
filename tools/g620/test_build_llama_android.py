#!/usr/bin/env python3
from pathlib import Path
s=Path(__file__).with_name("build_llama_android.py").read_text()
required=["ANDROID_ABI=arm64-v8a","ANDROID_PLATFORM=android-28","GGML_NATIVE=OFF","GGML_OPENMP=OFF","GGML_LLAMAFILE=OFF","LLAMA_OPENSSL=OFF","rev-parse"]
for token in required:
    assert token in s, token
assert "master" not in s and "main" not in s
