#!/usr/bin/env python3
"""Traffic/knowledge hub for routing-platform.

Provider ingestion is authenticated, provenance-preserving and fail-closed.
Only current events from explicitly allowlisted sources are returned to routes.
The language model is not used to classify incidents, calculate delay, or reroute.
"""
from __future__ import annotations

import json
import math
import os
import re
import sqlite3
import subprocess
import sys
import threading
import time
import urllib.request
from urllib.error import HTTPError, URLError
import uuid
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

MAX_BODY_BYTES = 1_000_000
MAX_EVENTS_PER_BATCH = 500
MAX_ROUTE_POINTS = 5_000
MAX_EVENT_AGE_MS = 5 * 60_000
MAX_DESCRIPTION = 500
TRAINING_PRESETS = {"speech_clarity", "route_explanation", "traffic_summary", "hazard_summary", "gps_confidence"}
ALLOWED_KINDS = {"congestion", "accident", "roadworks", "closure", "hazard", "weather", "other"}
SAFE_ID = re.compile(r"^[A-Za-z0-9._:-]{1,120}$")


class HubError(ValueError):
    pass


def _text(value, name, limit, required=True):
    if value is None and not required:
        return None
    if not isinstance(value, str):
        raise HubError(f"{name} must be text")
    result = " ".join(value.strip().split())
    if (required and not result) or len(result) > limit:
        raise HubError(f"{name} is empty or too long")
    return result


def _integer(value, name, minimum=0, maximum=2**63 - 1):
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise HubError(f"{name} is outside its valid range")
    return value


def _coordinate(lat, lon):
    if isinstance(lat, bool) or isinstance(lon, bool):
        raise HubError("coordinates must be numeric")
    try:
        lat, lon = float(lat), float(lon)
    except (TypeError, ValueError) as error:
        raise HubError("coordinates must be numeric") from error
    if not math.isfinite(lat) or not math.isfinite(lon) or not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise HubError("coordinates are invalid")
    return lat, lon


def normalize_event(event, provider, attribution, now_ms):
    if not isinstance(event, dict):
        raise HubError("event must be an object")
    event_id = _text(event.get("id"), "id", 120)
    if not SAFE_ID.fullmatch(event_id):
        raise HubError("id contains unsupported characters")
    kind = _text(event.get("kind"), "kind", 32).lower()
    if kind not in ALLOWED_KINDS:
        raise HubError("unsupported event kind")
    lat, lon = _coordinate(event.get("latitude"), event.get("longitude"))
    observed = _integer(event.get("observedAtEpochMillis"), "observedAtEpochMillis")
    valid_until = _integer(event.get("validUntilEpochMillis"), "validUntilEpochMillis")
    if observed > now_ms + 5 * 60_000 or valid_until < observed or valid_until > observed + 7 * 24 * 60 * 60_000:
        raise HubError("event validity window is invalid")
    severity = _integer(event.get("severity"), "severity", 1, 5)
    confidence = event.get("confidence", 1.0)
    if isinstance(confidence, bool) or not isinstance(confidence, (int, float)) or not math.isfinite(confidence) or not 0.0 <= confidence <= 1.0:
        raise HubError("confidence must be between 0 and 1")
    delay = event.get("estimatedDelaySeconds")
    if delay is not None:
        delay = _integer(delay, "estimatedDelaySeconds", 0, 86_400)
    source_url = _text(event.get("sourceUrl"), "sourceUrl", 500, required=False)
    if source_url and not source_url.startswith("https://"):
        raise HubError("sourceUrl must use HTTPS")
    return {
        "id": event_id, "provider": provider, "attribution": attribution,
        "kind": kind, "severity": severity, "latitude": lat, "longitude": lon,
        "observedAtEpochMillis": observed, "validUntilEpochMillis": valid_until,
        "estimatedDelaySeconds": delay,
        "roadLabel": _text(event.get("roadLabel"), "roadLabel", 160, required=False),
        "description": _text(event.get("description"), "description", MAX_DESCRIPTION, required=False),
        "sourceUrl": source_url, "confidence": float(confidence),
    }


def parse_geojson(collection, provider, attribution, now_ms):
    """Adapter for allowlisted GeoJSON feeds with canonical property names."""
    if not isinstance(collection, dict) or collection.get("type") != "FeatureCollection":
        raise HubError("expected GeoJSON FeatureCollection")
    features = collection.get("features")
    if not isinstance(features, list) or len(features) > MAX_EVENTS_PER_BATCH:
        raise HubError("GeoJSON feature collection is invalid or too large")
    result = []
    for feature in features:
        if not isinstance(feature, dict) or feature.get("type") != "Feature":
            raise HubError("invalid GeoJSON feature")
        geometry = feature.get("geometry") or {}
        coordinates = geometry.get("coordinates") if geometry.get("type") == "Point" else None
        if not isinstance(coordinates, list) or len(coordinates) < 2:
            raise HubError("traffic feature requires Point geometry")
        props = feature.get("properties") or {}
        result.append(normalize_event({
            **props, "id": props.get("id", feature.get("id")),
            "longitude": coordinates[0], "latitude": coordinates[1],
        }, provider, attribution, now_ms))
    return result


def _distance_to_segment_m(point, start, end):
    lat, lon = point
    lat0 = math.radians(lat)
    scale_x = 111_320.0 * math.cos(lat0)
    scale_y = 111_320.0
    px, py = lon * scale_x, lat * scale_y
    ax, ay = start[1] * scale_x, start[0] * scale_y
    bx, by = end[1] * scale_x, end[0] * scale_y
    dx, dy = bx - ax, by - ay
    denom = dx * dx + dy * dy
    t = 0.0 if denom == 0 else min(1.0, max(0.0, ((px-ax)*dx + (py-ay)*dy) / denom))
    return math.hypot(px - (ax + t*dx), py - (ay + t*dy))


def route_corridor_events(events, geometry, now_ms, radius_m=500, max_age_ms=MAX_EVENT_AGE_MS):
    if not isinstance(geometry, list) or not 2 <= len(geometry) <= MAX_ROUTE_POINTS:
        raise HubError("route geometry must contain 2 to 5000 points")
    points = [_coordinate(point[0], point[1]) for point in geometry
              if isinstance(point, (list, tuple)) and len(point) >= 2]
    if len(points) != len(geometry):
        raise HubError("route geometry contains invalid points")
    admitted = []
    for event in events:
        age = now_ms - event["observedAtEpochMillis"]
        if age < 0 or age > max_age_ms or event["validUntilEpochMillis"] < now_ms:
            continue
        if min(_distance_to_segment_m((event["latitude"], event["longitude"]), a, b)
               for a, b in zip(points, points[1:])) <= radius_m:
            admitted.append({**event, "ageSeconds": age // 1000})
    return sorted(admitted, key=lambda e: (-e["severity"], e["ageSeconds"], e["id"]))[:100]


class HubStore:
    def __init__(self, database, providers):
        self.database = database
        self.providers = providers
        self.lock = threading.RLock()
        with self.connect() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS traffic_events(
                  provider TEXT NOT NULL, event_id TEXT NOT NULL, payload TEXT NOT NULL,
                  PRIMARY KEY(provider,event_id));
                CREATE TABLE IF NOT EXISTS knowledge_items(
                  id TEXT PRIMARY KEY, payload TEXT NOT NULL, approved INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS training_examples(
                  id TEXT PRIMARY KEY, payload TEXT NOT NULL, created_at INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS training_runs(
                  id TEXT PRIMARY KEY, name TEXT NOT NULL, preset TEXT NOT NULL,
                  status TEXT NOT NULL, progress INTEGER NOT NULL, stage TEXT NOT NULL,
                  example_count INTEGER NOT NULL, metrics TEXT NOT NULL,
                  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, error TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS inference_metrics(
                  id TEXT PRIMARY KEY, model_id TEXT NOT NULL, task TEXT NOT NULL,
                  outcome TEXT NOT NULL, elapsed_ms INTEGER NOT NULL, created_at INTEGER NOT NULL);
                CREATE INDEX IF NOT EXISTS inference_metrics_created
                  ON inference_metrics(created_at);
            """)

    def connect(self):
        db = sqlite3.connect(self.database, timeout=10)
        db.row_factory = sqlite3.Row
        return db

    def ingest(self, provider, attribution, events, now_ms):
        provider = _text(provider, "provider", 80)
        attribution = _text(attribution, "attribution", 160)
        if provider not in self.providers:
            raise HubError("provider is not configured in the source allowlist")
        if not isinstance(events, list) or not 1 <= len(events) <= MAX_EVENTS_PER_BATCH:
            raise HubError("event batch must contain 1 to 500 events")
        normalized = [normalize_event(e, provider, attribution, now_ms) for e in events]
        with self.lock, self.connect() as db:
            for event in normalized:
                db.execute("INSERT INTO traffic_events VALUES(?,?,?) ON CONFLICT(provider,event_id) DO UPDATE SET payload=excluded.payload",
                           (provider, event["id"], json.dumps(event, separators=(",", ":"))))
        return len(normalized)

    def events(self):
        with self.connect() as db:
            return [json.loads(row["payload"]) for row in db.execute("SELECT payload FROM traffic_events")]

    def add_knowledge(self, item):
        if not isinstance(item, dict):
            raise HubError("knowledge item must be an object")
        item_id = _text(item.get("id"), "id", 120)
        if not SAFE_ID.fullmatch(item_id):
            raise HubError("knowledge id contains unsupported characters")
        source_url = _text(item.get("sourceUrl"), "sourceUrl", 500)
        if not source_url.startswith("https://"):
            raise HubError("knowledge source must use HTTPS")
        payload = {
            "id": item_id, "title": _text(item.get("title"), "title", 200),
            "text": _text(item.get("text"), "text", 4000),
            "sourceUrl": source_url,
            "attribution": _text(item.get("attribution"), "attribution", 200),
            "license": _text(item.get("license"), "license", 120),
            "approved": item.get("approved") is True,
            "updatedAtEpochMillis": _integer(item.get("updatedAtEpochMillis"), "updatedAtEpochMillis"),
        }
        with self.lock, self.connect() as db:
            db.execute("INSERT INTO knowledge_items VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload, approved=excluded.approved",
                       (item_id, json.dumps(payload, separators=(",", ":")), int(payload["approved"])))
        return payload

    def search_knowledge(self, query, limit=10):
        terms = [t.lower() for t in re.findall(r"[\w-]{2,}", query)[:12]]
        if not terms:
            return []
        with self.connect() as db:
            rows = db.execute("SELECT payload FROM knowledge_items WHERE approved=1").fetchall()
        ranked = []
        for row in rows:
            item = json.loads(row["payload"])
            searchable = (item["title"] + " " + item["text"]).lower()
            score = sum(searchable.count(term) for term in terms)
            if score:
                ranked.append((score, item))
        return [item for _, item in sorted(ranked, key=lambda pair: (-pair[0], pair[1]["id"]))[:limit]]

    def add_training_example(self, example):
        if not isinstance(example, dict):
            raise HubError("training example must be an object")
        ex_id = _text(example.get("id"), "id", 120)
        if not SAFE_ID.fullmatch(ex_id):
            raise HubError("training id contains unsupported characters")
        task = _text(example.get("task"), "task", 40)
        if task not in TRAINING_PRESETS:
            raise HubError("unsupported training task")
        prompt = _text(example.get("prompt"), "prompt", 2000)
        answer = _text(example.get("expectedAnswer"), "expectedAnswer", 2000)
        source_ids = example.get("knowledgeIds", [])
        if not isinstance(source_ids, list) or len(source_ids) > 16:
            raise HubError("knowledgeIds must be a short list")
        if task in {"route_explanation", "traffic_summary", "hazard_summary"} and not source_ids:
            raise HubError("route/traffic training examples require approved knowledge references")
        with self.connect() as db:
            for source_id in source_ids:
                row = db.execute("SELECT approved FROM knowledge_items WHERE id=?", (source_id,)).fetchone()
                if row is None or row["approved"] != 1:
                    raise HubError("training example references missing or unapproved knowledge")
        created = _integer(example.get("createdAtEpochMillis"), "createdAtEpochMillis")
        payload = {"id": ex_id, "task": task, "prompt": prompt, "expectedAnswer": answer,
                   "knowledgeIds": source_ids, "createdAtEpochMillis": created}
        with self.lock, self.connect() as db:
            db.execute("INSERT INTO training_examples VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload,created_at=excluded.created_at",
                       (ex_id, json.dumps(payload, separators=(",", ":")), created))
        return payload

    def training_dataset(self):
        with self.connect() as db:
            return [json.loads(row["payload"]) for row in
                    db.execute("SELECT payload FROM training_examples ORDER BY created_at,id")]

    def create_training_run(self, name, preset):
        name = _text(name, "name", 80)
        if preset not in TRAINING_PRESETS:
            raise HubError("unsupported training preset")
        run_id = uuid.uuid4().hex[:12]
        now = _now_ms()
        with self.lock, self.connect() as db:
            db.execute("INSERT INTO training_runs VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                       (run_id, name, preset, "queued", 0, "Wartet auf Start", 0,
                        "{}", now, now, ""))
        return self.training_run(run_id)

    def training_run(self, run_id):
        with self.connect() as db:
            row = db.execute("SELECT * FROM training_runs WHERE id=?", (run_id,)).fetchone()
        return dict(row) if row else None

    def training_runs(self):
        with self.connect() as db:
            return [dict(row) for row in db.execute(
                "SELECT * FROM training_runs ORDER BY created_at DESC LIMIT 50")]

    def update_training_run(self, run_id, **changes):
        allowed = {"status", "progress", "stage", "example_count", "metrics", "error"}
        if not changes or set(changes) - allowed:
            raise HubError("invalid training run update")
        values = dict(changes)
        if "metrics" in values:
            values["metrics"] = json.dumps(values["metrics"], separators=(",", ":"))
        values["updated_at"] = _now_ms()
        assignments = ", ".join(key + "=?" for key in values)
        with self.lock, self.connect() as db:
            db.execute("UPDATE training_runs SET " + assignments + " WHERE id=?",
                       (*values.values(), run_id))
        return self.training_run(run_id)

    def record_inference_metric(self, value):
        if not isinstance(value, dict):
            raise HubError("metric must be an object")
        metric_id = value.get("id") or uuid.uuid4().hex
        metric_id = _text(metric_id, "id", 120)
        if not SAFE_ID.fullmatch(metric_id):
            raise HubError("metric id contains unsupported characters")
        model_id = _text(value.get("modelId"), "modelId", 120)
        task = _text(value.get("task"), "task", 40)
        if task not in TRAINING_PRESETS and task not in {"conversation", "navigation_status"}:
            raise HubError("unsupported inference task")
        outcome = _text(value.get("outcome"), "outcome", 16)
        if outcome not in {"success", "fallback", "error"}:
            raise HubError("outcome must be success, fallback, or error")
        elapsed = _integer(value.get("elapsedMillis"), "elapsedMillis", 0, 120_000)
        created = _now_ms()
        with self.lock, self.connect() as db:
            db.execute("INSERT OR IGNORE INTO inference_metrics VALUES(?,?,?,?,?,?)",
                       (metric_id, model_id, task, outcome, elapsed, created))
            # Bound storage even for a misconfigured high-frequency client.
            db.execute("DELETE FROM inference_metrics WHERE created_at < ?", (created - 90 * 24 * 60 * 60_000,))
            db.execute("""DELETE FROM inference_metrics WHERE id IN (
                          SELECT id FROM inference_metrics ORDER BY created_at DESC LIMIT -1 OFFSET 100000)""")
        return {"id": metric_id, "accepted": True, "recordedAtEpochMillis": created}

    def inference_summary(self):
        with self.connect() as db:
            rows = db.execute(
                "SELECT task,outcome,elapsed_ms,created_at FROM inference_metrics "
                "WHERE created_at >= ? ORDER BY created_at",
                (_now_ms() - 30 * 24 * 60 * 60_000,)).fetchall()
        total = len(rows)
        successful = sum(row["outcome"] == "success" for row in rows)
        fallback = sum(row["outcome"] == "fallback" for row in rows)
        errors = sum(row["outcome"] == "error" for row in rows)
        latencies = sorted(row["elapsed_ms"] for row in rows if row["outcome"] == "success")
        p95 = latencies[min(len(latencies) - 1, math.ceil(len(latencies) * .95) - 1)] if latencies else None
        by_task = {}
        for row in rows:
            item = by_task.setdefault(row["task"], {"success": 0, "fallback": 0, "error": 0})
            item[row["outcome"]] += 1
        return {"sampleCount30d": total, "successCount30d": successful,
                "fallbackCount30d": fallback, "errorCount30d": errors,
                "successRate30d": round(successful / total, 4) if total else None,
                "p95LatencyMillis30d": p95, "byTask": by_task}


def _now_ms():
    return int(time.time() * 1000)


def _ai_endpoint():
    """Read a fixed, operator-configured OpenAI-compatible chat endpoint."""
    endpoint = os.environ.get("AI_API_BASE_URL", "").strip()
    if not endpoint:
        return None
    parsed = urlparse(endpoint)
    if parsed.scheme != "https" and not (
        parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost", "::1"}
    ):
        raise HubError("AI_API_BASE_URL must use HTTPS (HTTP is allowed only on loopback)")
    if not parsed.hostname or parsed.username or parsed.password or parsed.fragment or parsed.query:
        raise HubError("AI_API_BASE_URL is invalid")
    path = parsed.path.rstrip("/")
    if path.endswith("/chat/completions"):
        return endpoint
    suffix = "/chat/completions" if path.endswith("/v1") else "/v1/chat/completions"
    return endpoint.rstrip("/") + suffix


def _generate_ai_reply(message):
    """Single-turn conversational inference; never used for route decisions."""
    message = _text(message, "message", 2_000)
    endpoint = _ai_endpoint()
    if not endpoint:
        raise HubError("AI provider is not configured on the hub")
    model = _text(os.environ.get("AI_MODEL_ID", ""), "AI_MODEL_ID", 160)
    try:
        timeout = int(os.environ.get("AI_TIMEOUT_SECONDS", "25"))
    except ValueError as error:
        raise HubError("AI_TIMEOUT_SECONDS must be an integer") from error
    if not 1 <= timeout <= 60:
        raise HubError("AI_TIMEOUT_SECONDS must be between 1 and 60")
    payload = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": (
                "Du bist die Gesprächs-KI der Routing-App. Antworte klar und knapp auf Deutsch. "
                "Diese Schnittstelle ist nur für allgemeine Unterhaltung und Erklärungen. "
                "Berechne, ändere oder bestätige niemals Routen, Abbiegungen, Fahrspuren, "
                "Geschwindigkeiten oder Gefahren. Verweise dafür auf die verifizierte "
                "Navigationsanzeige. Erfinde keine aktuellen Verkehrs- oder Sicherheitsfakten."
            )},
            {"role": "user", "content": message},
        ],
        "temperature": 0.6,
        "max_tokens": 400,
    }, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    api_key = os.environ.get("AI_API_KEY", "")
    if api_key:
        headers["Authorization"] = "Bearer " + api_key
    request = urllib.request.Request(endpoint, data=payload, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            result = json.load(response)
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError, OSError) as error:
        raise HubError("AI provider is currently unavailable") from error
    try:
        answer = result["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as error:
        raise HubError("AI provider returned an unsupported response") from error
    if not isinstance(answer, str) or not answer.strip() or len(answer) > 8_000:
        raise HubError("AI provider returned an empty or oversized response")
    return {"reply": answer.strip(), "model": model, "task": "conversation"}


def _start_training_run(store, run_id):
    with store.lock:
        if getattr(store, "_active_training_run", False):
            raise HubError("another training run is already active")
        store._active_training_run = True

    def work():
        try:
            run = store.training_run(run_id)
            store.update_training_run(run_id, status="running", progress=10, stage="Datensatz wird geladen")
            examples = store.training_dataset()
            selected = [e for e in examples if e["task"] == run["preset"]]
            store.update_training_run(run_id, progress=45, stage="Beispiele und Quellen werden geprüft",
                                      example_count=len(selected))
            ids = [e["id"] for e in selected]
            duplicate_count = len(ids) - len(set(ids))
            approved_refs = 0
            with store.connect() as db:
                for example in selected:
                    for source_id in example.get("knowledgeIds", []):
                        row = db.execute("SELECT approved FROM knowledge_items WHERE id=?", (source_id,)).fetchone()
                        if row and row["approved"] == 1:
                            approved_refs += 1
                        else:
                            raise HubError("training data contains an unapproved or missing source")
            if duplicate_count:
                raise HubError("training data contains duplicate example IDs")
            store.update_training_run(run_id, progress=75, stage="Integrity check complete")
            store.update_training_run(run_id, status="completed", progress=100,
                                      stage="Datensatzprüfung abgeschlossen; Modellgewichte unverändert",
                                      metrics={"examplesChecked": len(selected),
                                               "approvedKnowledgeReferences": approved_refs,
                                               "duplicateIds": duplicate_count,
                                               "weightUpdate": False,
                                               "evaluationScore": None})
        except Exception as error:
            store.update_training_run(run_id, status="failed", stage="Prüfung fehlgeschlagen",
                                      error=str(error)[:240])
        finally:
            with store.lock:
                store._active_training_run = False

    threading.Thread(target=work, name="training-check-" + run_id, daemon=True).start()


def _start_lora_run(store, run_id, server_port, api_token):
    """Launch the real local LoRA worker and persist only truthful job state."""
    with store.lock:
        if getattr(store, "_active_training_run", False):
            raise HubError("another training run is already active")
        store._active_training_run = True
    try:
        enabled = os.environ.get("HUB_LORA_ENABLED", "").lower() == "true"
        revision = os.environ.get("MODEL_REVISION", "")
        trainer_version = os.environ.get("TRAINER_EXPECTED_VERSION", "")
        if not enabled:
            raise HubError("LoRA runner is disabled; set HUB_LORA_ENABLED=true")
        if not re.fullmatch(r"[0-9a-fA-F]{40,64}", revision):
            raise HubError("MODEL_REVISION must be a full immutable model commit hash")
        if not trainer_version:
            raise HubError("TRAINER_EXPECTED_VERSION must pin the installed trainer")
        run = store.training_run(run_id)
        if not run or run["status"] != "queued":
            raise HubError("LoRA run is not queued")
        root = Path(__file__).resolve().parent.parent
        runner = root / "tools" / "lora_runner.py"
        if not runner.is_file():
            raise HubError("LoRA runner script is unavailable")
        output_root = Path(os.environ.get("LORA_OUTPUT_DIR", str(root / "lora-output"))).expanduser().resolve()
        output_dir = output_root / (run_id + "-" + str(int(time.time())))
        output_dir.mkdir(parents=True, exist_ok=False)
        log_path = output_dir / "trainer.log"
        command = [
            sys.executable, str(runner), "--hub", f"http://127.0.0.1:{server_port}",
            "--trainer-version", trainer_version, "--revision", revision,
            "--model", os.environ.get("LORA_MODEL_ID", "Qwen/Qwen3.5-4B-Base"),
            "--template", os.environ.get("LORA_TEMPLATE", "qwen3_nothink"),
            "--preset", run["preset"], "--output-dir", str(output_dir / "adapter"),
        ]
        child_env = dict(os.environ)
        child_env["HUB_API_TOKEN"] = api_token
        with log_path.open("w", encoding="utf-8") as log:
            process = subprocess.Popen(command, cwd=root, env=child_env,
                                       stdout=log, stderr=subprocess.STDOUT)
        store.update_training_run(run_id, status="running", progress=0,
            stage="LoRA-Prozess läuft; kein synthetischer Schrittfortschritt",
            metrics={"mode":"lora", "pid":process.pid, "logPath":str(log_path),
                     "outputDir":str(output_dir), "weightUpdate":False})
    except Exception as error:
        with store.lock:
            store._active_training_run = False
        try:
            failed = store.training_run(run_id)
            if failed and failed["status"] == "queued":
                try:
                    metrics = json.loads(failed.get("metrics") or "{}")
                except (TypeError, json.JSONDecodeError):
                    metrics = {}
                store.update_training_run(run_id, status="failed", progress=0,
                    stage="LoRA-Vorabprüfung fehlgeschlagen; Modellgewichte unverändert",
                    error=str(error)[:240], metrics={**metrics, "mode":"lora", "weightUpdate":False})
        except Exception:
            pass
        raise

    def monitor():
        try:
            return_code = process.wait()
            current = store.training_run(run_id)
            metrics = {}
            try:
                metrics = json.loads(current["metrics"] or "{}")
            except (TypeError, json.JSONDecodeError):
                pass
            if return_code == 0:
                store.update_training_run(run_id, status="completed", progress=100,
                    stage="LoRA-Training beendet; Adapter und Manifest liegen im Ausgabepfad",
                    metrics={**metrics, "weightUpdate":True, "exitCode":0})
            else:
                store.update_training_run(run_id, status="failed", progress=0,
                    stage="LoRA-Training fehlgeschlagen; Details stehen im lokalen Trainer-Log",
                    error=f"Trainer exit code {return_code}",
                    metrics={**metrics, "weightUpdate":False, "exitCode":return_code})
        finally:
            with store.lock:
                store._active_training_run = False

    threading.Thread(target=monitor, name="lora-monitor-" + run_id, daemon=True).start()


class HubHandler(BaseHTTPRequestHandler):
    store = None
    api_token = ""
    max_age_ms = MAX_EVENT_AGE_MS

    def _send(self, code, value):
        body = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self):
        supplied = self.headers.get("Authorization", "")
        return bool(self.api_token) and supplied == "Bearer " + self.api_token

    def _body(self):
        length = int(self.headers.get("Content-Length", "0"))
        if not 0 < length <= MAX_BODY_BYTES:
            raise HubError("request body size is invalid")
        try:
            return json.loads(self.rfile.read(length))
        except (json.JSONDecodeError, UnicodeDecodeError) as error:
            raise HubError("request body is not valid JSON") from error

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/dashboard":
            try:
                page = (Path(__file__).parent / "static" / "intelligence_dashboard.html").read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(page)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(page)
            except OSError:
                self._send(503, {"error": "dashboard_unavailable"})
            return
        if parsed.path == "/v1/status":
            events = self.store.events()
            with self.store.connect() as db:
                knowledge_count = db.execute("SELECT COUNT(*) FROM knowledge_items WHERE approved=1").fetchone()[0]
            self._send(200, {"status": "ok", "trafficProviderCount": len(self.store.providers),
                             "trafficEventCount": len(events),
                             "trafficData": "configured" if self.store.providers else "unavailable",
                             "approvedKnowledgeCount": knowledge_count,
                             "trainingExampleCount": len(self.store.training_dataset()),
                             "model": {"id": os.environ.get("LOCAL_MODEL_ID", "Qwen2.5-1.5B-Instruct-Q4_K_M"),
                                       "runtime": "device-local", "weightsTraining": "configured" if os.environ.get("HUB_LORA_ENABLED", "").lower() == "true" else "not_configured"}})
            return
        if parsed.path == "/v1/dashboard":
            if not self._authorized():
                self._send(401, {"error": "authentication_required"}); return
            events = self.store.events()
            examples = self.store.training_dataset()
            with self.store.connect() as db:
                knowledge_count = db.execute("SELECT COUNT(*) FROM knowledge_items WHERE approved=1").fetchone()[0]
            self._send(200, {"status": {"trafficProviderCount": len(self.store.providers),
                                       "trafficEventCount": len(events),
                                       "approvedKnowledgeCount": knowledge_count,
                                       "trainingExampleCount": len(examples),
                                       "trafficData": "configured" if self.store.providers else "unavailable",
                                       "loraRunnerConfigured": (
                                           os.environ.get("HUB_LORA_ENABLED", "").lower() == "true"
                                           and bool(re.fullmatch(r"[0-9a-fA-F]{40,64}", os.environ.get("MODEL_REVISION", "")))
                                           and bool(os.environ.get("TRAINER_EXPECTED_VERSION", "")))},
                             "model": {"id": os.environ.get("LOCAL_MODEL_ID", "Qwen2.5-1.5B-Instruct-Q4_K_M"),
                                       "runtime": "device-local", "weightsTraining": "configured" if os.environ.get("HUB_LORA_ENABLED", "").lower() == "true" else "not_configured",
                                       "quality": "not_measured"},
                             "inference": self.store.inference_summary(),
                             "runs": self.store.training_runs()})
            return
        if parsed.path == "/v1/ai/status":
            if not self._authorized():
                self._send(401, {"error": "authentication_required"}); return
            try:
                configured = _ai_endpoint() is not None and bool(os.environ.get("AI_MODEL_ID", "").strip())
            except HubError:
                configured = False
            self._send(200, {"status": "configured" if configured else "not_configured",
                             "model": os.environ.get("AI_MODEL_ID", "") if configured else "",
                             "scope": "conversation_only", "storesPrompts": False})
            return
        if parsed.path == "/v1/training/runs":
            if not self._authorized():
                self._send(401, {"error": "authentication_required"}); return
            self._send(200, {"runs": self.store.training_runs()})
            return
        if parsed.path == "/v1/knowledge/search":
            query = parse_qs(parsed.query).get("q", [""])[0][:300]
            self._send(200, {"items": self.store.search_knowledge(query)})
            return
        if parsed.path == "/v1/training/dataset":
            if not self._authorized():
                self._send(401, {"error": "authentication_required"}); return
            self._send(200, {"examples": self.store.training_dataset()})
            return
        if parsed.path == "/v1/traffic/events":
            if not self._authorized():
                self._send(401, {"error": "authentication_required"}); return
            self._send(200, {"events": self.store.events()})
            return
        self._send(404, {"error": "not_found"})

    def do_POST(self):
        if not self._authorized():
            self._send(401, {"error": "authentication_required"}); return
        try:
            body = self._body()
            if self.path == "/v1/traffic/events":
                count = self.store.ingest(body.get("provider"), body.get("attribution"),
                                          body.get("events"), _now_ms())
                self._send(202, {"accepted": count})
            elif self.path == "/v1/traffic/route-events":
                geometry = body.get("geometry")
                events = route_corridor_events(self.store.events(), geometry, _now_ms(), max_age_ms=self.max_age_ms)
                self._send(200, {"status": "current" if events else "no_current_events", "events": events})
            elif self.path == "/v1/knowledge/items":
                item = self.store.add_knowledge(body)
                self._send(201, item)
            elif self.path == "/v1/training/examples":
                example = self.store.add_training_example(body)
                self._send(201, example)
            elif self.path == "/v1/metrics/inference":
                result = self.store.record_inference_metric(body)
                self._send(202, result)
            elif self.path == "/v1/ai/chat":
                if body.get("task", "conversation") != "conversation":
                    raise HubError("AI chat is restricted to conversation; navigation remains deterministic")
                started = time.monotonic()
                result = _generate_ai_reply(body.get("message"))
                elapsed = min(120_000, int((time.monotonic() - started) * 1000))
                self.store.record_inference_metric({
                    "modelId": result["model"], "task": "conversation",
                    "outcome": "success", "elapsedMillis": elapsed,
                })
                self._send(200, result)
            elif self.path == "/v1/training/runs":
                mode = body.get("mode", "validate")
                if mode not in {"validate", "lora"}:
                    raise HubError("mode must be validate or lora")
                run = self.store.create_training_run(body.get("name", "Sondertraining"),
                                                     body.get("preset"))
                if mode == "lora":
                    self.store.update_training_run(run["id"], metrics={"mode":"lora"})
                if body.get("start") is True:
                    if mode == "lora":
                        _start_lora_run(self.store, run["id"], self.server.server_port, self.api_token)
                    else:
                        _start_training_run(self.store, run["id"])
                self._send(202, self.store.training_run(run["id"]))
            elif self.path.startswith("/v1/training/runs/") and self.path.endswith("/start"):
                run_id = self.path[len("/v1/training/runs/"):-len("/start")].strip("/")
                run = self.store.training_run(run_id)
                if not run:
                    self._send(404, {"error": "run_not_found"}); return
                if run["status"] != "queued":
                    self._send(409, {"error": "run_not_queued"}); return
                try:
                    run_metrics = json.loads(run.get("metrics") or "{}")
                except (TypeError, json.JSONDecodeError):
                    run_metrics = {}
                if run_metrics.get("mode") == "lora":
                    _start_lora_run(self.store, run_id, self.server.server_port, self.api_token)
                else:
                    _start_training_run(self.store, run_id)
                self._send(202, self.store.training_run(run_id))
            else:
                self._send(404, {"error": "not_found"})
        except HubError as error:
            self._send(422, {"error": "invalid_input", "detail": str(error)})
        except Exception:
            self._send(503, {"error": "temporarily_unavailable"})

    def log_message(self, fmt, *args):
        pass


def create_server(host=None, port=None, database=None, providers=None, api_token=None):
    host = host or os.environ.get("HUB_BIND_HOST", "127.0.0.1")
    port = int(port if port is not None else os.environ.get("HUB_PORT", "8090"))
    token = api_token if api_token is not None else os.environ.get("HUB_API_TOKEN", "")
    if host not in {"127.0.0.1", "localhost", "0.0.0.0", "::"}:
        raise ValueError("hub bind host must be loopback or wildcard")
    if host in {"0.0.0.0", "::"} and len(token) < 32:
        raise ValueError("external bind requires HUB_API_TOKEN of at least 32 characters")
    configured = providers if providers is not None else {
        value.strip() for value in os.environ.get("TRAFFIC_PROVIDER_ALLOWLIST", "").split(",") if value.strip()
    }
    db_path = database or os.environ.get("HUB_DATABASE", "routing-intelligence.sqlite3")
    handler = type("ConfiguredHubHandler", (HubHandler,), {
        "store": HubStore(db_path, configured), "api_token": token,
    })
    return ThreadingHTTPServer((host, port), handler)


if __name__ == "__main__":
    server = create_server()
    from backend.traffic_feed_poller import start_configured_poller
    poller = start_configured_poller(server.RequestHandlerClass.store)
    try:
        server.serve_forever()
    finally:
        if poller is not None:
            poller.close()
        server.server_close()
