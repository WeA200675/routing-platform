#!/usr/bin/env python3
"""Verify that release-significant paths retrigger the unified candidate gate."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "g620-candidate.yml"

REQUIRED = [
    ".github/workflows/g620-candidate.yml",
    ".github/workflows/android-ci.yml",
    ".github/workflows/core-ci.yml",
    ".github/workflows/ios-ci.yml",
    ".github/workflows/real-routing-ci.yml",
    ".github/workflows/g6-final-device-gate.yml",
    "tools/g620/**",
    "tools/release/**",
    "platform/ios/**",
    "platform/shared/parity/**",
    "platform/android/app/src/main/java/org/routingplatform/app/MainActivity.kt",
    "platform/android/app/src/main/java/org/routingplatform/app/navigation/**",
    "platform/android/app/src/main/java/org/routingplatform/app/security/**",
    "platform/android/app/src/test/**",
    "docs/G*_ACCEPTANCE.md",
    "docs/P*_ACCEPTANCE.md",
    "backend/**",
]

def main() -> None:
    text = WORKFLOW.read_text(encoding="utf-8")
    missing = [entry for entry in REQUIRED if text.count(f"- {entry}") < 2]
    if missing:
        raise SystemExit(
            "candidate workflow coverage audit failed: missing push/PR coverage for "
            + ", ".join(missing)
        )
    print("candidate workflow coverage: required release paths covered for push and PR")

if __name__ == "__main__":
    main()
