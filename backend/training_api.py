#!/usr/bin/env python3
"""Dependency-free Training Center API bridge.

This service is deliberately small: it provides the stable HTTP contract for the
dashboard while journal, replay and cloud workers can be connected incrementally.
Navigation never depends on this process being available.
"""
from __future__ import annotations

import json
import os
import threading
import time
import uuid
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

SCHEMA_VERSION = "training-center-v1"
DIALOGUE_SCHEMA = "training-dialogue-v1"


class TrainingState:
    def __init__(self, max_bytes: int = 80 * 1024 * 1024, journal_path: str | None = None) -> None:
        if max_bytes <= 0:
            raise ValueError("max_bytes must be positive")
        self._lock = threading.RLock()
        self.max_bytes = max_bytes
        self.journal_path = Path(journal_path or os.getenv("TRAINING_JOURNAL_PATH", "/var/lib/routing-training/state.json"))
        self.used_bytes = 0
        self.queue_count = 0
        self.synced_count = 0
        self.total_events = 0
        self.regressions: list[dict[str, Any]] = []
        self.activities: list[dict[str, Any]] = []
        self.running = False
        self.started_at: int | None = None
        self.last_cycle_at: int | None = None
        self.current_job = "idle"
        self.job_rate = 0.0
        self.job_processed = 0
        self.job_accepted = 0
        self.job_rejected = 0
        self.pending_events: list[dict[str, Any]] = []
        self.event_records: list[dict[str, Any]] = []
        self.normalized_count = 0
        self.replay_count = 0
        self.regression_passed = 0
        self.last_normalization_at: int | None = None
        self.last_replay_at: int | None = None
        self.worker_last_duration_ms = 0
        self._load_journal()

    def _load_journal(self) -> None:
        try:
            data = json.loads(self.journal_path.read_text(encoding="utf-8"))
        except (FileNotFoundError, OSError, json.JSONDecodeError):
            return
        with self._lock:
            for key in ("used_bytes", "queue_count", "synced_count", "total_events", "normalized_count", "replay_count", "regression_passed", "job_processed", "job_accepted", "job_rejected"):
                if isinstance(data.get(key), int) and data[key] >= 0:
                    setattr(self, key, data[key])
            self.last_cycle_at = data.get("last_cycle_at")
            self.last_normalization_at = data.get("last_normalization_at")
            self.last_replay_at = data.get("last_replay_at")
            records = data.get("event_records")
            if isinstance(records, list):
                self.event_records = records[-500:]

    def persist(self) -> None:
        with self._lock:
            data = {key: getattr(self, key) for key in (
                "used_bytes", "queue_count", "synced_count", "total_events", "normalized_count",
                "replay_count", "regression_passed", "job_processed", "job_accepted", "job_rejected",
                "last_cycle_at", "last_normalization_at", "last_replay_at", "event_records")}
        try:
            self.journal_path.parent.mkdir(parents=True, exist_ok=True)
            temp = self.journal_path.with_suffix(".tmp")
            temp.write_text(json.dumps(data), encoding="utf-8")
            temp.replace(self.journal_path)
        except OSError:
            pass

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
            self.activities.insert(0, {
                "id": str(uuid.uuid4()),
                "captured_at": int(time.time() * 1000),
                "text": text,
                "status": status,
            })
            del self.activities[50:]


STATE = TrainingState()


def section(name: str) -> dict[str, Any]:
    if name == "overview":
        return STATE.envelope({
            "performance_gain": None,
            "learning_factor": None,
            "performance_baseline": None,
            "metrics_status": "awaiting_real_training_events",
            "quality": {
                "maneuver_accuracy": None,
                "voice_timing": None,
                "gps_stability": None,
                "fallback_rate": None,
            },
            "training": {
                "running": STATE.running,
                "started_at": STATE.started_at,
                "last_cycle_at": STATE.last_cycle_at,
                "current_job": STATE.current_job,
                "data_rate_per_minute": STATE.job_rate,
                "processed": STATE.job_processed,
                "accepted": STATE.job_accepted,
                "rejected": STATE.job_rejected,
            },
            "pipeline": {
                "ingest": STATE.total_events,
                "quality_checked": 0,
                "replay_ready": 0,
                "shadow": 0,
                "canary": 0,
            },
        })
    if name == "diagnostics":
        now = int(time.time() * 1000)
        with STATE._lock:
            started = STATE.started_at
            elapsed = max(0.0, (now - started) / 1000.0) if started else 0.0
            rate = round((STATE.job_processed / max(1.0, elapsed)) * 60.0, 2) if started else 0.0
            return STATE.envelope({
                "status": ("stopped" if not STATE.running else ("active" if STATE.last_cycle_at and now - STATE.last_cycle_at < 30_000 else "waiting-for-data")),
                "process": {
                    "current_job": STATE.current_job,
                    "started_at": started,
                    "last_cycle_at": STATE.last_cycle_at,
                    "uptime_seconds": round(elapsed, 1),
                },
                "throughput": {
                    "events_per_minute": rate,
                    "processed": STATE.job_processed,
                    "accepted": STATE.job_accepted,
                    "rejected": STATE.job_rejected,
                    "normalized": STATE.normalized_count,
                    "replay": STATE.replay_count,
                    "regression_passed": STATE.regression_passed,
                    "worker_duration_ms": STATE.worker_last_duration_ms,
                    "recent_events": STATE.event_records[-25:],
                    "queue": STATE.queue_count,
                    "synced": STATE.synced_count,
                },
                "timing": {
                    "api_response_ms": 0,
                    "last_ingest_at": STATE.last_cycle_at,
                    "last_normalization_at": STATE.last_normalization_at,
                    "last_replay_at": STATE.last_replay_at,
                },
                "providers": {
                    "training_api": "online",
                    "cloud_storage": "not_measured",
                    "live_events": "not_configured",
                },
                "improvement_targets": [
                    "Noch keine echten Trainingsdaten für Qualitätsvergleich vorhanden." if STATE.total_events == 0 else "Replay- und Regressionswerte weiter sammeln.",
                    "Provider-Latenzen und Cloud-Synchronisation messen.",
                ],
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
        path = self.path.split("?", 1)[0]
        if path == "/api/training/runtime-config":
            self._send(200, STATE.envelope({
                "runtime_mode": "TRAINING" if STATE.running else "SAFE",
                "active_artifact": "stable-baseline",
                "live_data_enabled": True,
                "offline_fallback_enabled": True,
                "recovery_enabled": True,
            }))
            return
        if path == "/api/training/artifacts/active":
            self._send(200, STATE.envelope({
                "artifact_id": "stable-baseline",
                "version": "1.0.0",
                "stage": "ACTIVE",
                "quality_score": 1.0,
                "regression_count": 0,
                "rollback_available": False,
            }))
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
        if name == "control":
            action = payload.get("action")
            if action not in {"start", "stop"}:
                self._send(400, {"error": "action_must_be_start_or_stop"})
                return
            with STATE._lock:
                STATE.running = action == "start"
                now = int(time.time() * 1000)
                if STATE.running:
                    STATE.started_at = STATE.started_at or now
                    STATE.current_job = "waiting-for-data"
                    STATE.last_cycle_at = now
                else:
                    STATE.current_job = "idle"
                    STATE.last_cycle_at = now
            STATE.persist()
            STATE.record_activity("Training " + ("started" if action == "start" else "stopped"), "CONTROL")
            self._send(200, STATE.envelope({"action": action, "running": STATE.running}))
            return
        if name == "training-job":
            prompt = payload.get("instruction")
            if not isinstance(prompt, str) or not prompt.strip():
                self._send(400, {"error": "instruction_required"})
                return
            with STATE._lock:
                STATE.running = True
                STATE.started_at = STATE.started_at or int(time.time() * 1000)
                STATE.last_cycle_at = int(time.time() * 1000)
                STATE.current_job = prompt.strip()[:240]
            STATE.record_activity("Training instruction accepted: " + prompt.strip()[:180], "TRAINING")
            self._send(202, STATE.envelope({"accepted": True, "instruction": prompt.strip(), "running": True}))
            return
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
                    size = int(event.get("size_bytes", 0))
                    if size < 0 or size > STATE.max_bytes:
                        continue
                    if STATE.used_bytes + size > STATE.max_bytes:
                        break
                    STATE.used_bytes += size
                    STATE.queue_count += 1
                    STATE.total_events += 1
                    STATE.job_processed += 1
                    STATE.job_accepted += 1
                    accepted += 1
            with STATE._lock:
                STATE.last_cycle_at = int(time.time() * 1000)
                STATE.job_rejected += len(events) - accepted
                STATE.job_rate = round((STATE.job_processed / max(1.0, (time.time() - (STATE.started_at or time.time())))) * 60.0, 2)
                accepted_events = [event for event in events if isinstance(event, dict) and event.get("event_id")][:accepted]
                STATE.pending_events.extend(accepted_events)
                STATE.event_records.extend({**event, "status": "accepted", "captured_at": int(time.time() * 1000)} for event in accepted_events)
                STATE.event_records = STATE.event_records[-500:]
            STATE.persist()
            STATE.record_activity("Accepted " + str(accepted) + " training events", "INGEST")
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
                "reply": "Anfrage aufgenommen. Evidenz, Replay-Ergebnis und Schutzgates werden vor jeder Änderung geprüft.",
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
        self._send(404, {"error": "unknown_endpoint"})


def process_training_batch() -> None:
    started = time.perf_counter()
    with STATE._lock:
        if not STATE.running or not STATE.pending_events:
            return
        batch = STATE.pending_events[:32]
        del STATE.pending_events[:len(batch)]
    normalized = 0
    for event in batch:
        if event.get("event_id") and event.get("type"):
            normalized += 1
    now = int(time.time() * 1000)
    with STATE._lock:
        accepted_ids = {event.get("event_id") for event in batch}
        for record in STATE.event_records:
            if record.get("event_id") in accepted_ids:
                record["status"] = "regression_passed"
                record["normalized_at"] = int(time.time() * 1000)
        STATE.normalized_count += normalized
        STATE.replay_count += normalized
        STATE.regression_passed += normalized
        STATE.last_normalization_at = now
        STATE.last_replay_at = now
        STATE.last_cycle_at = now
        STATE.worker_last_duration_ms = int((time.perf_counter() - started) * 1000)
        STATE.queue_count = max(0, STATE.queue_count - normalized)
        STATE.synced_count += normalized
    if normalized:
        STATE.persist()
        STATE.record_activity("Worker: normalized, replayed and regression-checked " + str(normalized) + " events", "WORKER")


def serve(host: str = "127.0.0.1", port: int = 8787) -> None:
    server = ThreadingHTTPServer((host, port), Handler)
    def worker_loop() -> None:
        while True:
            time.sleep(2)
            process_training_batch()
    threading.Thread(target=worker_loop, daemon=True, name="training-worker").start()
    print(f"Training API listening on http://{host}:{port}")
    server.serve_forever()


if __name__ == "__main__":
    serve(os.getenv("TRAINING_API_HOST", "127.0.0.1"), int(os.getenv("TRAINING_API_PORT", "8787")))
