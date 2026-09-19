#!/usr/bin/env python3
"""Validate a captured candidate artifact record against candidate.lock."""
import json
import sys
from pathlib import Path

root = Path(__file__).resolve().parent
lock = {}
for raw in (root / "candidate.lock").read_text().splitlines():
    line = raw.strip()
    if line and not line.startswith("#"):
        key, value = line.split("=", 1)
        lock[key] = value

record = json.loads(Path(sys.argv[1]).read_text())
checks = {
    "runtimeCommitSha": lock["RUNTIME_REVISION"],
    "modelRevision": lock["MODEL_REVISION"],
    "modelSha256": lock["MODEL_SHA256"],
    "modelBytes": int(lock["MODEL_BYTES"]),
    "ggufFilename": lock["MODEL_ARTIFACT"],
}
for key, expected in checks.items():
    if record.get(key) != expected:
        raise SystemExit(f"{key} does not match candidate.lock")
print("Captured artifact record matches the immutable candidate lock.")
