#!/usr/bin/env python3
"""Static safety contract for the real candidate preparation orchestrator."""
from pathlib import Path

text = Path(__file__).with_name("prepare_candidate_artifacts.py").read_text()
required = [
    'candidate.lock',
    'rev-parse',
    'RUNTIME_REVISION',
    'MODEL_ARTIFACT',
    'MODEL_SHA256',
    'MODEL_BYTES',
    'capture_candidate_artifact.py',
    'build_llama_android.py',
    'expected exactly one libllama.so',
]
for token in required:
    assert token in text, token
for forbidden in ['requests.', 'urllib.request', 'curl', 'wget', 'huggingface_hub']:
    assert forbidden not in text, forbidden
print("G6.20 candidate preparation remains local-input-only and fail-closed.")
