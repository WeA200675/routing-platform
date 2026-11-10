#!/usr/bin/env python3
"""Production smoke check for the Training API and persisted state."""
import json
import os
import stat
import sys
import urllib.request

base = os.environ.get("TRAINING_API_BASE", "http://127.0.0.1:8787")
for path in ("/api/training/diagnostics", "/api/training/overview"):
    with urllib.request.urlopen(base + path, timeout=5) as response:
        payload = json.loads(response.read().decode())
        assert response.status == 200
        assert payload.get("schema_version") == "training-center-v1"

state_path = os.environ.get("TRAINING_STATE_PATH", "/var/lib/routing-training/api-state.json")
if os.path.exists(state_path):
    mode = stat.S_IMODE(os.stat(state_path).st_mode)
    if mode & 0o077:
        raise SystemExit(f"state file permissions too broad: {oct(mode)}")

print("training api healthcheck: PASS")
