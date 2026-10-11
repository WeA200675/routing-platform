#!/usr/bin/env python3
import hashlib, json, sys
from pathlib import Path

if len(sys.argv)!=3:
    raise SystemExit("usage: verify_dataset_provenance.py <dataset> <provenance.json>")
dataset=Path(sys.argv[1]); record=json.loads(Path(sys.argv[2]).read_text())
if not dataset.is_file(): raise SystemExit("dataset missing")
source=record.get("sourceId","")
version=record.get("version","")
expected=record.get("sha256","")
if not source.strip() or not version.strip(): raise SystemExit("dataset identity/version missing")
actual=hashlib.sha256(dataset.read_bytes()).hexdigest()
if expected != actual: raise SystemExit("dataset SHA-256 mismatch")
print(json.dumps({"sourceId":source,"version":version,"sha256":actual},sort_keys=True))
