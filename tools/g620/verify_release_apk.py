#!/usr/bin/env python3
"""Fail closed unless a packaged Social AI runtime matches reviewed release evidence."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("apk")
p.add_argument("--release-manifest")
a = p.parse_args()

allowed_native = {
    "librouting_platform_jni.so",
    "libandroidx.graphics.path.so",
    "libmaplibre.so",
}
reviewed_name = "libsocial_ai_llama.so"
manifest = None
if a.release_manifest:
    manifest = json.loads(Path(a.release_manifest).read_text())

with zipfile.ZipFile(a.apk) as archive:
    model_asset = "assets/g620/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
    if manifest is not None:
        if model_asset not in archive.namelist():
            raise SystemExit("release APK lacks the pinned G6.20 model asset")
        model_expected = manifest.get("modelSha256")
        model_actual = hashlib.sha256(archive.read(model_asset)).hexdigest()
        if not model_expected or model_actual.lower() != model_expected.lower():
            raise SystemExit("packaged G6.20 model hash does not match release manifest")
    native_entries = sorted(
        name for name in archive.namelist()
        if name.startswith("lib/") and name.endswith(".so")
    )
    social_entries = [name for name in native_entries if name.rsplit("/", 1)[-1] == reviewed_name]
    if social_entries:
        if manifest is None:
            raise SystemExit("Social AI runtime is packaged without reviewed release manifest")
        if len(social_entries) != 1 or social_entries[0] != "lib/arm64-v8a/libsocial_ai_llama.so":
            raise SystemExit("Social AI runtime must be exactly one arm64-v8a artifact")
        expected = manifest.get("runtimeArtifactSha256ByAbi", {}).get("arm64-v8a")
        if not expected:
            raise SystemExit("release manifest lacks arm64-v8a Social AI artifact hash")
        actual = hashlib.sha256(archive.read(social_entries[0])).hexdigest()
        if actual.lower() != expected.lower():
            raise SystemExit("packaged Social AI runtime hash does not match release manifest")

unexpected = [
    name for name in native_entries
    if name.rsplit("/", 1)[-1] not in allowed_native | {reviewed_name}
]
if unexpected:
    print("Unexpected native libraries in release APK:", *unexpected, sep="\n  ")
    raise SystemExit(1)
print("Release APK native runtime and model satisfy the reviewed evidence policy.")
