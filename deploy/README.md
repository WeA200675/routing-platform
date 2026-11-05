# Local training storage stack

This stack provides the first runnable local backend for the AI training platform:

- PostgreSQL 16 for the transactional storage ledger.
- MinIO for S3-compatible training objects and backups.
- A one-shot initializer for private buckets.
- Healthchecks before initialization.

## Start

From the repository root:

    cp deploy/.env.local.example deploy/.env.local
    docker compose --env-file deploy/.env.local -f deploy/docker-compose.local.yml up -d
    docker compose --env-file deploy/.env.local -f deploy/docker-compose.local.yml ps

MinIO console: http://localhost:9001
MinIO S3 API: http://localhost:9000

## Stop

    docker compose --env-file deploy/.env.local -f deploy/docker-compose.local.yml down

Named volumes are retained. Removing them requires the explicit down -v command.

The NaviApp must never receive MinIO credentials. A future API will issue short-lived presigned URLs; PostgreSQL remains authoritative for admission decisions.
