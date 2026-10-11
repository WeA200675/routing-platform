#!/usr/bin/env python3
"""Fail-closed structural audit for the normative P1-P36 acceptance declarations."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]

SOURCES = [
    ROOT / "MILESTONES.md",
    ROOT / "docs" / "P10_P18_ACCEPTANCE.md",
    ROOT / "docs" / "P19_P27_ACCEPTANCE.md",
    ROOT / "docs" / "P28_P36_ACCEPTANCE.md",
]

def fail(message: str) -> None:
    raise SystemExit(f"P1-P36 acceptance audit failed: {message}")

def main() -> None:
    for path in SOURCES:
        if not path.is_file():
            fail(f"missing normative source: {path.relative_to(ROOT)}")

    text = "\n".join(path.read_text(encoding="utf-8") for path in SOURCES)

    missing = []
    duplicated = []
    for milestone in range(1, 37):
        # Match the milestone identifier itself, not incidental numbers.
        count = len(re.findall(rf"(?<![A-Za-z0-9])P{milestone}(?![0-9])", text))
        if count == 0:
            missing.append(f"P{milestone}")
        # Multiple mentions are normal in explanatory evidence sections, so
        # duplicates are not rejected. We only require complete declaration.

    if missing:
        fail("missing milestone declarations: " + ", ".join(missing))

    required_files = [
        ".github/workflows/android-ci.yml",
        ".github/workflows/core-ci.yml",
        ".github/workflows/ios-ci.yml",
        ".github/workflows/g620-candidate.yml",
        "tools/release/verify_ga_promotion.py",
        "tools/release/verify_distribution_state.py",
        "tools/release/verify_device_acceptance.py",
    ]
    absent = [p for p in required_files if not (ROOT / p).is_file()]
    if absent:
        fail("missing release/acceptance boundaries: " + ", ".join(absent))

    p28_36 = (ROOT / "docs" / "P28_P36_ACCEPTANCE.md").read_text(encoding="utf-8")
    external_markers = [
        "physical",
        "production signing",
        "store publication",
        "cannot pass GA",
    ]
    for marker in external_markers:
        if marker.lower() not in p28_36.lower():
            fail(f"missing explicit external-evidence boundary: {marker}")

    print("P1-P36 acceptance declarations: complete; external boundaries remain explicit")

if __name__ == "__main__":
    main()
