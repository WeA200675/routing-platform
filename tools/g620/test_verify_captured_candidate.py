#!/usr/bin/env python3
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

record = {
    "runtimeCommitSha": lock["RUNTIME_REVISION"],
    "modelRevision": lock["MODEL_REVISION"],
    "modelSha256": lock["MODEL_SHA256"],
    "modelBytes": int(lock["MODEL_BYTES"]),
    "ggufFilename": lock["MODEL_ARTIFACT"],
}
with tempfile.TemporaryDirectory() as directory:
    path = Path(directory) / "record.json"
    path.write_text(json.dumps(record))
    subprocess.run([sys.executable, str(root / "verify_captured_candidate.py"), str(path)], check=True)

    for key, bad in [
        ("ggufFilename", "substituted.gguf"),
        ("modelSha256", "0" * 64),
        ("modelRevision", "deadbee"),
        ("modelBytes", 1),
    ]:
        changed = dict(record)
        changed[key] = bad
        path.write_text(json.dumps(changed))
        result = subprocess.run([sys.executable, str(root / "verify_captured_candidate.py"), str(path)])
        assert result.returncode != 0, key
