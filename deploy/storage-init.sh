#!/bin/sh
set -eu

: "${MINIO_ROOT_USER:?MINIO_ROOT_USER is required}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required}"
: "${STORAGE_BUCKETS:?STORAGE_BUCKETS is required}"

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"

until mc ready local >/dev/null 2>&1; do
  echo "waiting for MinIO readiness"
  sleep 2
done

for bucket in $STORAGE_BUCKETS; do
  mc mb --ignore-existing "local/$bucket"
  mc anonymous set none "local/$bucket"
done

echo "storage buckets initialized"
