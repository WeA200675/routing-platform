#!/usr/bin/env python3
"""Opt-in SQLite HTTP contract: durable ACK, duplicate and backpressure."""
import http.client
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path


class DurableHttpTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            self.port = sock.getsockname()[1]
        env = dict(os.environ, TRAINING_API_HOST="127.0.0.1", TRAINING_API_PORT=str(self.port),
                   TRAINING_STATE_PATH=str(Path(self.directory.name) / "state.json"),
                   TRAINING_JOB_DB_PATH=str(Path(self.directory.name) / "jobs.sqlite3"))
        self.process = subprocess.Popen([sys.executable, "-m", "backend.training_api"], env=env,
                                        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        self.addCleanup(self.stop)
        for _ in range(80):
            if self.process.poll() is not None:
                raise RuntimeError("API exited before startup: " + self.process.stderr.read().decode()[:1500])
            try:
                status, _ = self.request("GET", "/api/training/jobs")
                if status == 200:
                    break
            except OSError:
                pass
            time.sleep(0.05)
        else:
            self.fail("API failed to start")

    def stop(self):
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=4)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=4)
        self.process.stderr.close()

    def request(self, method, path, payload=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=3)
        try:
            data = json.dumps(payload).encode() if payload is not None else None
            conn.request(method, path, body=data, headers={"Content-Type": "application/json"})
            response = conn.getresponse()
            return response.status, json.loads(response.read())
        finally:
            conn.close()

    @staticmethod
    def event(identifier):
        return {"event_id": identifier, "type": "navigation-error", "payload": {"reason": "wrong-turn"}}

    def test_durable_ack_and_duplicate(self):
        status, result = self.request("POST", "/api/training/events", {"events": [self.event("one")]})
        self.assertEqual(status, 202)
        self.assertTrue(result["durable"])
        self.assertEqual(result["accepted"], 1)
        status, result = self.request("POST", "/api/training/events", {"events": [self.event("one")]})
        self.assertEqual(status, 202)
        self.assertEqual(result["accepted"], 0)
        self.assertEqual(result["duplicates"], 1)
        status, result = self.request("GET", "/api/training/jobs")
        self.assertEqual(status, 200)
        self.assertEqual(result["counts"], {"queued": 1})
        self.assertEqual(result["status"], "blocked_no_replay_runner")

    def test_atomic_rejection(self):
        conflict = self.event("one")
        conflict["payload"]["reason"] = "different"
        status, _ = self.request("POST", "/api/training/events", {"events": [self.event("one")]})
        self.assertEqual(status, 202)
        status, _ = self.request("POST", "/api/training/events",
                                 {"events": [self.event("two"), conflict]})
        self.assertEqual(status, 400)
        _, result = self.request("GET", "/api/training/jobs")
        self.assertEqual(result["counts"], {"queued": 1})


if __name__ == "__main__":
    unittest.main()
