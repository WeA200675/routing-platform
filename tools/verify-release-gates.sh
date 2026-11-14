#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL="$ROOT/platform/android/app/src/main/assets/g620/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
RUNTIME="$ROOT/platform/android/app/src/main/jniLibs/arm64-v8a/libsocial_ai_llama.so"
JNI="$ROOT/platform/android/app/src/main/cpp/social_ai_llama_jni.cpp"

fail() { echo "RELEASE_GATE_FAILED: $*" >&2; exit 1; }

[[ -f "$MODEL" ]] || fail "model missing: $MODEL"
[[ -f "$RUNTIME" ]] || fail "native runtime missing: $RUNTIME"
[[ -f "$JNI" ]] || fail "JNI source missing: $JNI"

if grep -q 'g_loaded = false' "$JNI" || grep -q 'return JNI_FALSE' "$JNI"; then
  fail "placeholder/fail-closed local AI JNI implementation detected"
fi

if grep -q 'return to_jstring(env, "")' "$JNI"; then
  fail "empty-generation placeholder detected"
fi

echo "RELEASE_GATES_PASSED"
