# Training Platform Integration Contract

## Repository decision

Keep the training platform in the existing routing-platform repository as isolated components. Do not duplicate the routing core. A separate repository can be considered later if the dashboard becomes an independently released product.

## App boundary

- POST /v1/events — route, GPS, maneuver, voice and feedback events
- GET /v1/context — current relevant knowledge
- POST /v1/dialogue — conversation request
- POST /v1/commands — investigate, simulate, explain, approve, reject, rollback
- GET /v1/releases/active — active compatible release
- GET /v1/health — service and freshness state

The app tolerates unavailable service, stale context, incompatible release, delayed response and empty knowledge. It always uses deterministic local routing as fallback.

## Contract requirements

Every request and event includes schema version, installation/session ID, timestamp, correlation ID, privacy classification and source/quality metadata where relevant. Commands are idempotent and auditable.

## Deployment shape

Initial target: local one-process or Docker Compose; free cloud stateless API plus scheduled worker and bounded database; asynchronous app event upload; dashboard read models for charts.

Charts must use projections or materialized summaries, not repeated scans of raw event tables.
