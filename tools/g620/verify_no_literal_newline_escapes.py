#!/usr/bin/env python3
"""Reject accidental literal backslash-n tokens in YAML and suspicious source lines."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
violations = []

for base in (ROOT / ".github/workflows", ROOT / "tools/g620"):
    for path in base.rglob("*"):
        if not path.is_file() or path.suffix not in {".py", ".yml", ".yaml"}:
            continue
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if r"\n" not in line:
                continue
            stripped = line.strip()
            # Legitimate source strings may intentionally contain a newline escape.
            legitimate = (
                "sep=" in stripped
                or "write_text(" in stripped
                or "json.dumps(" in stripped
                or r"'\n'" in stripped
                or r'"\n"' in stripped
            )
            if path.suffix in {".yml", ".yaml"} or not legitimate:
                violations.append(f"{path.relative_to(ROOT)}:{number}")

if violations:
    raise SystemExit("suspicious literal newline escape(s): " + ", ".join(violations))
print("No suspicious literal newline escapes found.")
