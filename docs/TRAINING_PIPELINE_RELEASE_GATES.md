# Training pipeline — implementation gate (November 2026)

## Delivered on this branch

- `backend/training_job_store.py`: SQLite WAL transactional **fallback** store. It persists jobs before acknowledging enqueue, rejects conflicting duplicate IDs, enforces a bounded nonterminal backlog, and uses expiring lease tokens to recover interrupted work. It does **not** silently delete pending jobs.
- A job can reach `passed` or `failed` only with a nonempty evidence blob, structured result, matching decision, and active lease. The store records an SHA-256 digest. **The caller is not yet a trusted replay verifier**; `verified: true` is a contract assertion, not cryptographic proof of navigation correctness.
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
