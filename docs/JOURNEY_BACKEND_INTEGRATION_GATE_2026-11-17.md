# Journey Intelligence Backend – Integration Gate

Status: **not production-ready**. All journey code is advisory-only; no route mutation.

## Implemented
- `backend/journey_break_service.py`: deterministic stop policy and strict detour limit.
- `backend/journey_valhalla_adapter.py`: original-route vs. via-stop full-route comparison, `durationS` from the repository's real exporter contract.
- `backend/journey_pipeline.py`: verified POI source → Valhalla adapter → policy.
- `backend/journey_offline_poi.py`: bounded offline index, exact map version and pinned SHA-256; raw OSM POIs cannot be promoted automatically.
- `backend/journey_api_contract.py`: strict schema validation (pure function, **not yet an HTTP endpoint**).
- `tools/test_journey_*.py`: isolated regression tests, wired into `training-backend-safety.yml`.

## Mandatory blockers before real recommendations
1. **Map-graph attestation**: route exporter currently provides `engineName`, `engineVersion` and `durationS`, but does **not** provide an independently verified `mapGraphVersion`. Adapter now requires the latter in **both** routing results. Until implemented, real routes are deliberately rejected. Do not fabricate this field from a client request, POI index, or constant.
2. **Audited POI publisher**: offline JSON needs independent motorway access, direction, legal exit/return and route-order checks, plus an independently distributed trusted SHA-256 digest. SHA-256 proves integrity, not truth. Do not infer attestations from OSM `toilets=yes` alone.
3. **Route-order semantics**: routes with existing via-points are rejected until the candidate can be inserted in its verified route position.
4. **API transport**: `journey_api_contract.py` is a validator only. Never expose client-supplied `verified` booleans as trusted evidence; route comparisons and POI lookup must be performed server-side.
5. **Real integration**: run against the real Valhalla exporter, authenticated service transport, correct map snapshot and a deliberately audited test POI.
6. **Release gate**: confirm GitHub CI on HEAD and Android device tests before enabling UI; keep feature flag disabled.

## Verification
```sh
PYTHONPATH=. python -m unittest -v \
  tools.test_journey_break_service \
  tools.test_journey_valhalla_adapter \
  tools.test_journey_api_contract \
  tools.test_journey_pipeline \
  tools.test_journey_offline_poi
```

## Fallback
Missing evidence, mismatched graph, missing index, unavailable Valhalla, invalid JSON or routing timeout → **no recommendation**. Existing navigation continues unchanged; no auto-reroute, no fabricated availability. Retain R1 P0 priority.
