# Training pipeline — implementation gate (November 2026)

## Delivered on this branch

- `backend/training_job_store.py`: SQLite WAL transactional **fallback** store. It persists jobs before acknowledging enqueue, rejects conflicting duplicate IDs, enforces a bounded nonterminal backlog, and uses expiring lease tokens to recover interrupted work. It does **not** silently delete pending jobs.
- The store now refuses all replay completion (even a caller-supplied `verified: true`) until a trusted independent attestor is connected. SHA-256 evidence fields are reserved for the future verified execution pipeline.
- `backend/training_api.py` no longer increments replay or regression success counters for schema-valid events without a replay runner; valid events remain pending. Overview unknown metrics are `null`, and the diagnostic response separates historical unverified counts.
- Automated tests cover deduplication, queue capacity, restart, expired leases, blocked jobs, evidence requirements, and HTTP smoke behavior.

## Not yet connected

The new store is a standalone module, **not yet the production API persistence backend**. The existing API state journal and HTTP ingestion remain in place; do not mistake the new module for a completed deployment. No PostgreSQL adapter, MinIO evidence storage, Valhalla route replay, baseline comparison, LLM inference, or automatic promotion is connected.

## Release gates (all required)

1. Integrate a PostgreSQL transactional adapter and the SQLite fallback behind one interface; atomically acknowledge API events **only after** durable commit. Handle disk-full, DB outage, duplicate conflicts, and backpressure with explicit HTTP responses.
2. Implement an actual deterministic replay executor using captured route/trace inputs, map-data version and baseline; produce immutable evidence, verify results independently, and test negative cases.
3. Keep model suggestions advisory. Require explicit shadow/regression/canary gates and rollback before any navigation changes.
4. Replace fabricated data in *all* dashboard sections, not just overview. Migrate and label old unverified counters separately.
5. Run the CI suite and production-like fault-injection tests; snapshot PostgreSQL, MinIO, API state and configuration; verify rollback. Never copy `deploy/.env.local` or `deploy/training-api.env` into Git.

## Rollout

Work only on the GitHub draft PR until all gates pass. The Oracle VM's `fix/real-training-pipeline` worktree is a **different local branch**. Do not deploy this PR by overwriting its working files, and do not merge to `main` without green tests and an explicit migration plan.

## Milestone 3 — Recorded-trace regression (implemented as advisory only)

- `backend/trace_regression.py` compares recorded reference and candidate maneuvers with matching route, map and input identifiers.
- Wrong left/right direction, late voice cues, GPS deviation, and fallback usage are explicit failure conditions.
- Missing observations, duplicate IDs, inconsistent map versions and nonfinite measurements are rejected.
- The output has `verified=false` and `promotion_eligible=false` even when the trace comparison passes. **This is not a Valhalla replay** and cannot authorize a navigation change.
- All known hardcoded dashboard quality, knowledge, experiment, cloud and history metrics in `backend/training_api.py` have been replaced with empty or unknown values. Dashboard clients must gracefully render null and empty data.

## Important remaining risks

- The standalone job store's `complete()` rejects all completion until an independently verified Valhalla execution and trusted worker boundary are implemented.
- The SQLite opt-in API and legacy state journal currently coexist. Provider diagnostic aggregation in SQLite mode is not migrated; a migration/reconciliation strategy is mandatory.
- PostgreSQL adapter, Valhalla executor, evidence persistence, replay attestation, and LLM analysis are **not implemented**.
- Tests have been authored; GitHub Actions status and integration against the Oracle ARM environment have not been independently verified.

## Milestone 4 — Read-only Valhalla route replay adapter

- `backend/valhalla_replay.py` posts a strictly validated route fixture to a loopback-only Valhalla `/route` endpoint and compares returned maneuver directions to an explicitly supplied reference sequence.
- It emits deterministic SHA-256 hashes for the fixture, request and raw response, plus mismatch status. It does **not** modify navigation, train a model or automatically accept results.
- `verified=false` and `promotion_eligible=false` are mandatory until map tile hash, Valhalla binary/build, fixture provenance and reference-label provenance are independently attested.
- Unit tests use an injected fake transport and cover wrong turns, missing maneuvers, invalid coordinates, remote endpoint rejection and unavailable service. **They do not establish that a real Valhalla instance is installed or responds correctly on the Oracle VM.**
- Maneuver code classification follows the Valhalla enum and requires an integration test against the exact pinned Valhalla version before release.

### Remaining mandatory work

1. Pin and attest the Valhalla build, configuration and tile archive checksum, then execute integration tests on Oracle ARM with actual fixtures.
2. Build a trusted replay worker that stores immutable evidence and independently validates completion before releasing the currently locked `complete()` transition.
3. Add PostgreSQL transactional persistence and an explicit migration/rollback route from the legacy journal.
4. Add bounded, privacy-preserving LLM diagnosis as advisory output only, followed by shadow-mode evaluations.
5. Confirm GitHub Actions checks on the latest commit; earlier commits passing does not establish the newest commit passed.
