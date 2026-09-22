#!/usr/bin/env python3
import argparse, json, re
from pathlib import Path

HEX40=re.compile(r"^[0-9a-f]{40}$")
HEX64=re.compile(r"^[0-9a-f]{64}$")

def load_device(path, expected_platform, candidate_sha):
    r=json.loads(Path(path).read_text())
    required=(
        r.get("candidateSourceSha")==candidate_sha and
        HEX64.fullmatch(r.get("artifactSha256","")) is not None and
        r.get("platform")==expected_platform and
        isinstance(r.get("platformVersion"),str) and bool(r["platformVersion"].strip()) and
        r.get("physicalDevice") is True and
        r.get("result")=="pass"
    )
    if not required:
        raise SystemExit(f"GA blocked: invalid {expected_platform} physical acceptance")
    return r

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
if not args.android_device or not args.ios_device:
    raise SystemExit("GA blocked: Android and iOS physical acceptance are required")

android=load_device(args.android_device,"android",sha)
ios=load_device(args.ios_device,"ios",sha)
if android["artifactSha256"] != apk:
    raise SystemExit("GA blocked: Android device evidence artifact mismatch")

if args.require_production_distribution:
    if dist.get("productionSigned") is not True:
        raise SystemExit("GA blocked: missing production signing")
    if dist.get("storePublished") is not True:
        raise SystemExit("GA blocked: missing store publication")

print(json.dumps({
    "candidateSourceSha":sha,
    "androidArtifactSha256":android["artifactSha256"],
    "iosArtifactSha256":ios["artifactSha256"],
    "gaPromotionEligible":True,
},sort_keys=True))
