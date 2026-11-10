#!/usr/bin/env python3
import tempfile
import unittest
from pathlib import Path
from backend.training_api import TrainingState


class TrainingStatePersistenceTest(unittest.TestCase):
    def test_pending_queue_survives_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            path = str(Path(directory) / "state.json")
            first = TrainingState()
            first.state_path = Path(path)
            first.pending_events.append({"event_id": "pending-1", "type": "test", "payload": {}})
            first.queue_count = 1
            first.persist()

            second = TrainingState()
            second.state_path = Path(path)
            second._load_persistent_state()
            self.assertEqual(second.queue_count, 1)
            self.assertEqual(second.pending_events[0]["event_id"], "pending-1")

    def test_corrupt_primary_uses_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "state.json"
            backup = path.with_suffix(".json.bak")
            state = TrainingState()
            state.state_path = backup
            state.pending_events.append({"event_id": "backup-1"})
            state.queue_count = 1
            state.persist()
            path.write_text("{broken", encoding="utf-8")
            restored = TrainingState()
            restored.state_path = path
            restored._load_persistent_state()
            self.assertEqual(restored.pending_events[0]["event_id"], "backup-1")

if __name__ == "__main__":
    unittest.main()
