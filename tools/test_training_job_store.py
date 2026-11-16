#!/usr/bin/env python3
"""Transactional queue invariants; run with python3 -m unittest -v tools.test_training_job_store."""
import sqlite3
import tempfile
import unittest
from pathlib import Path

from backend.training_job_store import (
    InvalidTransitionError, QueueFullError, TrainingJobStore,
)


class TrainingJobStoreTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "jobs.sqlite3"
        self.store = TrainingJobStore(self.path, max_pending=2, lease_seconds=30)

    @staticmethod
    def event(event_id):
        return {"event_id": event_id, "type": "navigation-error", "payload": {"reason": "wrong-turn"}}

    def test_connection_closes_after_success_and_failure(self):
        with self.store._connect() as connection:
            connection.execute("SELECT 1")
        with self.assertRaises(sqlite3.ProgrammingError):
            connection.execute("SELECT 1")

        with self.assertRaisesRegex(RuntimeError, "deliberate failure"):
            with self.store._connect() as failed_connection:
                failed_connection.execute("SELECT 1")
                raise RuntimeError("deliberate failure")
        with self.assertRaises(sqlite3.ProgrammingError):
            failed_connection.execute("SELECT 1")

    def test_idempotency_and_conflicting_duplicate(self):
        self.assertEqual(self.store.submit(self.event("one")), ("queued", True))
        self.assertEqual(self.store.submit(self.event("one")), ("queued", False))
        conflict = self.event("one")
        conflict["payload"]["reason"] = "other"
        with self.assertRaises(ValueError):
            self.store.submit(conflict)
        self.assertEqual(self.store.counts(), {"queued": 1})

    def test_capacity_is_fail_closed_not_fifo_data_loss(self):
        self.store.submit(self.event("one"))
        self.store.submit(self.event("two"))
        with self.assertRaises(QueueFullError):
            self.store.submit(self.event("three"))
        self.assertEqual(self.store.counts()["queued"], 2)

    def test_restart_preserves_pending(self):
        self.store.submit(self.event("one"))
        reopened = TrainingJobStore(self.path)
        self.assertEqual(reopened.status("one")["state"], "queued")
        self.assertEqual(reopened.claim()["event_id"], "one")

    def test_no_completion_without_trusted_attestor(self):
        self.store.submit(self.event("one"))
        claimed = self.store.claim()
        for evidence, result in [
            (b"", {"verified": True, "passed": True}),
            (b"replay", {"verified": False, "passed": True}),
            (b"replay", {"verified": True, "passed": True}),
            (b"replay", {"verified": True, "passed": False}),
        ]:
            with self.subTest(result=result):
                with self.assertRaises((ValueError, InvalidTransitionError)):
                    self.store.complete("one", claimed["lease_token"],
                                        passed=result["passed"], evidence=evidence, result=result)
        self.assertEqual(self.store.status("one")["state"], "replaying")
        self.assertIsNone(self.store.status("one")["evidence_sha256"])

    def test_block_and_explicit_requeue(self):
        self.store.submit(self.event("one"))
        job = self.store.claim()
        self.store.block("one", job["lease_token"])
        self.assertEqual(self.store.status("one")["state"], "blocked_no_runner")
        self.assertIsNone(self.store.claim())
        self.assertTrue(self.store.requeue_blocked("one"))
        self.assertEqual(self.store.claim()["attempt"], 2)

    def test_atomic_batch_rolls_back_on_conflict(self):
        self.store.submit(self.event("one"))
        conflicting = self.event("one")
        conflicting["payload"]["reason"] = "different"
        with self.assertRaises(ValueError):
            self.store.submit_batch([self.event("two"), conflicting])
        self.assertIsNone(self.store.status("two"))
        self.assertEqual(self.store.counts(), {"queued": 1})

    def test_atomic_batch_rolls_back_on_capacity(self):
        with self.assertRaises(QueueFullError):
            self.store.submit_batch([self.event("one"), self.event("two"), self.event("three")])
        self.assertEqual(self.store.counts(), {})

    def test_atomic_batch_deduplicates_within_batch(self):
        result = self.store.submit_batch([self.event("one"), self.event("one")])
        self.assertEqual(result["accepted"], 1)
        self.assertEqual(result["duplicates"], 1)
        self.assertEqual(self.store.counts(), {"queued": 1})

    def test_batch_recovery_after_restart(self):
        self.store.submit_batch([self.event("one"), self.event("two")])
        reopened = TrainingJobStore(self.path)
        self.assertEqual(reopened.counts(), {"queued": 2})

    def test_expired_lease_reclaimed(self):
        self.store.submit(self.event("one"))
        first = self.store.claim()
        with self.store._connect() as db:
            db.execute("UPDATE jobs SET lease_until=0 WHERE event_id='one'")
        second = self.store.claim()
        self.assertNotEqual(first["lease_token"], second["lease_token"])
        with self.assertRaises(InvalidTransitionError):
            self.store.complete("one", first["lease_token"], passed=True,
                                evidence=b"evidence", result={"verified": True, "passed": True})


if __name__ == "__main__":
    unittest.main()
