#!/usr/bin/env python3
"""Capture immutable evidence for a locally built G6.20 Android native runtime."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--runtime-commit", required=True)
parser.add_argument("--ndk-version", required=True)
parser.add_argument("--cmake-version", required=True)
parser.add_argument("--abi", required=True)
parser.add_argument("--artifact", required=True)
parser.add_argument("--output", required=True)
args = parser.parse_args()

if len(args.runtime_commit) != 40 or any(c not in "0123456789abcdefABCDEF" for c in args.runtime_commit):
    raise SystemExit("runtime commit must be a full 40-character Git SHA")
if args.abi != "arm64-v8a":
    raise SystemExit("only the reviewed arm64-v8a runtime is admitted")

artifact = Path(args.artifact)
digest = hashlib.sha256()
size = 0
with artifact.open("rb") as handle:
    while chunk := handle.read(1024 * 1024):
        digest.update(chunk)
        size += len(chunk)
if size <= 0:
    raise SystemExit("native artifact must not be empty")

record = {
    "runtimeCommitSha": args.runtime_commit.lower(),
    "ndkVersion": args.ndk_version,
    "cmakeVersion": args.cmake_version,
    "abi": args.abi,
    "artifactFilename": artifact.name,
    "artifactSha256": digest.hexdigest(),
    "artifactBytes": size,
}
Path(args.output).write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(json.dumps(record, sort_keys=True))
