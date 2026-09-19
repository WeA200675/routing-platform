#!/usr/bin/env python3
"""Validate a captured candidate artifact record against candidate.lock."""
import json
import sys
from pathlib import Path

root=Path(__file__).resolve().parent
lock={}
for raw in (root/"candidate.lock").read_text().splitlines():
    line=raw.strip()
    if line and not line.startswith("#"):
        k,v=line.split("=",1); lock[k]=v
record=json.loads(Path(sys.argv[1]).read_text())
checks={
    "runtimeCommitSha": lock["RUNTIME_REVISION"],
    "ggufFilename": lock["MODEL_ARTIFACT"],
}
for key, expected in checks.items():
    if record.get(key) != expected:
        raise SystemExit(f"{key} does not match candidate.lock")
if not isinstance(record.get("modelRevision"),str) or len(record["modelRevision"]) < 7:
    raise SystemExit("captured model revision is missing or too short")
if not isinstance(record.get("modelSha256"),str) or len(record["modelSha256"]) != 64:
    raise SystemExit("captured model SHA-256 is invalid")
if not isinstance(record.get("modelBytes"),int) or record["modelBytes"] <= 0:
    raise SystemExit("captured model byte size is invalid")
print("Captured artifact record matches the immutable candidate lock.")
