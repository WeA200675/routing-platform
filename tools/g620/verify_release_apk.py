#!/usr/bin/env python3
"""Fail closed if an LLM native runtime is packaged before release evidence exists."""
import sys
import zipfile

apk = sys.argv[1]
allowed_native = {"librouting_platform_jni.so"}
with zipfile.ZipFile(apk) as archive:
    native_entries = sorted(
        name for name in archive.namelist()
        if name.startswith("lib/") and name.endswith(".so")
    )
unexpected = [
    name for name in native_entries
    if name.rsplit("/", 1)[-1] not in allowed_native
]
if unexpected:
    print("Unreviewed native libraries in release APK:", *unexpected, sep="\n  ")
    raise SystemExit(1)
print("Release APK native libraries are restricted to reviewed non-LLM entries:")
for name in native_entries:
    print(f"  {name}")
