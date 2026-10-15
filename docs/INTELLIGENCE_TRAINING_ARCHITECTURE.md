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

For automatic ingestion, configure HTTPS feeds in `TRAFFIC_FEEDS_JSON`. The built-in Autobahn adapter accepts official road/service endpoints and needs no token. This example polls warning, roadworks and closure for A92, A9 and A99:

```sh
export TRAFFIC_PROVIDER_ALLOWLIST=autobahn-germany
export TRAFFIC_FEEDS_JSON='[
  {"id":"de-a92-warning","provider":"autobahn-germany","format":"autobahn","url":"https://verkehr.autobahn.de/o/autobahn/A92/services/warning","attribution":"Die Autobahn GmbH des Bundes","intervalSeconds":120},
  {"id":"de-a92-roadworks","provider":"autobahn-germany","format":"autobahn","url":"https://verkehr.autobahn.de/o/autobahn/A92/services/roadworks","attribution":"Die Autobahn GmbH des Bundes","intervalSeconds":300},
  {"id":"de-a92-closure","provider":"autobahn-germany","format":"autobahn","url":"https://verkehr.autobahn.de/o/autobahn/A92/services/closure","attribution":"Die Autobahn GmbH des Bundes","intervalSeconds":120}
]'
```

Add one entry per road/service, up to 16 configured feeds. The adapter accepts only `verkehr.autobahn.de/o/autobahn/{A-road}/services/{warning|roadworks|closure}`; it rejects other hosts and paths. Warning IDs are SHA-256-derived before storage, coordinates are validated by the hub, and events expire after five minutes unless refreshed. Empty successful responses mark that feed current but do not erase other sources. For other providers, `format: "geojson"` supports canonical GeoJSON with optional `authorizationEnv`; credentials remain server-side. The default bind is loopback. To expose the service, set `HUB_BIND_HOST=0.0.0.0`, use an API token of at least 32 characters, and place it behind authenticated TLS. Never put that token in the APK. Provider allowlist and source attribution are explicit; an empty allowlist means no feed is trusted and traffic status remains unavailable.

## API outline

- `POST /v1/traffic/events` — authenticated normalized provider batches, idempotent by provider/event ID.
- `POST /v1/traffic/route-events` — authenticated route-corridor lookup; only fresh (five-minute default), valid, nearby events are returned.
- `POST /v1/knowledge/items` and `GET /v1/knowledge/search?q=...` — approved, attributed knowledge.
- `POST /v1/training/examples` and authenticated `GET /v1/training/dataset` — human-curated training records and dataset handoff.
- `GET /v1/status` — whether a provider is configured, never a guess that traffic is clear.

The HTTPS poller accepts canonical GeoJSON and the Germany Autobahn API's `warning`, `roadworks`, and `closure` JSON services. Other DATEX II/provider schemas require a source-specific adapter and verified access. Provider API credentials stay server-side. The poller supports up to 16 feeds and isolates failures so one provider cannot clear another provider's data. Feed coverage, update cadence and license terms remain source-specific.

## Training is staged, not self-reinforcing

The trainer validates provenance, knowledge references, duplicate IDs, size limits and coordinate-shaped content before export. It creates a reviewable dataset; it does not update the Android GGUF weights. Actual LoRA/fine-tuning requires a compatible source checkpoint, pinned trainer and hardware, followed by the existing candidate-integrity, safety and device evaluation gates. Generated answers are never fed back as ground truth. No training dataset contains raw route traces.

## Verification

```sh
python3 -m unittest backend/test_traffic_feed_poller.py backend/test_traffic_intelligence_hub.py tools/test_lora_runner.py
python3 tools/ai_training_app.py --help
python3 tools/lora_runner.py --help
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


## LoRA training runner

`tools/lora_runner.py` is an actual supervised LoRA training entry point for a separate GPU machine. It fetches the approved hub dataset over HTTPS, rejects invalid examples and GPS-like coordinates, requires at least 20 reviewed rows for the selected task, requires a full immutable Hugging Face model revision, and requires the exact installed LLaMA-Factory version. It checks CUDA and records device/VRAM information, dataset SHA-256, model revision, trainer version, seed and configuration in `training-manifest.json`. The run uses LoRA SFT; it does not run unattended. No prompts/outputs or route traces enter runtime metrics.

Install and validate a pinned LLaMA-Factory/PyTorch/CUDA environment on the same machine as the Hub. Configure the backend before starting it:

```sh
export HUB_LORA_ENABLED=true
export MODEL_REVISION=FULL_40_CHAR_MODEL_COMMIT
export TRAINER_EXPECTED_VERSION=EXACT_INSTALLED_VERSION
export LORA_OUTPUT_DIR=/secure/models/route-lora
```

Then open the dashboard, choose **Echtes LoRA auf diesem Hub-Rechner**, choose a task with at least 20 reviewed examples, and select **Anlegen & starten**. The Hub starts the isolated Python runner locally. If the training machine is separate, run the CLI below on that machine instead:

```sh
python3 tools/lora_runner.py --hub https://YOUR-HUB --token "$HUB_API_TOKEN" \
  --trainer-version EXACT_INSTALLED_VERSION --revision FULL_40_CHAR_MODEL_COMMIT \
  --preset speech_clarity --output-dir /secure/models/route-lora/speech-run
```

To validate the environment without changing model weights, add `--preflight`. The selected Qwen3.5-4B-Base candidate is Apache-2.0; choose and pass a pinned model commit and verify the installed LLaMA-Factory release supports that model architecture/template before starting. The current dashboard validation runs remain dataset checks; they are not GPU jobs. The separate LoRA runner writes its real training log and manifest to the training host's output directory. A trained adapter still must pass held-out route/speech safety evaluation and Android runtime compatibility review before app release.

## Feed source inventory (official sources checked 2026-10-15)

| Region | Official source found | Access / format | Integration status |
|---|---|---|---|
| Germany | [Autobahn API](https://autobahn.api.bund.dev/) / `verkehr.autobahn.de` | Public JSON services per motorway: warnings, roadworks, closures | Built-in adapter; configure up to 16 motorway/service URLs |
| Switzerland | [FEDRO/Open Transport Data](https://opentransportdata.swiss/en/cookbook/road-traffic-cookbook/traffic-situations/) | DATEX II SOAP pull; API key required; traffic situations | Catalogued; not polled until the key, terms and DATEX II parser are configured |
| Austria | [ASFINAG Traffic Data](https://contentportal.asfinag.at/) and [Austrian Mobility Data NAP](https://mobilitaetsdaten.gv.at/) | Portal signup; public RSS available after signup; other DATEX II packages require requested access | Catalogued; no scraping or credentials bundled |
| Luxembourg | [CITA DATEX II events](https://data.public.lu/en/datasets/cita-evenements-trafic-en-datex-ii/) | Public XML/DATEX II feed; CC0 metadata; event feed `cita.lu/info_trafic/datex/situationrecord` | Catalogued; XML/location decoding adapter not yet enabled |
| Finland | [Fintraffic Digitraffic](https://www.digitraffic.fi/en/) | Machine-readable open data; APIs and DATEX II, API/version changes are published | Catalogued; individual endpoint/schema must be selected and tested before activation |
| France | [Bison Futé](https://www.bison-fute.gouv.fr/traffic-information.html) | National live traffic map and official reuse terms; feed/API access is dataset-specific | Catalogued; no unverified map scraping |
| Denmark | [Danish Road Directorate](https://www.vejdirektoratet.dk/viden-og-data/planlaegning-og-drift/TrafficeventsandRoadworks-datafeed) | Traffic Events & Roadworks DATEX II feed | Catalogued; format/profile adapter not yet enabled |
| Sweden | [Trafikverket Open API](https://www.trafiklab.se/api/other-apis/trafikverket/) | API key-based, road situation events and disruptions | Catalogued; not activated without an API key and provider-specific parser |
| Belgium | [Transportdata.be](https://transportdata.be/) | National access point for RTTI/SRTI; feed access is dataset-specific | Catalogued; endpoint, schema and terms must be verified per dataset |
| Netherlands | [NDW National Access Point](https://ntm.ndw.nu/) | Official NAP catalog with dataset-specific access | Catalogued; no universal public URL to safely poll |
| Spain | [DGT National Access Point](https://nap.dgt.es/en/dataset) | Real-time standardized traffic data; DGT SRTI LOD/DATEX II resource listed under CC BY | Catalogued; LOD RDF/DATEX II location decoding not yet enabled |
| England | [Street Manager roadworks API](https://www.gov.uk/guidance/find-and-use-roadworks-data) | API account required; roadworks/permits and coordinates | Catalogued; key/account required; no adapter yet |
| Czechia | [NDIC DATEX II](https://tpeg.dopravniinfo.cz/) | National traffic events via DATEX II; access/profile varies | Catalogued; provider profile/location mapping not yet enabled |

This is a verified starter inventory, not a claim that every road authority, city, private operator, or commercial provider worldwide has been enumerated. Europe has distributed national and regional NAP catalogs; data may require registration, API keys, contracts, or separate licenses. Only sources with confirmed endpoint/schema and permission should be activated.