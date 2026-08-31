#!/usr/bin/env python3
"""Minimal dependency-policy gate for Foundation 0.1.

This intentionally starts strict: dependencies listed here must be known open-source
(or public-domain) components. Proprietary platform SDKs may only appear in adapter
modules and must never become dependencies of core/.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_CORE_MARKERS = [
    "google_maps",
    "mapbox_access_token",
    "tomtom",
    "here_sdk",
    "proprietary_sdk",
]

violations = []
for path in (ROOT / "core").rglob("*"):
    if path.is_file():
        text = path.read_text(encoding="utf-8", errors="ignore").lower()
        for marker in FORBIDDEN_CORE_MARKERS:
            if marker in text:
                violations.append(f"{path.relative_to(ROOT)} contains forbidden marker: {marker}")

if violations:
    print("Open-source policy check FAILED")
    print("\n".join(violations))
    sys.exit(1)
print("Open-source policy check PASS")
