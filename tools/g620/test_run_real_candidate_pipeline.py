#!/usr/bin/env python3
from pathlib import Path

text = Path(__file__).with_name("run_real_candidate_pipeline.sh").read_text()
for token in [
    "set -euo pipefail",
    "verify_candidate_lock.py",
    "fetch_pinned_sources.py",
    "prepare_candidate_artifacts.py",
    "SmolLM2-360M-Instruct-Q4_K_M.gguf",
    "not release-approved",
]:
    assert token in text, token
for forbidden in ["assembleRelease", "adb ", "curl ", "wget "]:
    assert forbidden not in text, forbidden
print("G6.20 real candidate pipeline remains pinned, fail-closed and pre-packaging.")
