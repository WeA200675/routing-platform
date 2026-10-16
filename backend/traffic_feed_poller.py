"""HTTPS GeoJSON source collector for traffic_intelligence_hub."""
from __future__ import annotations
import json
import os
import re
import threading
import time
import urllib.request


def _validate_autobahn_url(url):
    match = re.fullmatch(
        r"https://verkehr\.autobahn\.de/o/autobahn/(A[0-9]{1,3}[A-Z]?)/services/(warning|roadworks|closure)",
        url,
    )
    if not match:
        raise ValueError("Autobahn feed URL must be a documented road/service endpoint")
    return match.group(1), match.group(2)


def load_config(raw, providers):
    if not raw:
        return []
    config = json.loads(raw)
    if not isinstance(config, list) or len(config) > 16:
        raise ValueError("TRAFFIC_FEEDS_JSON must be a list of at most 16 feeds")
    feeds, ids = [], set()
    for item in config:
        if not isinstance(item, dict):
            raise ValueError("feed must be an object")
        feed_id = str(item.get("id", "")).strip()
        provider = str(item.get("provider", "")).strip()
        url = str(item.get("url", "")).strip()
        attribution = str(item.get("attribution", "")).strip()
        interval = item.get("intervalSeconds", 120)
        feed_format = item.get("format", "geojson")
        if not re.fullmatch(r"[A-Za-z0-9._:-]{1,80}", feed_id) or feed_id in ids:
            raise ValueError("feed ID is invalid or duplicated")
        if provider not in providers:
            raise ValueError("feed provider is not allowlisted")
        if not url.startswith("https://") or len(url) > 1000:
            raise ValueError("feed URL must use HTTPS")
        if not attribution or len(attribution) > 160:
            raise ValueError("feed attribution is missing or too long")
        if isinstance(interval, bool) or not isinstance(interval, int) or not 30 <= interval <= 1800:
            raise ValueError("feed interval must be between 30 and 1800 seconds")
        if feed_format not in {"geojson", "autobahn"}:
            raise ValueError("unsupported feed format")
        feed = {"id": feed_id, "provider": provider, "url": url,
                "attribution": attribution, "intervalSeconds": interval,
                "format": feed_format}
        auth_env = item.get("authorizationEnv")
        if auth_env is not None and not re.fullmatch(r"[A-Z][A-Z0-9_]{0,63}", str(auth_env)):
            raise ValueError("authorizationEnv must be an environment variable name")
        if feed_format == "autobahn":
            if auth_env is not None:
                raise ValueError("public Autobahn API does not use bearer credentials")
            feed["roadId"], feed["service"] = _validate_autobahn_url(url)
        elif feed_format == "geojson":
            feed["authorizationEnv"] = auth_env
        feeds.append(feed)
        ids.add(feed_id)
    return feeds


def parse_autobahn_response(payload, feed, now_ms):
    """Normalize one official Autobahn API service response into hub events."""
    import hashlib
    from backend.traffic_intelligence_hub import HubError

    if not isinstance(payload, dict):
        raise HubError("Autobahn response must be a JSON object")
    service = feed["service"]
    items = payload.get(service)
    if not isinstance(items, list) or len(items) > 500:
        raise HubError("Autobahn response has an invalid service list")
    result = []
    for item in items:
        if not isinstance(item, dict):
            continue
        raw_id = item.get("identifier")
        title = item.get("title")
        if not isinstance(raw_id, str) or not raw_id.strip() or not isinstance(title, str) or not title.strip():
            continue
        point = item.get("point")
        lat = lon = None
        if isinstance(point, str):
            parts = point.split(",")
            if len(parts) == 2:
                try:
                    lon, lat = float(parts[0]), float(parts[1])
                except ValueError:
                    continue
        elif isinstance(point, dict):
            try:
                lat, lon = float(point.get("lat")), float(point.get("long", point.get("lon")))
            except (TypeError, ValueError):
                continue
        if lat is None or lon is None:
            continue
        description_value = item.get("description", [])
        if isinstance(description_value, list):
            description = " ".join(str(part).strip() for part in description_value if part)
        elif isinstance(description_value, str):
            description = description_value.strip()
        else:
            description = ""
        text = (title + " " + description).lower()
        if service == "closure":
            kind, severity = "closure", 5
        elif any(word in text for word in ("unfall", "accident", "geisterfahrer", "fahrzeugbrand")):
            kind, severity = "accident", 5
        elif service == "roadworks":
            kind, severity = "roadworks", 3
        elif any(word in text for word in ("stau", "störung", "stockender", "congestion", "jam")):
            kind, severity = "congestion", 4
        else:
            kind, severity = "hazard", 3
        event_id = "ab-" + hashlib.sha256(raw_id.encode("utf-8")).hexdigest()[:32]
        result.append({
            "id": event_id, "kind": kind, "severity": severity,
            "latitude": lat, "longitude": lon,
            "observedAtEpochMillis": now_ms,
            "validUntilEpochMillis": now_ms + 5 * 60_000,
            "estimatedDelaySeconds": None,
            "roadLabel": feed["roadId"],
            "description": (title.strip() + (": " + description if description else ""))[:500],
            "sourceUrl": feed["url"], "confidence": 0.85,
        })
    return result


class GeoJSONFeedPoller(threading.Thread):
    def __init__(self, feeds, store, clock_ms=lambda: int(time.time()*1000),
                 opener=urllib.request.urlopen, environ=None):
        super().__init__(name="traffic-feed-poller", daemon=True)
        self.feeds = list(feeds)
        self.store = store
        self.clock_ms = clock_ms
        self.opener = opener
        self.environ = os.environ if environ is None else environ
        self.stop_event = threading.Event()
        self.next_due = {}
        self.status = {feed["id"]: "not_polled" for feed in feeds}

    def poll_once(self, monotonic_now=0.0):
        now_ms = self.clock_ms()
        for feed in self.feeds:
            if monotonic_now < self.next_due.get(feed["id"], 0.0):
                continue
            try:
                headers = {"Accept":"application/geo+json, application/json",
                           "User-Agent":"routing-platform-traffic-hub/1"}
                env_name = feed.get("authorizationEnv")
                if env_name:
                    secret = self.environ.get(env_name, "")
                    if not secret:
                        raise RuntimeError("provider credential unavailable")
                    headers["Authorization"] = "Bearer " + secret
                request = urllib.request.Request(feed["url"], headers=headers)
                with self.opener(request, timeout=12) as response:
                    raw = response.read(2_000_001)
                if len(raw) > 2_000_000:
                    raise ValueError("feed response too large")
                collection = json.loads(raw)
                from backend import traffic_intelligence_hub as hub
                if feed.get("format", "geojson") == "autobahn":
                    events = parse_autobahn_response(collection, feed, now_ms)
                else:
                    events = hub.parse_geojson(collection, feed["provider"], feed["attribution"], now_ms)
                if events:
                    self.store.ingest(feed["provider"], feed["attribution"], events, now_ms)
                self.status[feed["id"]] = "current"
            except Exception:
                # Provider payloads and credentials are deliberately not logged.
                self.status[feed["id"]] = "unavailable"
            self.next_due[feed["id"]] = monotonic_now + feed["intervalSeconds"]

    def run(self):
        while not self.stop_event.is_set():
            self.poll_once(time.monotonic())
            self.stop_event.wait(5)

    def close(self):
        self.stop_event.set()


def start_configured_poller(store):
    feeds = load_config(os.environ.get("TRAFFIC_FEEDS_JSON", ""), store.providers)
    if not feeds:
        return None
    poller = GeoJSONFeedPoller(feeds, store)
    poller.start()
    return poller
