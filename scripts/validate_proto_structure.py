#!/usr/bin/env python3
"""Dependency-free structural checks for .proto sources.

This is intentionally not a replacement for protoc. It catches repository mistakes
before protoc is introduced: missing syntax/package declarations, missing local
imports and unbalanced braces.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
PROTO_ROOT = ROOT / "schemas" / "proto"
errors: list[str] = []

files = sorted(PROTO_ROOT.glob("*.proto"))
if not files:
    errors.append("no .proto files found")

for path in files:
    text = path.read_text(encoding="utf-8")
    if not re.search(r'^syntax\s*=\s*"proto3"\s*;', text, re.M):
        errors.append(f"{path.name}: missing proto3 syntax declaration")
    if not re.search(r'^package\s+[A-Za-z_][\w.]*\s*;', text, re.M):
        errors.append(f"{path.name}: missing package declaration")
    if text.count("{") != text.count("}"):
        errors.append(f"{path.name}: unbalanced braces")
    for imp in re.findall(r'^import\s+"([^"]+)"\s*;', text, re.M):
        if imp.startswith("google/protobuf/"):
            continue
        if not (PROTO_ROOT / imp).exists():
            errors.append(f"{path.name}: missing local import {imp}")

if errors:
    print("Proto structure check FAILED")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print(f"Proto structure check PASS ({len(files)} schemas)")
print("Note: protoc is not installed in this environment; compile validation is deferred.")
