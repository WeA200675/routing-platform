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


## Dashboard and measured training checks

Start the hub and open `http://127.0.0.1:8090/dashboard`. The dashboard requires the hub bearer token for reads and writes; it retains the token in browser session storage only. The dashboard is served by the same process, so keep the hub loopback-bound unless it is behind authenticated TLS.

- `GET /v1/dashboard` — authenticated aggregate status, selected local model id, and the latest 50 persisted training runs.
- `GET /v1/traffic/events` — authenticated stored traffic events for the dashboard charts.
- `GET /v1/training/runs`, `POST /v1/training/runs`, `POST /v1/training/runs/{id}/start` — persistent run lifecycle. A run verifies the selected curated examples, source approvals and duplicate IDs, then records counts. It does not modify model weights; its quality score is intentionally null.
- The page can curate examples and attributed/licensed knowledge items using the existing APIs. Traffic or route examples still require approved knowledge references.
- The optional Bitcoin panel is a browser-only synthetic visualization. It performs no proof-of-work, wallet operation, CPU/GPU mining or network activity.

The current Android runtime uses a pinned Qwen2.5 1.5B Instruct Q4_K_M model for compact local responses. It is not the training model. Qwen3.5-4B Base is a candidate to evaluate for separate LoRA training after choosing a pinned revision, validating dataset rights, and providing a compatible GPU trainer. Never present dataset validation progress as model training progress. A deployable adapter requires independent evaluation and Android compatibility checks.


## Privacy-preserving runtime metrics

Authenticated clients may report `POST /v1/metrics/inference` with `modelId`, `task`, `outcome` (`success`, `fallback`, or `error`) and `elapsedMillis`. The hub deliberately ignores prompt, response, remembered preferences, and route coordinates. Metric IDs are idempotent, retention is capped at 90 days and 100,000 rows. The dashboard reports counts, success rate, p95 successful latency, and counts grouped by task over the last 30 days. Technical success is not a human quality score.

The Android runtime already defines local inference timing metadata, but the production Android app does not yet authenticate to and upload this hub endpoint. Until that opt-in client transport exists, the dashboard correctly displays “no measurement data”; it does not fabricate metrics. Do not put the hub bearer token into the APK. A future device connection needs a pairing/device-token flow or a local-only export/import path.
