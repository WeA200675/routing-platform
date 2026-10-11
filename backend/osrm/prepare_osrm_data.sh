#!/usr/bin/env bash
set -euo pipefail
: "${1:?usage: prepare_osrm_data.sh <region.osm.pbf>}"
PBF="$(realpath "$1")"
OUT="$(cd "$(dirname "$0")" && pwd)/data"
mkdir -p "$OUT"
cp "$PBF" "$OUT/region.osm.pbf"
IMAGE="ghcr.io/project-osrm/osrm-backend:v5.27.1"
docker run --rm -v "$OUT:/data" "$IMAGE" osrm-extract -p /opt/car.lua /data/region.osm.pbf
docker run --rm -v "$OUT:/data" "$IMAGE" osrm-partition /data/region.osrm
docker run --rm -v "$OUT:/data" "$IMAGE" osrm-customize /data/region.osrm
echo "Prepared $OUT/region.osrm"
