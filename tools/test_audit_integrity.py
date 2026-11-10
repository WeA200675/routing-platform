#!/usr/bin/env python3
"""Audit-chain integrity tests."""
import unittest
from backend.training_api import TrainingState


class AuditIntegrityTest(unittest.TestCase):
    def test_chain_is_valid(self):
        state = TrainingState(max_bytes=1024)
        for index in range(3):
            state.record_activity(f"event-{index}", "TEST")
        self.assertTrue(state._verify_audit_chain(state.activities, state.audit_anchor_hash))

    def test_fifo_anchor_preserves_validity(self):
        state = TrainingState(max_bytes=1024)
        for index in range(80):
            state.record_activity(f"event-{index}", "TEST")
        self.assertEqual(len(state.activities), 50)
        self.assertTrue(state.audit_anchor_hash)
        self.assertTrue(state._verify_audit_chain(state.activities, state.audit_anchor_hash))

    def test_tampering_is_detected(self):
        state = TrainingState(max_bytes=1024)
        state.record_activity("protected", "TEST")
        state.activities[0]["text"] = "changed"
        self.assertFalse(state._verify_audit_chain(state.activities, state.audit_anchor_hash))


if __name__ == "__main__":
    unittest.main()
