#!/usr/bin/env bash
# End-to-end G6.20 benchmark candidate build. Network is used only for immutable, verified inputs.
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
MODEL="$(python3 -c 'from pathlib import Path; p=Path("tools/g620/candidate.lock"); lock=dict(line.strip().split("=",1) for line in p.read_text().splitlines() if line.strip() and not line.startswith("#")); print(lock["MODEL_ARTIFACT"])')"
python3 "$ROOT/tools/g620/prepare_candidate_artifacts.py" \
  --runtime-source "$INPUTS/llama.cpp" \
  --model "$INPUTS/$MODEL" \
  --ndk "$NDK" \
  --work-dir "$BUILD"

echo "Real G6.20 benchmark candidate inputs verified and native runtime built; not release-approved."
