#!/usr/bin/env python3
import json, subprocess, sys, tempfile
from pathlib import Path

tool=Path(__file__).resolve().parent/"verify_device_acceptance.py"
sha="a"*40
digest="b"*64

def run(record, *extra):
    with tempfile.TemporaryDirectory() as td:
        p=Path(td)/"record.json"
        p.write_text(json.dumps(record))
        return subprocess.run([
            sys.executable,str(tool),str(p),
            "--candidate-sha",sha,"--artifact-sha256",digest,*extra
        ], capture_output=True).returncode

valid={"candidateSourceSha":sha,"artifactSha256":digest,"platform":"android",
       "platformVersion":"test","physicalDevice":True,"result":"pass"}
assert run(valid,"--platform","android")==0
assert run({**valid,"platform":"linux"})!=0
assert run({**valid,"platform":"ios"},"--platform","android")!=0
assert run({**valid,"physicalDevice":False})!=0
assert run({**valid,"result":"fail"})!=0
assert run({**valid,"candidateSourceSha":"c"*40})!=0
assert run({**valid,"artifactSha256":"d"*64})!=0
