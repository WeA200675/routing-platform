#!/usr/bin/env python3
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[2]
subprocess.run(
    [sys.executable, str(root / "tools/g620/verify_no_literal_newline_escapes.py")],
    cwd=root,
    check=True,
)
