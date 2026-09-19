#!/usr/bin/env python3
"""Fail closed when a GitHub Actions workflow uses a floating action revision."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
FULL_SHA = re.compile(r"^[0-9a-f]{40}$")
USES = re.compile(r"^\s*-?\s*uses:\s*([^\s#]+)(?:\s+#.*)?$")


def main() -> int:
    failures: list[str] = []
    for workflow in sorted(WORKFLOWS.glob("*.yml")):
        for lineno, line in enumerate(workflow.read_text(encoding="utf-8").splitlines(), 1):
            match = USES.match(line)
            if not match:
                continue
            value = match.group(1)
            # Local actions are repository content and therefore bound to the checked-out commit.
            if value.startswith("./"):
                continue
            if "@" not in value:
                failures.append(f"{workflow.relative_to(ROOT)}:{lineno}: action has no revision: {value}")
                continue
            action, revision = value.rsplit("@", 1)
            if not FULL_SHA.fullmatch(revision):
                failures.append(
                    f"{workflow.relative_to(ROOT)}:{lineno}: {action} must use a full 40-char commit SHA, got {revision!r}"
                )
    if failures:
        print("Floating/unpinned GitHub Action revisions rejected:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("All external GitHub Actions are pinned to immutable 40-character commit SHAs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
