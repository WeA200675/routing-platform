#!/usr/bin/env python3
import argparse, json, re
from pathlib import Path

HEX40=re.compile(r"^[0-9a-f]{40}$")
HEX64=re.compile(r"^[0-9a-f]{64}$")

def valid_record(r):
    return (
        HEX40.fullmatch(r.get("candidateSourceSha","")) is not None and
        HEX64.fullmatch(r.get("artifactSha256","")) is not None and
        isinstance(r.get("platform"), str) and bool(r["platform"].strip()) and
        isinstance(r.get("platformVersion"), str) and bool(r["platformVersion"].strip()) and
        r.get("result") in {"pass","fail"} and
        isinstance(r.get("physicalDevice"), bool) and r["physicalDevice"] is True
    )

parser=argparse.ArgumentParser()
parser.add_argument("record")
parser.add_argument("--candidate-sha", required=True)
parser.add_argument("--artifact-sha256", required=True)
args=parser.parse_args()
r=json.loads(Path(args.record).read_text())
if not valid_record(r):
    raise SystemExit("invalid physical-device acceptance record")
if r["candidateSourceSha"] != args.candidate_sha.lower():
    raise SystemExit("device record candidate SHA mismatch")
if r["artifactSha256"] != args.artifact_sha256.lower():
    raise SystemExit("device record artifact digest mismatch")
if r["result"] != "pass":
    raise SystemExit("physical-device acceptance did not pass")
print(json.dumps({"platform":r["platform"],"platformVersion":r["platformVersion"],"result":"pass"},sort_keys=True))
