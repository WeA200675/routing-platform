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
import threading
import time
import urllib.request
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

MAX_BODY_BYTES = 1_000_000
MAX_EVENTS_PER_BATCH = 500
MAX_ROUTE_POINTS = 5_000
MAX_EVENT_AGE_MS = 5 * 60_000
MAX_DESCRIPTION = 500
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
        if task not in {"speech_clarity", "route_explanation", "traffic_summary", "hazard_summary"}:
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


def _now_ms():
    return int(time.time() * 1000)


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
        if parsed.path == "/v1/status":
            self._send(200, {"status": "ok", "trafficProviderCount": len(self.store.providers),
                             "trafficEventCount": len(self.store.events()),
                             "trafficData": "configured" if self.store.providers else "unavailable"})
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
