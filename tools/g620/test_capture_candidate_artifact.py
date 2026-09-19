#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile
from pathlib import Path

script = Path(__file__).with_name("capture_candidate_artifact.py")
with tempfile.TemporaryDirectory() as d:
    root = Path(d)
    gguf = root / "candidate.gguf"
    gguf.write_bytes(b"GGUF-test-artifact")
    out = root / "record.json"
    subprocess.run([
        sys.executable, str(script), "--runtime-commit", "1" * 40,
        "--model-revision", "model-revision-123", "--gguf", str(gguf),
        "--output", str(out),
    ], check=True)
    record = json.loads(out.read_text())
    assert record["ggufFilename"] == "candidate.gguf"
    assert record["modelBytes"] == len(b"GGUF-test-artifact")
    assert len(record["modelSha256"]) == 64
