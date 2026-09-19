#!/usr/bin/env python3
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parent
lock = {}
for raw in (root / "candidate.lock").read_text().splitlines():
    line = raw.strip()
    if line and not line.startswith("#"):
        key, value = line.split("=", 1)
        lock[key] = value

with tempfile.TemporaryDirectory() as directory:
    d = Path(directory)
    model = {
        "runtimeCommitSha": lock["RUNTIME_REVISION"],
        "modelRevision": lock["MODEL_REVISION"],
        "modelSha256": lock["MODEL_SHA256"],
        "modelBytes": int(lock["MODEL_BYTES"]),
        "ggufFilename": lock["MODEL_ARTIFACT"],
    }
    native = {
        "runtimeCommitSha": lock["RUNTIME_REVISION"],
        "ndkVersion": lock["ANDROID_NDK"],
        "cmakeVersion": lock["CMAKE_VERSION"],
        "abi": "arm64-v8a",
        "artifactFilename": "libsocial_ai_llama.so",
        "artifactSha256": "2" * 64,
        "artifactBytes": 123,
    }
    (d / "model.json").write_text(json.dumps(model))
    (d / "native.json").write_text(json.dumps(native))
    cmd = [
        sys.executable, str(root / "create_release_evidence.py"),
        "--candidate-lock", str(root / "candidate.lock"),
        "--model-capture", str(d / "model.json"),
        "--native-capture", str(d / "native.json"),
        "--source-sha256", "3" * 64,
        "--output-dir", str(d / "out"),
    ]
    subprocess.run(cmd, check=True)
    sbom = (d / "out/g620-sbom.spdx.json").read_bytes()
    manifest = json.loads((d / "out/g620-release-manifest.json").read_text())
    assert manifest["runtimeRevision"] == lock["RUNTIME_REVISION"]
    assert manifest["modelSha256"] == lock["MODEL_SHA256"]
    assert manifest["runtimeArtifactSha256ByAbi"]["arm64-v8a"] == "2" * 64
    assert manifest["sbomSha256"] == hashlib.sha256(sbom).hexdigest()

    native["artifactFilename"] = "libsubstituted.so"
    (d / "native.json").write_text(json.dumps(native))
    assert subprocess.run(cmd).returncode != 0
