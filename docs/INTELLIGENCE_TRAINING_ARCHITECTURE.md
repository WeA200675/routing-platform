# Connected traffic intelligence and AI training

## Components

The system has two separately operable applications sharing one private hub:

1. `backend/traffic_intelligence_hub.py` receives normalized events from configured sources, retains provenance, expires old events, filters events against a route corridor, and serves only current events. It also owns the approved knowledge base and example store.
2. `tools/ai_training_app.py` is the curation/export companion. It retrieves reviewed examples and emits validated JSONL for a separately pinned fine-tuning runner. Traffic, hazard, and route examples must reference approved knowledge items.

The Android app can query `POST /v1/traffic/route-events` with route geometry; it receives timestamped events or an explicit empty/unavailable response. Routing geometry, maneuver decisions, delays and reroutes remain owned by the deterministic route/data providers. AI training and inference do not create these facts.

## Start locally

```sh
HUB_API_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')" \
TRAFFIC_PROVIDER_ALLOWLIST=die-autobahn,asfinag \
python3 -m backend.traffic_intelligence_hub
```

For automatic ingestion, provide a JSON array in `TRAFFIC_FEEDS_JSON` with `id`, allowlisted `provider`, HTTPS `url`, `attribution` and optional `intervalSeconds` (30–1800). For example: `[{"id":"autobahn-feed","provider":"die-autobahn","url":"https://YOUR-CONFIGURED-FEED/events.geojson","attribution":"Die Autobahn","intervalSeconds":120}]`. Optional `authorizationEnv` names a server environment variable; secret values never go in the JSON. The default bind is loopback. To expose the service, set `HUB_BIND_HOST=0.0.0.0`, use an API token of at least 32 characters, and place it behind authenticated TLS. Never put that token in the APK. Provider allowlist and source attribution are explicit; an empty allowlist means no feed is trusted and traffic status remains unavailable.

## API outline

- `POST /v1/traffic/events` — authenticated normalized provider batches, idempotent by provider/event ID.
- `POST /v1/traffic/route-events` — authenticated route-corridor lookup; only fresh (five-minute default), valid, nearby events are returned.
- `POST /v1/knowledge/items` and `GET /v1/knowledge/search?q=...` — approved, attributed knowledge.
- `POST /v1/training/examples` and authenticated `GET /v1/training/dataset` — human-curated training records and dataset handoff.
- `GET /v1/status` — whether a provider is configured, never a guess that traffic is clear.

The HTTPS poller accepts GeoJSON FeatureCollections with canonical properties. Other DATEX II/provider schemas must be mapped by a source-specific adapter and reviewed before allowlisting. Provider API credentials stay server-side. The poller supports up to 16 feeds and isolates failures so one provider cannot clear another provider's data. Feed coverage, update cadence and license terms remain source-specific.

## Training is staged, not self-reinforcing

The trainer validates provenance, knowledge references, duplicate IDs, size limits and coordinate-shaped content before export. It creates a reviewable dataset; it does not update the Android GGUF weights. Actual LoRA/fine-tuning requires a compatible source checkpoint, pinned trainer and hardware, followed by the existing candidate-integrity, safety and device evaluation gates. Generated answers are never fed back as ground truth. No training dataset contains raw route traces.

## Verification

```sh
python3 -m unittest backend/test_traffic_intelligence_hub.py
python3 tools/ai_training_app.py --help
```
