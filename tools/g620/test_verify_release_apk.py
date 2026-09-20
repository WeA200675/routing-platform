#!/usr/bin/env python3
import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

tool = Path(__file__).with_name("verify_release_apk.py")
payload = b"reviewed-social-ai-runtime"\nmodel = b"pinned-test-model"
with tempfile.TemporaryDirectory() as directory:
    d = Path(directory)
    apk = d / "candidate.apk"
    manifest = d / "manifest.json"
    with zipfile.ZipFile(apk, "w") as z:
        z.writestr("lib/arm64-v8a/librouting_platform_jni.so", b"nav")
        z.writestr("lib/arm64-v8a/libsocial_ai_llama.so", payload)\n        z.writestr("assets/g620/SmolLM2-360M-Instruct-Q4_K_M.gguf", model)
    manifest.write_text(json.dumps({
        "runtimeArtifactSha256ByAbi": {
            "arm64-v8a": hashlib.sha256(payload).hexdigest()
        }
    }))
    assert subprocess.run([sys.executable, str(tool), str(apk)]).returncode != 0
    assert subprocess.run([
        sys.executable, str(tool), str(apk), "--release-manifest", str(manifest)
    ]).returncode == 0
    manifest.write_text(json.dumps({
        "runtimeArtifactSha256ByAbi": {"arm64-v8a": "0" * 64}
    }))
    assert subprocess.run([
        sys.executable, str(tool), str(apk), "--release-manifest", str(manifest)
    ]).returncode != 0
print("G6.20 release APK runtime evidence gate passed.")
