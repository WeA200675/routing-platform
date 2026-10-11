#!/usr/bin/env python3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "release" / "verify_candidate_workflow_coverage.py"

result = subprocess.run([sys.executable, str(SCRIPT)], cwd=ROOT, text=True, capture_output=True)
if result.returncode:
    sys.stderr.write(result.stdout + result.stderr)
    raise SystemExit(result.returncode)
if "required release paths covered" not in result.stdout:
    raise SystemExit("workflow coverage verifier did not emit success marker")
print(result.stdout.strip())
