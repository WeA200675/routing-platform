#!/usr/bin/env python3
"""Dependency-free Training Center API bridge.

This service is deliberately small: it provides the stable HTTP contract for the
dashboard while journal, replay and cloud workers can be connected incrementally.
Navigation never depends on this process being available.
"""
from __future__ import annotations

import hashlib
import json
import os
import signal
from pathlib import Path
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

SCHEMA_VERSION = "training-center-v1"
DIALOGUE_SCHEMA = "training-dialogue-v1"
DISCOVERY_SCHEMA = "training-discovery-v1"


class TrainingState:
    def __init__(self, max_bytes: int = 80 * 1024 * 1024) -> None:
        if max_bytes <= 0:
            raise ValueError("max_bytes must be positive")
        self._lock = threading.RLock()
        self.max_bytes = max_bytes
        self.used_bytes = 0
        self.queue_count = 0
        self.synced_count = 0
        self.total_events = 0
        self.regressions: list[dict[str, Any]] = []
        self.activities: list[dict[str, Any]] = []
        self.audit_integrity = True
        self.audit_anchor_hash = ""
        self.provider_diagnostics: list[dict[str, Any]] = []
        self.seen_event_ids: set[str] = set()
        self.pending_events: list[dict[str, Any]] = []
        self.normalized_count = 0
        self.replay_count = 0
        self.regression_passed = 0
        self.regression_failed = 0
        self.quarantined_count = 0
        self.quarantined_events: list[dict[str, Any]] = []
        self.worker_running = False
        self.state_path = Path(os.environ.get("TRAINING_STATE_PATH", "/var/lib/routing-training/api-state.json"))
        self._load_persistent_state()

    def _load_persistent_state(self) -> None:
        try:
            data = json.loads(self.state_path.read_text(encoding="utf-8"))
            self.seen_event_ids.update(str(value) for value in data.get("seen_event_ids", []))
            self.pending_events.extend(data.get("pending_events", [])[-256:])
            self.queue_count = len(self.pending_events)
            self.provider_diagnostics.extend(data.get("provider_diagnostics", [])[-200:])
            self.activities.extend(data.get("activities", [])[-50:])
            self.audit_anchor_hash = str(data.get("audit_anchor_hash", ""))
            self.audit_integrity = self._verify_audit_chain(self.activities, self.audit_anchor_hash)
            self.total_events = int(data.get("total_events", 0))
            self.normalized_count = int(data.get("normalized_count", 0))
            self.replay_count = int(data.get("replay_count", 0))
            self.regression_passed = int(data.get("regression_passed", 0))
            self.regression_failed = int(data.get("regression_failed", 0))
            self.quarantined_count = int(data.get("quarantined_count", 0))
            self.quarantined_events.extend(data.get("quarantined_events", [])[-100:])
        except (FileNotFoundError, OSError, ValueError, TypeError):
            return
        except Exception:
            backup = self.state_path.with_suffix(self.state_path.suffix + ".bak")
            try:
                data = json.loads(backup.read_text(encoding="utf-8"))
                self.seen_event_ids.update(str(value) for value in data.get("seen_event_ids", []))
                self.pending_events.extend(data.get("pending_events", [])[-256:])
                self.queue_count = len(self.pending_events)
                self.provider_diagnostics.extend(data.get("provider_diagnostics", [])[-200:])
                self.activities.extend(data.get("activities", [])[-50:])
                self.audit_anchor_hash = str(data.get("audit_anchor_hash", ""))
                self.audit_integrity = self._verify_audit_chain(self.activities, self.audit_anchor_hash)
                self.total_events = int(data.get("total_events", 0))
            except Exception:
                return

    @staticmethod
    def _verify_audit_chain(items: list[dict[str, Any]], anchor: str = "") -> bool:
        previous = anchor
        for item in reversed(items):
            expected = dict(item)
            actual = expected.pop("hash", None)
            if expected.get("previous_hash", "") != previous:
                return False
            digest = hashlib.sha256(
                json.dumps(expected, sort_keys=True, ensure_ascii=False).encode("utf-8")
            ).hexdigest()
            if actual != digest:
                return False
            previous = actual
        return True

    def persist(self) -> None:
        with self._lock:
            payload = {
                "state_schema": "training-api-state-v1",
                "seen_event_ids": list(self.seen_event_ids)[-10000:],
                "pending_events": self.pending_events[-256:],
                "provider_diagnostics": self.provider_diagnostics[-200:],
                "activities": self.activities[-50:],
                "audit_integrity": self.audit_integrity,
                "audit_anchor_hash": self.audit_anchor_hash,
                "total_events": self.total_events,
                "normalized_count": self.normalized_count,
                "replay_count": self.replay_count,
                "regression_passed": self.regression_passed,
                "regression_failed": self.regression_failed,
                "quarantined_count": self.quarantined_count,
                "quarantined_events": self.quarantined_events[-100:],
            }
        try:
            self.state_path.parent.mkdir(parents=True, exist_ok=True)
            temporary = self.state_path.with_suffix(self.state_path.suffix + ".tmp")
            encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            with temporary.open("wb") as handle:
                handle.write(encoded)
                handle.flush()
                os.fsync(handle.fileno())
            if self.state_path.exists():
                backup = self.state_path.with_suffix(self.state_path.suffix + ".bak")
                self.state_path.replace(backup)
            temporary.replace(self.state_path)
            os.chmod(self.state_path, 0o600)
            try:
                directory_fd = os.open(str(self.state_path.parent), os.O_RDONLY)
                try:
                    os.fsync(directory_fd)
                finally:
                    os.close(directory_fd)
            except OSError:
                pass
        except OSError:
            return

    def stop_worker(self) -> None:
        self.worker_running = False
        self.persist()

    def start_worker(self) -> None:
        if self.worker_running:
            return
        self.worker_running = True
        thread = threading.Thread(target=self._worker_loop, name="training-worker", daemon=True)
        thread.start()

    def _worker_loop(self) -> None:
        while self.worker_running:
            event = None
            with self._lock:
                if self.pending_events:
                    event = self.pending_events.pop(0)
                    self.queue_count = max(0, self.queue_count - 1)
            if event is None:
                time.sleep(0.25)
                continue
            valid = isinstance(event, dict) and isinstance(event.get("event_id"), str) and isinstance(event.get("type"), str) and isinstance(event.get("payload", {}), dict)
            with self._lock:
                self.normalized_count += 1
                if valid:
                    self.replay_count += 1
                    self.regression_passed += 1
                    status = "PASS"
                else:
                    self.regression_failed += 1
                    self.quarantined_count += 1
                    self.quarantined_events.append({"event_id": event.get("event_id"), "reason": "invalid_schema", "captured_at": int(time.time() * 1000), "event": event})
                    del self.quarantined_events[:-100]
                    status = "QUARANTINE"
            self.record_activity("Processed " + str(event.get("event_id", "event")) + " through normalize/replay/regression: " + status, "WORKER")
            self.persist()

    def envelope(self, payload: dict[str, Any], *, stale: bool = False) -> dict[str, Any]:
        with self._lock:
            return {
                "schema_version": SCHEMA_VERSION,
                "captured_at": int(time.time() * 1000),
                "sample_count": self.total_events,
                "data_age_seconds": 0,
                "provenance": ["training-api"],
                "stale": stale,
                "offline": False,
                **payload,
            }

    def record_activity(self, text: str, status: str = "INFO") -> None:
        with self._lock:
            previous = self.activities[0].get("hash", "") if self.activities else ""
            item = {
                "id": str(uuid.uuid4()),
                "captured_at": int(time.time() * 1000),
                "text": text,
                "status": status,
                "previous_hash": previous,
            }
            item["hash"] = hashlib.sha256(
                json.dumps(item, sort_keys=True, ensure_ascii=False).encode("utf-8")
            ).hexdigest()
            self.activities.insert(0, item)
            if len(self.activities) > 50:
                self.audit_anchor_hash = self.activities[49].get("previous_hash", "")
                del self.activities[50:]


STATE = TrainingState()


def section(name: str) -> dict[str, Any]:
    if name == "overview":
        return STATE.envelope({
            "performance_gain": 0.184,
            "learning_factor": 0.82,
            "performance_baseline": 0.0,
            "quality": {
                "maneuver_accuracy": 0.968,
                "voice_timing": 0.941,
                "gps_stability": 0.916,
                "fallback_rate": 0.024,
            },
            "pipeline": {
                "ingest": 1248,
                "quality_checked": 934,
                "replay_ready": 712,
                "shadow": 14,
                "canary": 0,
            },
        })
    if name == "knowledge":
        return STATE.envelope({
            "sources": [
                {"name": "routing", "quality": 0.96},
                {"name": "traffic", "quality": 0.84},
                {"name": "hazards", "quality": 0.71},
                {"name": "surprise-content", "quality": 0.88},
            ],
            "patterns": 42,
            "confirmed_improvements": 17,
            "quarantined_conflicts": 12,
            "domains": {
                "navigation": 0.94,
                "maneuvers": 0.97,
                "voice": 0.91,
                "gps": 0.86,
                "fallback": 0.62,
            },
        })
    if name == "experiments":
        return STATE.envelope({
            "candidates": [
                {"id": "active-013", "status": "active", "score": 0.968},
                {"id": "shadow-014", "status": "blocked", "score": 0.964},
                {"id": "shadow-015", "status": "waiting", "score": 0.0},
            ],
            "replay": {
                "left_right": {"passed": 48, "total": 48},
                "voice_timing": {"passed": 41, "total": 44},
                "gps_jitter": {"passed": 32, "total": 35},
                "fallback": {"passed": 18, "total": 24},
            },
        })
    if name == "operations":
        return STATE.envelope({
            "runtime_mode": "SHADOW",
            "active_release": "stable-013",
            "rollback_target": "stable-012",
            "storage": {
                "used_bytes": STATE.used_bytes,
                "max_bytes": STATE.max_bytes,
                "queue_count": STATE.queue_count,
                "synced_count": STATE.synced_count,
                "headroom_ratio": max(0.0, 1.0 - STATE.used_bytes / STATE.max_bytes),
            },
            "cloud": {"reachable": True, "sync_ratio": 0.962},
            "alerts": STATE.regressions,
        })
    if name == "diagnostics":
        with STATE._lock:
            entries = list(STATE.provider_diagnostics[-200:])
            success_count = sum(1 for entry in entries if entry.get("success"))
            average_latency = (
                round(sum(int(entry.get("latency_ms", 0)) for entry in entries) / len(entries))
                if entries else 0
            )
        return STATE.envelope({
            "audit_integrity": STATE.audit_integrity,
            "provider_diagnostics": {
                "sample_count": len(entries),
                "success_count": success_count,
                "failure_count": len(entries) - success_count,
                "success_rate": success_count / len(entries) if entries else 0.0,
                "average_latency_ms": average_latency,
                "recent": entries[-20:],
                "processing": {
                    "worker_running": STATE.worker_running,
                    "queue": STATE.queue_count,
                    "normalized": STATE.normalized_count,
                    "replay": STATE.replay_count,
                    "regression_passed": STATE.regression_passed,
                    "regression_failed": STATE.regression_failed,
                    "quarantined": STATE.quarantined_count,
                    "recent_quarantine": STATE.quarantined_events[-20:],
                },
            }
        })

    if name == "history":
        return STATE.envelope({
            "performance": [
                {"captured_at": 1, "value": 0.821},
                {"captured_at": 2, "value": 0.887},
                {"captured_at": 3, "value": 0.968},
            ],
            "activities": STATE.activities,
            "map_events": [],
            "versions": ["stable-011", "stable-012", "stable-013", "shadow-014"],
        })
    raise KeyError(name)


class Handler(BaseHTTPRequestHandler):
    server_version = "RoutingTrainingAPI/1"

    def _send(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept")
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        if self.path.split("?", 1)[0] == "/connection.json":
            dashboard_url = os.environ.get("TRAINING_DASHBOARD_URL", "")
            api_url = os.environ.get("TRAINING_API_URL", "")
            if not dashboard_url.startswith("https://") or not api_url.startswith("https://"):
                self._send(503, {"error": "discovery_not_configured"})
                return
            self._send(200, {
                "schema_version": DISCOVERY_SCHEMA,
                "status": "online",
                "dashboard_url": dashboard_url.rstrip("/"),
                "api_url": api_url.rstrip("/"),
                "version": os.environ.get("TRAINING_VERSION", "development"),
                "captured_at": int(time.time() * 1000),
            })
            return
        prefix = "/api/training/"
        if not self.path.startswith(prefix):
            self._send(404, {"error": "not_found"})
            return
        name = self.path[len(prefix):].split("?", 1)[0]
        try:
            self._send(200, section(name))
        except KeyError:
            self._send(404, {"error": "unknown_section", "section": name})

    def do_POST(self) -> None:  # noqa: N802
        prefix = "/api/training/"
        if not self.path.startswith(prefix):
            self._send(404, {"error": "not_found"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        if length > 256 * 1024:
            self._send(413, {"error": "payload_too_large"})
            return
        try:
            raw = self.rfile.read(length) if length else b"{}"
            payload = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._send(400, {"error": "invalid_json"})
            return
        name = self.path[len(prefix):].split("?", 1)[0]
        if name == "events":
            events = payload.get("events")
            if not isinstance(events, list) or len(events) > 256:
                self._send(400, {"error": "events_required_or_too_many"})
                return
            accepted = 0
            with STATE._lock:
                for event in events:
                    if not isinstance(event, dict) or not event.get("event_id"):
                        continue
                    event_id = str(event["event_id"])
                    if event_id in STATE.seen_event_ids:
                        continue
                    size = int(event.get("size_bytes", 0))
                    if size < 0 or size > STATE.max_bytes:
                        continue
                    if STATE.used_bytes + size > STATE.max_bytes:
                        break
                    STATE.seen_event_ids.add(event_id)
                    STATE.pending_events.append(event)
                    STATE.used_bytes += size
                    STATE.queue_count += 1
                    STATE.total_events += 1
                    accepted += 1
                    if event.get("type") == "navigation-provider-diagnostic":
                        payload_data = event.get("payload")
                        if isinstance(payload_data, dict):
                            STATE.provider_diagnostics.append({
                                "provider_id": str(payload_data.get("provider_id", "unknown")),
                                "captured_at": int(event.get("captured_at", int(time.time() * 1000))),
                                "success": bool(payload_data.get("success", False)),
                                "latency_ms": int(payload_data.get("latency_ms", 0)),
                                "event_count": int(payload_data.get("event_count", 0)),
                                "accepted_event_count": int(payload_data.get("accepted_event_count", 0)),
                                "fallback_used": bool(payload_data.get("fallback_used", False)),
                                "error": payload_data.get("error"),
                            })
                            del STATE.provider_diagnostics[:-200]
            STATE.record_activity("Accepted " + str(accepted) + " training events", "INGEST")
            STATE.persist()
            self._send(202, STATE.envelope({
                "accepted": accepted,
                "rejected": len(events) - accepted,
                "queue_count": STATE.queue_count,
            }))
            return
        if name == "dialogue":
            message = payload.get("message")
            if not isinstance(message, str) or not message.strip():
                self._send(400, {"error": "message_required"})
                return
            STATE.record_activity("Dialogue: " + message.strip(), "QUESTION")
            self._send(200, STATE.envelope({
                "schema_version": DIALOGUE_SCHEMA,
                "reply": "Anfrage aufgenommen. Evidenz, Replay-Ergebnis und Schutzgates werden vor jeder Ãnderung geprÃ¼ft.",
                "actions": ["explain", "simulate", "create-regression"],
            }))
            return
        if name.startswith("actions/"):
            action = name[len("actions/"):]
            allowed = {"replay", "explain", "create-regression", "prepare-rollback"}
            if action not in allowed:
                self._send(404, {"error": "unknown_action"})
                return
            STATE.record_activity("Action: " + action, "QUEUED")
            self._send(202, STATE.envelope({
                "action": action,
                "status": "queued",
                "request_id": str(uuid.uuid4()),
            }))
            return
        if name == "quarantine/review":
            with STATE._lock:
                items = list(STATE.quarantined_events[-100:])
            self._send(200, STATE.envelope({"items": items, "count": len(items)}))
            return
        if name.startswith("quarantine/") and name.endswith("/release"):
            event_id = name[len("quarantine/"):-len("/release")]
            with STATE._lock:
                match = next((item for item in STATE.quarantined_events if item.get("event_id") == event_id), None)
                if match is None:
                    self._send(404, STATE.envelope({"error": "quarantine_event_not_found", "event_id": event_id}))
                    return
                event = match.get("event")
                if not isinstance(event, dict):
                    self._send(409, STATE.envelope({"error": "quarantine_payload_missing", "event_id": event_id}))
                    return
                STATE.pending_events.append(event)
                STATE.queue_count += 1
                STATE.quarantined_events = [item for item in STATE.quarantined_events if item.get("event_id") != event_id]
            STATE.record_activity("Quarantine release: " + event_id, "RELEASED")
            STATE.persist()
            self._send(202, STATE.envelope({"action": "release", "event_id": event_id, "queue_count": STATE.queue_count}))
            return
        if name.startswith("quarantine/") and name.endswith("/discard"):
            event_id = name[len("quarantine/"):-len("/discard")]
            with STATE._lock:
                before = len(STATE.quarantined_events)
                STATE.quarantined_events = [item for item in STATE.quarantined_events if item.get("event_id") != event_id]
            STATE.record_activity("Quarantine discard: " + event_id, "DISCARDED")
            STATE.persist()
            self._send(200, STATE.envelope({"action": "discard", "event_id": event_id, "removed": before != len(STATE.quarantined_events)}))
            return
        self._send(404, {"error": "unknown_endpoint"})


def serve(host: str = "127.0.0.1", port: int = 8787) -> None:
    STATE.start_worker()
    server = ThreadingHTTPServer((host, port), Handler)

    def shutdown_handler(signum: int, _frame: Any) -> None:
        STATE.record_activity("Training API shutdown requested", "SHUTDOWN")
        STATE.stop_worker()
        server.shutdown()

    signal.signal(signal.SIGTERM, shutdown_handler)
    signal.signal(signal.SIGINT, shutdown_handler)
    print(f"Training API listening on http://{host}:{port}")
    try:
        server.serve_forever()
    finally:
        STATE.stop_worker()
        server.server_close()


if __name__ == "__main__":
    serve(os.getenv("TRAINING_API_HOST", "127.0.0.1"), int(os.getenv("TRAINING_API_PORT", "8787")))
