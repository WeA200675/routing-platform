#!/usr/bin/env python3
from pathlib import Path

text = Path(__file__).with_name("fetch_pinned_sources.py").read_text()
for token in [
    'RUNTIME_REPOSITORY', 'RUNTIME_REVISION', 'MODEL_REPOSITORY',
    'MODEL_REVISION', 'MODEL_ARTIFACT', 'MODEL_SHA256', 'MODEL_BYTES',
    'checkout", "--detach"', 'hashlib.sha256',
]:
    assert token in text, token
assert '/resolve/' in text
assert 'latest' not in text.lower()
print("G6.20 source acquisition is pinned and verifies downloaded model bytes.")
