#!/usr/bin/env bash
# End-to-end G6.20 candidate build. Network is used only for immutable, verified inputs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${1:?usage: run_real_candidate_pipeline.sh WORK_DIR ANDROID_NDK_HOME}"
NDK="${2:?usage: run_real_candidate_pipeline.sh WORK_DIR ANDROID_NDK_HOME}"
INPUTS="$WORK/inputs"
BUILD="$WORK/build"
rm -rf "$INPUTS" "$BUILD"
mkdir -p "$WORK"

python3 "$ROOT/tools/g620/verify_candidate_lock.py"
python3 "$ROOT/tools/g620/fetch_pinned_sources.py" --work-dir "$INPUTS"
python3 "$ROOT/tools/g620/prepare_candidate_artifacts.py" \
  --runtime-source "$INPUTS/llama.cpp" \
  --model "$INPUTS/SmolLM2-360M-Instruct-Q4_K_M.gguf" \
  --ndk "$NDK" \
  --work-dir "$BUILD"

# This script deliberately stops before APK packaging. Packaging remains forbidden
# until native artifact capture, release evidence and APK hash verification approve it.
echo "Real G6.20 candidate inputs verified and native runtime built; not release-approved."
