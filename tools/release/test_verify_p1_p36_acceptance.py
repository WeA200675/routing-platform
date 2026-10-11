#!/usr/bin/env python3
"""Self-test the structural P1-P36 audit and then execute it."""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / "tools" / "release" / "verify_p1_p36_acceptance.py"

def main() -> None:
    result = subprocess.run(
        [sys.executable, str(AUDIT)],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        sys.stderr.write(result.stdout + result.stderr)
        raise SystemExit(result.returncode)
    if "P1-P36 acceptance declarations: complete" not in result.stdout:
        raise SystemExit("acceptance audit did not emit its success marker")
    print(result.stdout.strip())

if __name__ == "__main__":
    main()
