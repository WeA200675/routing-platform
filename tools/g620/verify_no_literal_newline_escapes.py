#!/usr/bin/env python3
"""Reject accidental literal backslash-n sequences in executable G6.20 text files."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGETS = [
    ROOT / "tools/g620",
    ROOT / ".github/workflows",
]
SUFFIXES = {".py", ".yml", ".yaml", ".kt", ".kts", ".cpp", ".h", ".md"}
violations = []
for base in TARGETS:
    for path in base.rglob("*"):
        if path.is_file() and path.suffix in SUFFIXES:
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if r"\n" in line and not any(token in line for token in ('"\\n"', "'\\n'", "append('\\n')", 'append("\\n")')):
                    violations.append(f"{path.relative_to(ROOT)}:{number}")
if violations:
    raise SystemExit("suspicious literal newline escape(s): " + ", ".join(violations))
print("No suspicious literal newline escapes found.")
