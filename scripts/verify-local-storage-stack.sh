#!/usr/bin/env sh
set -eu
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ENV_FILE="$ROOT_DIR/deploy/.env.local"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.local.yml"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing deploy/.env.local. Copy deploy/.env.local.example first." >&2
  exit 2
fi

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config >/dev/null
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d
trap 'docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down' EXIT

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres pg_isready -U routing -d routing_training
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T minio mc ready local
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" run --rm storage-init
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres psql -U routing -d routing_training -c "SELECT id, max_bytes, reserved_headroom_bytes, warning_bytes FROM storage_config;"
echo "local storage stack verification passed"
