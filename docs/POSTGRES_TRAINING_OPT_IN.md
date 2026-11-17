# PostgreSQL training queue (opt-in; not production enabled)

The default remains SQLite when `TRAINING_JOB_DB_PATH` is set, and legacy in-memory handling when no job store is configured. PostgreSQL is selected **only** with:

```sh
export TRAINING_JOB_BACKEND=postgres
export TRAINING_POSTGRES_DSN='postgresql://training_user:REDACTED@127.0.0.1:5432/training'
python3 -m pip install 'psycopg[binary]>=3,<4'
python3 -m backend.training_api
```

Do not commit real DSNs, credentials or logs containing them. PostgreSQL failures abort startup rather than silently redirecting data into SQLite. This adapter creates the `training_jobs` table in the selected database and does **not** migrate SQLite events.

## Validation

1. Use a fresh, isolated **disposable** PostgreSQL database, never the Oracle production database.
2. Run `TRAINING_TEST_POSTGRES_DSN=postgresql://... python3 -m unittest -v tools.test_postgres_training_job_store`.
3. Run existing `python3 -m unittest -v tools.test_training_job_store` for SQLite parity.
4. Run the repository CI suites and API integration checks before enabling PostgreSQL.
5. Verify restart, simultaneous claims, exhausted leases, 256-item batches, conflicting IDs, full capacity, network disconnect and backup/restore with additional isolated integration tests. These gates are **not yet all implemented**.
6. Keep `TRAINING_JOB_BACKEND=sqlite` until migration and rollback are separately tested. Do not switch a populated deployment without backup, data comparison and explicit approval.

## Security and known gaps

- API write endpoints still require authentication, authorization and CORS hardening before external exposure.
- No trusted replay attestor is wired: `complete()` intentionally raises.
- No automatic migration, Android outbox or production rollout is included.
- PostgreSQL schema setup needs a narrowly scoped database role; use a migration role for deployment after schema stabilization.
- `FOR UPDATE SKIP LOCKED` permits parallel claims; capacity writes use a transaction-scoped advisory lock.
- Tests are not proof of an end-to-end Android -> PostgreSQL -> replay -> AI integration.

## Fallback

If PostgreSQL is unavailable, **do not** auto-switch to SQLite with unacknowledged events. Keep the Android offline outbox and the previously configured SQLite mode unchanged. Explicitly retry after operator diagnosis; retain all unsynced data.
