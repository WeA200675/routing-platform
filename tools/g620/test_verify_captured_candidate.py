#!/usr/bin/env python3
import json, subprocess, sys, tempfile
from pathlib import Path
root=Path(__file__).resolve().parent
lock={}
for raw in (root/"candidate.lock").read_text().splitlines():
    line=raw.strip()
    if line and not line.startswith("#"):
        k,v=line.split("=",1); lock[k]=v
record={"runtimeCommitSha":lock["RUNTIME_REVISION"],"modelRevision":"model-revision-123","modelSha256":"1"*64,"modelBytes":123,"ggufFilename":lock["MODEL_ARTIFACT"]}
with tempfile.TemporaryDirectory() as d:
    p=Path(d)/"record.json"; p.write_text(json.dumps(record))
    subprocess.run([sys.executable,str(root/"verify_captured_candidate.py"),str(p)],check=True)
    record["ggufFilename"]="substituted.gguf"; p.write_text(json.dumps(record))
    assert subprocess.run([sys.executable,str(root/"verify_captured_candidate.py"),str(p)]).returncode != 0
