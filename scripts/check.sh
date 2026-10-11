#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$ROOT/scripts/check_open_source.py"
python3 "$ROOT/scripts/validate_proto_structure.py"
"$ROOT/scripts/build.sh"
