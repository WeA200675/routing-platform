"""PostgreSQL contract tests; require explicit disposable test DSN.

Run: TRAINING_TEST_POSTGRES_DSN=postgresql://... python -m unittest -v tools.test_postgres_training_job_store
Never point this suite at production: it creates and deletes test rows.
"""
import os
import unittest
import uuid

from backend.training_job_store import InvalidTransitionError, QueueFullError


@unittest.skipUnless(os.getenv("TRAINING_TEST_POSTGRES_DSN"), "disposable PostgreSQL test DSN not provided")
class PostgresTrainingJobStoreContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from backend.postgres_training_job_store import PostgresTrainingJobStore
        cls.store = PostgresTrainingJobStore(
            os.environ["TRAINING_TEST_POSTGRES_DSN"], max_pending=10000,
            lease_seconds=30, max_attempts=2)

    def setUp(self):
        self.prefix = "contract-" + uuid.uuid4().hex
        self.ids = []

    def tearDown(self):
        # Only delete this test's own IDs; never truncate shared tables.
        with self.store._connect() as db:
            for event_id in self.ids:
                db.execute("DELETE FROM training_jobs WHERE event_id=%s", (event_id,))

    def event(self, suffix):
        event_id = self.prefix + "-" + suffix
        self.ids.append(event_id)
        return {"event_id": event_id, "type": "navigation-error",
                "payload": {"reason": "wrong-turn"}}

    def test_idempotent_batch_and_conflict_rollback(self):
        first = self.event("one")
        second = self.event("two")
        result = self.store.submit_batch([first, first])
        self.assertEqual(result["accepted"], 1)
        self.assertEqual(result["duplicates"], 1)
        self.assertEqual(self.store.submit(first), ("queued", False))
        conflict = dict(first, payload={"reason": "other"})
        with self.assertRaises(ValueError):
            self.store.submit_batch([second, conflict])
        self.assertIsNone(self.store.status(second["event_id"]))

    def test_claim_block_requeue_and_attestation_gate(self):
        event = self.event("claim")
        self.store.submit(event)
        claimed = self.store.claim()
        self.assertEqual(claimed["event_id"], event["event_id"])
        with self.assertRaises(InvalidTransitionError):
            self.store.complete(event["event_id"], claimed["lease_token"],
                                passed=True, evidence=b"replay",
                                result={"verified": True})
        self.store.block(event["event_id"], claimed["lease_token"])
        self.assertEqual(self.store.status(event["event_id"])["state"], "blocked_no_runner")
        self.assertTrue(self.store.requeue_blocked(event["event_id"]))


if __name__ == "__main__":
    unittest.main()
