#!/usr/bin/env python3
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parent
payload = b"reviewed-social-ai-native-artifact"
with tempfile.TemporaryDirectory() as directory:
    directory = Path(directory)
    artifact = directory / "libsocial_ai_llama.so"
    output = directory / "native.json"
    artifact.write_bytes(payload)
    subprocess.run([
        sys.executable, str(root / "capture_native_artifact.py"),
        "--runtime-commit", "a" * 40,
        "--ndk-version", "28.2.13676358",
        "--cmake-version", "3.22.1",
        "--abi", "arm64-v8a",
        "--artifact", str(artifact),
        "--output", str(output),
    ], check=True)
    record = json.loads(output.read_text())
    assert record["artifactFilename"] == "libsocial_ai_llama.so"
    assert record["artifactSha256"] == hashlib.sha256(payload).hexdigest()
    assert record["artifactBytes"] == len(payload)
