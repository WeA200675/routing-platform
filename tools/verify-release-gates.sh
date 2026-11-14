#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL="$ROOT/platform/android/app/src/main/assets/g620/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
JNI="$ROOT/platform/android/app/src/main/cpp/social_ai_llama_jni.cpp"
CMAKE="$ROOT/platform/android/app/src/main/cpp/CMakeLists.txt"

fail() { echo "RELEASE_GATE_FAILED: $*" >&2; exit 1; }

[[ -f "$MODEL" ]] || fail "model missing: $MODEL"
[[ -f "$JNI" ]] || fail "JNI source missing: $JNI"
[[ -f "$CMAKE" ]] || fail "native runtime build definition missing: $CMAKE"

if grep -q 'g_loaded = false' "$JNI"; then
  fail "placeholder/fail-closed local AI JNI implementation detected"
fi

if ! grep -q 'llama_model_load_from_file' "$JNI" || ! grep -q 'llama_decode' "$JNI"; then
  fail "real llama.cpp model loading or decoding is not linked"
fi

if ! grep -q 'GIT_TAG b3561' "$CMAKE"; then
  fail "llama.cpp source is not pinned to the reviewed revision"
fi

echo "RELEASE_GATES_PASSED"
