#!/usr/bin/env python3
"""Fail-closed verification of an extracted GitHub Android APK artifact.

Usage: python3 scripts/verify_android_apk_artifact.py DIRECTORY EXPECTED_COMMIT
Only stdlib is required. No APK is executed or installed.
"""
import argparse
import hashlib
import pathlib
import re
import sys


def verify(directory: pathlib.Path, expected_commit: str) -> str:
    directory = directory.resolve(strict=True)
    if not directory.is_dir():
        raise ValueError("artifact path is not a directory")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", expected_commit):
        raise ValueError("expected commit must be a full 40-character SHA")
    # Artifact extraction must not introduce symlinks or nested surprise files.
    entries = list(directory.iterdir())
    if any(item.is_symlink() or not item.is_file() for item in entries):
        raise ValueError("unexpected directory or symlink in artifact")
    apks = [item for item in entries if item.suffix.lower() == ".apk"]
    if len(apks) != 1:
        raise ValueError("exactly one APK is required")
    expected_names = {apks[0].name, "SHA256SUMS.txt", "SOURCE_COMMIT.txt"}
    if {item.name for item in entries} != expected_names:
        raise ValueError("artifact has missing or unexpected files")
    source = (directory / "SOURCE_COMMIT.txt").read_text(encoding="ascii").strip()
    if source.lower() != expected_commit.lower():
        raise ValueError("source commit does not match expected HEAD")
    checksum = (directory / "SHA256SUMS.txt").read_text(encoding="ascii")
    lines = checksum.splitlines()
    if len(lines) != 1:
        raise ValueError("exactly one checksum entry is required")
    match = re.fullmatch(r"([0-9a-fA-F]{64})  (.+\.apk)", lines[0])
    if not match or match.group(2) != apks[0].name:
        raise ValueError("checksum format or filename mismatch")
    digest = hashlib.sha256()
    with apks[0].open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    actual = digest.hexdigest()
    if actual.lower() != match.group(1).lower():
        raise ValueError("APK SHA-256 mismatch")
    return actual


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact_dir", type=pathlib.Path)
    parser.add_argument("expected_commit")
    args = parser.parse_args()
    try:
        digest = verify(args.artifact_dir, args.expected_commit)
    except (ValueError, OSError, UnicodeError) as exc:
        print(f"APK artifact verification FAILED: {exc}", file=sys.stderr)
        return 1
    print(f"APK artifact verified: sha256={digest}, commit={args.expected_commit}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
