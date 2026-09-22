#!/usr/bin/env python3
"""Create deterministic G6.20 SBOM and release manifest from captured artifacts."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--candidate-lock", required=True)
parser.add_argument("--model-capture", required=True)
parser.add_argument("--native-capture", required=True)
parser.add_argument("--source-sha256", required=True)
parser.add_argument("--output-dir", required=True)
parser.add_argument("--candidate-source-sha", required=True)
args = parser.parse_args()

lock = {}
for raw in Path(args.candidate_lock).read_text().splitlines():
    line = raw.strip()
    if line and not line.startswith("#"):
        key, value = line.split("=", 1)
        lock[key] = value
model = json.loads(Path(args.model_capture).read_text())
native = json.loads(Path(args.native_capture).read_text())

expected = {
    "runtimeCommitSha": lock["RUNTIME_REVISION"],
    "modelRevision": lock["MODEL_REVISION"],
    "modelSha256": lock["MODEL_SHA256"],
    "modelBytes": int(lock["MODEL_BYTES"]),
    "ggufFilename": lock["MODEL_ARTIFACT"],
}
for key, value in expected.items():
    if model.get(key) != value:
        raise SystemExit(f"model capture mismatch: {key}")
if native.get("runtimeCommitSha") != lock["RUNTIME_REVISION"]:
    raise SystemExit("native capture runtime commit mismatch")
if native.get("abi") != "arm64-v8a" or native.get("artifactFilename") != "libsocial_ai_llama.so":
    raise SystemExit("native capture identity mismatch")
for digest in (args.source_sha256, native.get("artifactSha256", "")):
    if len(digest) != 64 or any(c not in "0123456789abcdefABCDEF" for c in digest):
        raise SystemExit("invalid SHA-256 evidence")

sbom = {
    "spdxVersion": "SPDX-2.3",
    "name": "routing-platform-g6.20-local-llm",
    "components": [
        {"id": "ggml-org/llama.cpp", "revision": lock["RUNTIME_REVISION"], "license": lock["RUNTIME_LICENSE"], "sourceSha256": args.source_sha256.lower()},
        {"id": lock["MODEL_FAMILY"], "revision": lock["MODEL_REVISION"], "license": lock["MODEL_LICENSE"], "sha256": lock["MODEL_SHA256"], "bytes": int(lock["MODEL_BYTES"])},
    ],
}
out = Path(args.output_dir)
out.mkdir(parents=True, exist_ok=True)
sbom_bytes = (json.dumps(sbom, indent=2, sort_keys=True) + "\n").encode()
(out / "g620-sbom.spdx.json").write_bytes(sbom_bytes)
sbom_sha = hashlib.sha256(sbom_bytes).hexdigest()
candidate_sha = args.candidate_source_sha.lower()
if len(candidate_sha) != 40 or any(c not in "0123456789abcdef" for c in candidate_sha):
    raise SystemExit("invalid immutable candidate source SHA")
manifest = {
    "candidateSourceSha": candidate_sha,
    "runtimeComponentId": "ggml-org/llama.cpp",
    "runtimeRevision": lock["RUNTIME_REVISION"],
    "modelId": lock["MODEL_FAMILY"],
    "modelRevision": lock["MODEL_REVISION"],
    "modelSha256": lock["MODEL_SHA256"],
    "runtimeArtifactSha256ByAbi": {"arm64-v8a": native["artifactSha256"].lower()},
    "sbomSha256": sbom_sha,
}
(out / "g620-release-manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
print(json.dumps(manifest, sort_keys=True))
