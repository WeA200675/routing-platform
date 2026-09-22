#!/usr/bin/env python3
import argparse, json, re
from pathlib import Path

HEX40=re.compile(r"^[0-9a-f]{40}$")
HEX64=re.compile(r"^[0-9a-f]{64}$")

parser=argparse.ArgumentParser()
parser.add_argument("--manifest", required=True)
parser.add_argument("--distribution-state", required=True)
parser.add_argument("--android-device")
parser.add_argument("--ios-device")
parser.add_argument("--require-production-distribution", action="store_true")
args=parser.parse_args()

manifest=json.loads(Path(args.manifest).read_text())
dist=json.loads(Path(args.distribution_state).read_text())
sha=manifest.get("candidateSourceSha","")
apk=manifest.get("releaseApkSha256","")
if not HEX40.fullmatch(sha) or not HEX64.fullmatch(apk):
    raise SystemExit("GA blocked: incomplete immutable candidate identity")

missing=[]
if not args.android_device: missing.append("android physical acceptance")
if not args.ios_device: missing.append("ios physical acceptance")
if args.require_production_distribution:
    if not dist.get("productionSigned"): missing.append("production signing")
    if not dist.get("storePublished"): missing.append("store publication")
if missing:
    raise SystemExit("GA blocked: missing " + ", ".join(missing))

print(json.dumps({"candidateSourceSha":sha,"gaPromotionEligible":True},sort_keys=True))
