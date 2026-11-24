#!/usr/bin/env python3
"""Safety regression tests: ingestion is not proof of replay success."""
import tempfile
import time
import unittest
from pathlib import Path
from backend.training_api import TrainingState


class ReplayFailClosedTest(unittest.TestCase):
    def test_valid_event_remains_pending_without_runner(self):
        with tempfile.TemporaryDirectory() as directory:
            state = TrainingState()
            state.state_path = Path(directory) / "state.json"
            event = {"event_id": "safety-001", "type": "navigation-error", "payload": {"reason": "wrong-turn"}}
            state.pending_events.append(event)
            state.queue_count = 1
            state.start_worker()
            try:
                time.sleep(0.7)
                self.assertTrue(state.worker_running)
                self.assertEqual(state.replay_count, 0)
                self.assertEqual(state.regression_passed, 0)
                self.assertEqual(state.queue_count, 1)
                self.assertEqual(state.pending_events, [event])
                state.persist()
                restored = TrainingState()
                restored.state_path = state.state_path
                restored._load_persistent_state()
                self.assertEqual(restored.pending_events, [event])
            finally:
                state.stop_worker()


if __name__ == "__main__":
    unittest.main()
