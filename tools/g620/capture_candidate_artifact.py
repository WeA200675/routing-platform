#!/usr/bin/env python3
"""Create machine-readable immutable inputs from locally downloaded release artifacts."""
import argparse
import hashlib
import json
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("--runtime-commit", required=True)
p.add_argument("--model-revision", required=True)
p.add_argument("--gguf", required=True)
p.add_argument("--output", required=True)
a = p.parse_args()

if len(a.runtime_commit) != 40 or any(c not in "0123456789abcdefABCDEF" for c in a.runtime_commit):
    raise SystemExit("runtime commit must be a full 40-character Git SHA")
path = Path(a.gguf)
h = hashlib.sha256()
size = 0
with path.open("rb") as f:
    while chunk := f.read(1024 * 1024):
        h.update(chunk)
        size += len(chunk)
record = {
    "runtimeCommitSha": a.runtime_commit.lower(),
    "modelRevision": a.model_revision,
    "modelSha256": h.hexdigest(),
    "modelBytes": size,
    "ggufFilename": path.name,
}
Path(a.output).write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(json.dumps(record, sort_keys=True))
