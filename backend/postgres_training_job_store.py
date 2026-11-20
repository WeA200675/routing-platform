"""Opt-in PostgreSQL implementation of the fail-closed training job queue.

Requires psycopg 3; never falls back to SQLite silently. The caller must
explicitly supply a DSN. Does not alter or migrate existing SQLite data.
"""
from __future__ import annotations

from contextlib import contextmanager
import hashlib
import json
import time
import uuid
from typing import Any

from backend.training_job_store import (
    InvalidTransitionError, QueueFullError, MAX_EVENT_BYTES,
)


class PostgresTrainingJobStore:
    def __init__(self, dsn: str, *, max_pending: int = 10000,
                 lease_seconds: int = 120, max_attempts: int = 5):
        if not isinstance(dsn, str) or not dsn.strip():
            raise ValueError("PostgreSQL DSN required")
        if min(max_pending, lease_seconds, max_attempts) < 1:
            raise ValueError("invalid queue limits")
        self.dsn = dsn
        self.max_pending = max_pending
        self.lease_seconds = lease_seconds
        self.max_attempts = max_attempts
        try:
            import psycopg
        except ImportError as exc:
            raise RuntimeError("PostgreSQL backend requires psycopg 3") from exc
        self._driver = psycopg
        self._init_db()

    @contextmanager
    def _connect(self):
        with self._driver.connect(self.dsn, connect_timeout=5) as db:
            with db.cursor() as cur:
                cur.execute("SET LOCAL lock_timeout = '5s'")
                cur.execute("SET LOCAL statement_timeout = '15s'")
            yield db

    def _init_db(self):
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS training_jobs (
                event_id TEXT PRIMARY KEY,
                payload TEXT NOT NULL,
                payload_sha256 TEXT NOT NULL,
                state TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                lease_token TEXT,
                lease_until BIGINT,
                evidence_sha256 TEXT,
                result_json TEXT,
                last_error TEXT,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL
            )""")
            db.execute("""CREATE INDEX IF NOT EXISTS training_jobs_state_lease
                ON training_jobs(state,lease_until,created_at)""")

    @staticmethod
    def _prepare(event: dict[str, Any]):
        if not isinstance(event, dict):
            raise ValueError("event must be an object")
        event_id = event.get("event_id")
        if not isinstance(event_id, str) or not 1 <= len(event_id) <= 200:
            raise ValueError("invalid event_id")
        if not isinstance(event.get("type"), str) or not event["type"]:
            raise ValueError("invalid event type")
        if not isinstance(event.get("payload", {}), dict):
            raise ValueError("invalid event payload")
        serialized = json.dumps(event, sort_keys=True, ensure_ascii=False,
                                separators=(",", ":"), allow_nan=False)
        raw = serialized.encode("utf-8")
        if len(raw) > MAX_EVENT_BYTES:
            raise ValueError("event exceeds size limit")
        return event_id, serialized, hashlib.sha256(raw).hexdigest()

    def submit(self, event: dict[str, Any]) -> tuple[str, bool]:
        prepared = self._prepare(event)
        with self._connect() as db:
            # Serialize capacity checks and insertion across concurrent writers.
            db.execute("SELECT pg_advisory_xact_lock(782341902)")
            existing = db.execute(
                "SELECT payload_sha256,state FROM training_jobs WHERE event_id=%s",
                (prepared[0],)).fetchone()
            if existing:
                if existing[0] != prepared[2]:
                    raise ValueError("event_id reused with different content")
                return existing[1], False
            count = db.execute("""SELECT COUNT(*) FROM training_jobs
                WHERE state NOT IN ('passed','failed','quarantined')""").fetchone()[0]
            if count >= self.max_pending:
                raise QueueFullError("pending job capacity reached; retry later")
            now = int(time.time())
            db.execute("""INSERT INTO training_jobs
                (event_id,payload,payload_sha256,state,created_at,updated_at)
                VALUES (%s,%s,%s,'queued',%s,%s)""", (*prepared, now, now))
            return "queued", True

    def submit_batch(self, events: list[dict[str, Any]]) -> dict[str, Any]:
        if not isinstance(events, list) or not 1 <= len(events) <= 256:
            raise ValueError("events must contain 1..256 objects")
        prepared = [self._prepare(event) for event in events]
        accepted = duplicates = 0
        receipts = []
        with self._connect() as db:
            db.execute("SELECT pg_advisory_xact_lock(782341902)")
            count = db.execute("""SELECT COUNT(*) FROM training_jobs
                WHERE state NOT IN ('passed','failed','quarantined')""").fetchone()[0]
            now = int(time.time())
            for event_id, payload, digest in prepared:
                existing = db.execute(
                    "SELECT payload_sha256 FROM training_jobs WHERE event_id=%s",
                    (event_id,)).fetchone()
                if existing:
                    if existing[0] != digest:
                        raise ValueError("event_id reused with different content")
                    duplicates += 1
                    receipts.append({"event_id": event_id, "sha256": digest, "status": "duplicate"})
                    continue
                if count >= self.max_pending:
                    raise QueueFullError("pending job capacity reached; retry later")
                db.execute("""INSERT INTO training_jobs
                    (event_id,payload,payload_sha256,state,created_at,updated_at)
                    VALUES (%s,%s,%s,'queued',%s,%s)""",
                    (event_id, payload, digest, now, now))
                receipts.append({"event_id": event_id, "sha256": digest, "status": "accepted"})
                accepted += 1
                count += 1
        return {"accepted": accepted, "duplicates": duplicates, "queue_count": count, "receipts": receipts}

    def claim(self) -> dict[str, Any] | None:
        now = int(time.time())
        with self._connect() as db:
            db.execute("""UPDATE training_jobs SET state='blocked_no_runner',
                lease_token=NULL,lease_until=NULL,last_error='retry_exhausted',
                updated_at=%s WHERE
                ((state='replaying' AND lease_until <= %s) OR state='queued')
                AND attempts >= %s""", (now, now, self.max_attempts))
            row = db.execute("""SELECT event_id,payload,attempts FROM training_jobs
                WHERE (state='queued' AND attempts < %s)
                   OR (state='replaying' AND lease_until <= %s AND attempts < %s)
                ORDER BY created_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED""",
                (self.max_attempts, now, self.max_attempts)).fetchone()
            if row is None:
                return None
            token = uuid.uuid4().hex
            db.execute("""UPDATE training_jobs SET state='replaying',
                attempts=attempts+1,lease_token=%s,lease_until=%s,updated_at=%s
                WHERE event_id=%s""", (token, now + self.lease_seconds, now, row[0]))
            return {"event_id": row[0], "event": json.loads(row[1]),
                    "lease_token": token, "attempt": row[2] + 1}

    def complete(self, event_id: str, lease_token: str, *, passed: bool,
                 evidence: bytes, result: dict[str, Any]) -> None:
        if not isinstance(evidence, bytes) or not evidence:
            raise ValueError("nonempty replay evidence required")
        if not isinstance(result, dict) or not result:
            raise ValueError("structured regression result required")
        raise InvalidTransitionError("trusted replay attestation not configured")

    def block(self, event_id: str, lease_token: str,
              reason: str = "no_replay_runner") -> None:
        if not reason:
            raise ValueError("reason required")
        with self._connect() as db:
            changed = db.execute("""UPDATE training_jobs SET
                state='blocked_no_runner',lease_token=NULL,lease_until=NULL,
                last_error=%s,updated_at=%s WHERE event_id=%s
                AND state='replaying' AND lease_token=%s""",
                (reason, int(time.time()), event_id, lease_token)).rowcount
            if changed != 1:
                raise InvalidTransitionError("invalid lease")

    def requeue_blocked(self, event_id: str) -> bool:
        with self._connect() as db:
            changed = db.execute("""UPDATE training_jobs SET state='queued',
                attempts=CASE WHEN attempts >= %s THEN 0 ELSE attempts END,
                last_error=NULL,updated_at=%s
                WHERE event_id=%s AND state='blocked_no_runner'""",
                (self.max_attempts, int(time.time()), event_id)).rowcount
            return changed == 1

    def status(self, event_id: str) -> dict[str, Any] | None:
        with self._connect() as db:
            row = db.execute("""SELECT event_id,state,attempts,evidence_sha256,
                result_json,last_error,created_at,updated_at
                FROM training_jobs WHERE event_id=%s""", (event_id,)).fetchone()
        if not row:
            return None
        result = dict(zip(("event_id","state","attempts","evidence_sha256",
                           "result_json","last_error","created_at","updated_at"), row))
        result["result"] = json.loads(result.pop("result_json")) if result["result_json"] else None
        return result

    def counts(self) -> dict[str, int]:
        with self._connect() as db:
            rows = db.execute("SELECT state,COUNT(*) FROM training_jobs GROUP BY state").fetchall()
        return {state: count for state, count in rows}
