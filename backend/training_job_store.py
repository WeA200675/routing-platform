"""Transactional, dependency-free training job store.

SQLite is the local fallback for disconnected deployments. The API may later
select a PostgreSQL implementation with the same contract. No successful
replay can be recorded without a verifiable evidence digest.
"""
from __future__ import annotations

import hashlib
from contextlib import contextmanager
import json
import sqlite3
import time
import uuid
from pathlib import Path
from typing import Any

TERMINAL = frozenset({"passed", "failed", "quarantined"})
MAX_EVENT_BYTES = 256 * 1024


class QueueFullError(RuntimeError):
    pass


class InvalidTransitionError(RuntimeError):
    pass


class TrainingJobStore:
    def __init__(self, path: str | Path, *, max_pending: int = 10000, lease_seconds: int = 120, max_attempts: int = 5):
        if max_pending < 1 or lease_seconds < 1 or max_attempts < 1:
            raise ValueError("invalid queue limits")
        self.path = str(path)
        self.max_pending = max_pending
        self.lease_seconds = lease_seconds
        self.max_attempts = max_attempts
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    @contextmanager
    def _connect(self):
        """Open a transactional connection and always release its resources.

        sqlite3.Connection.__exit__ commits or rolls back but does not close
        the connection. Every job-store operation must close it explicitly,
        including exceptions and early returns.
        """
        connection = sqlite3.connect(self.path, timeout=15, isolation_level=None)
        try:
            connection.row_factory = sqlite3.Row
            connection.execute("PRAGMA busy_timeout=15000")
            connection.execute("PRAGMA journal_mode=WAL")
            connection.execute("PRAGMA synchronous=FULL")
            with connection:
                yield connection
        finally:
            connection.close()

    def _init_db(self):
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS jobs (
                event_id TEXT PRIMARY KEY,
                payload TEXT NOT NULL,
                payload_sha256 TEXT NOT NULL,
                state TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                lease_token TEXT,
                lease_until INTEGER,
                evidence_sha256 TEXT,
                result_json TEXT,
                last_error TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""")
            db.execute("CREATE INDEX IF NOT EXISTS jobs_state_lease ON jobs(state,lease_until,created_at)")

    def submit(self, event: dict[str, Any]) -> tuple[str, bool]:
        if not isinstance(event, dict):
            raise ValueError("event must be an object")
        event_id = event.get("event_id")
        if not isinstance(event_id, str) or not (1 <= len(event_id) <= 200):
            raise ValueError("invalid event_id")
        if not isinstance(event.get("type"), str) or not event["type"]:
            raise ValueError("invalid event type")
        if not isinstance(event.get("payload", {}), dict):
            raise ValueError("invalid event payload")
        serialized = json.dumps(event, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        if len(serialized.encode("utf-8")) > MAX_EVENT_BYTES:
            raise ValueError("event exceeds size limit")
        digest = hashlib.sha256(serialized.encode("utf-8")).hexdigest()
        now = int(time.time())
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            existing = db.execute("SELECT payload_sha256,state FROM jobs WHERE event_id=?", (event_id,)).fetchone()
            if existing:
                if existing["payload_sha256"] != digest:
                    raise ValueError("event_id reused with different content")
                db.execute("COMMIT")
                return existing["state"], False
            count = db.execute("SELECT COUNT(*) FROM jobs WHERE state NOT IN ('passed','failed','quarantined')").fetchone()[0]
            if count >= self.max_pending:
                raise QueueFullError("pending job capacity reached; retry later")
            db.execute("""INSERT INTO jobs(event_id,payload,payload_sha256,state,created_at,updated_at)
                VALUES(?,?,?,'queued',?,?)""", (event_id, serialized, digest, now, now))
            db.execute("COMMIT")
            return "queued", True

    def submit_batch(self, events: list[dict[str, Any]]) -> dict[str, Any]:
        """Atomically accept a batch; no partial success or silent event loss.

        A duplicate with identical content is idempotent. Any malformed event,
        conflicting ID, or full queue rolls back the *whole* batch.
        """
        if not isinstance(events, list) or not 1 <= len(events) <= 256:
            raise ValueError("events must contain 1..256 objects")
        prepared = []
        for event in events:
            if not isinstance(event, dict):
                raise ValueError("event must be an object")
            event_id = event.get("event_id")
            if not isinstance(event_id, str) or not (1 <= len(event_id) <= 200):
                raise ValueError("invalid event_id")
            if not isinstance(event.get("type"), str) or not event["type"]:
                raise ValueError("invalid event type")
            if not isinstance(event.get("payload", {}), dict):
                raise ValueError("invalid event payload")
            serialized = json.dumps(event, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
            if len(serialized.encode("utf-8")) > MAX_EVENT_BYTES:
                raise ValueError("event exceeds size limit")
            prepared.append((event_id, serialized, hashlib.sha256(serialized.encode("utf-8")).hexdigest()))
        accepted = 0
        duplicates = 0
        now = int(time.time())
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            count = db.execute("SELECT COUNT(*) FROM jobs WHERE state NOT IN ('passed','failed','quarantined')").fetchone()[0]
            for event_id, serialized, digest in prepared:
                existing = db.execute("SELECT payload_sha256 FROM jobs WHERE event_id=?", (event_id,)).fetchone()
                if existing:
                    if existing["payload_sha256"] != digest:
                        raise ValueError("event_id reused with different content")
                    duplicates += 1
                    continue
                if count >= self.max_pending:
                    raise QueueFullError("pending job capacity reached; retry later")
                db.execute("""INSERT INTO jobs(event_id,payload,payload_sha256,state,created_at,updated_at)
                    VALUES(?,?,?,'queued',?,?)""", (event_id, serialized, digest, now, now))
                count += 1
                accepted += 1
            db.execute("COMMIT")
        return {"accepted": accepted, "duplicates": duplicates, "queue_count": count}

    def claim(self) -> dict[str, Any] | None:
        now = int(time.time())
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            # Expired leases must not be reclaimed forever after worker crashes.
            # Quarantine exhausted jobs as blocked; preserve payload for review.
            db.execute("""UPDATE jobs SET state='blocked_no_runner',
                lease_token=NULL,lease_until=NULL,last_error='retry_exhausted',
                updated_at=? WHERE (state='replaying' AND lease_until <= ?
                OR state='queued') AND attempts >= ?""",
                (now, now, self.max_attempts))
            row = db.execute("""SELECT * FROM jobs
                WHERE (state='queued' AND attempts < ?)
                   OR (state='replaying' AND lease_until <= ? AND attempts < ?)
                ORDER BY created_at,event_id LIMIT 1""",
                (self.max_attempts, now, self.max_attempts)).fetchone()
            if row is None:
                db.execute("COMMIT")
                return None
            token = uuid.uuid4().hex
            db.execute("""UPDATE jobs SET state='replaying', attempts=attempts+1,
                lease_token=?,lease_until=?,updated_at=?
                WHERE event_id=?""", (token, now + self.lease_seconds, now, row["event_id"]))
            db.execute("COMMIT")
            return {"event_id": row["event_id"], "event": json.loads(row["payload"]),
                    "lease_token": token, "attempt": row["attempts"] + 1}

    def complete(self, event_id: str, lease_token: str, *, passed: bool,
                 evidence: bytes, result: dict[str, Any]) -> None:
        if not isinstance(evidence, bytes) or not evidence:
            raise ValueError("nonempty replay evidence required")
        if not isinstance(result, dict) or not result:
            raise ValueError("structured regression result required")
        # No trusted replay attestor is wired to this store yet. Never let an
        # arbitrary caller set verified=true and promote a navigation result.
        # Persisting a passing/failed replay will be enabled only after an
        # independently checked execution artifact and trusted worker exist.
        raise InvalidTransitionError("trusted replay attestation not configured")

    def block(self, event_id: str, lease_token: str, reason: str = "no_replay_runner") -> None:
        if not reason:
            raise ValueError("reason required")
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT state,lease_token FROM jobs WHERE event_id=?", (event_id,)).fetchone()
            if not row or row["state"] != "replaying" or row["lease_token"] != lease_token:
                raise InvalidTransitionError("invalid lease")
            db.execute("""UPDATE jobs SET state='blocked_no_runner',lease_token=NULL,
                lease_until=NULL,last_error=?,updated_at=? WHERE event_id=?""",
                (reason, int(time.time()), event_id))
            db.execute("COMMIT")

    def requeue_blocked(self, event_id: str) -> bool:
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            changed = db.execute("""UPDATE jobs SET state='queued',last_error=NULL,updated_at=?
                WHERE event_id=? AND state='blocked_no_runner'""",
                (int(time.time()), event_id)).rowcount
            db.execute("COMMIT")
            return changed == 1

    def status(self, event_id: str) -> dict[str, Any] | None:
        with self._connect() as db:
            row = db.execute("""SELECT event_id,state,attempts,evidence_sha256,result_json,
                last_error,created_at,updated_at FROM jobs WHERE event_id=?""", (event_id,)).fetchone()
            if not row:
                return None
            result = dict(row)
            result["result"] = json.loads(result.pop("result_json")) if result["result_json"] else None
            return result

    def counts(self) -> dict[str, int]:
        with self._connect() as db:
            rows = db.execute("SELECT state,COUNT(*) AS n FROM jobs GROUP BY state").fetchall()
            return {row["state"]: row["n"] for row in rows}
