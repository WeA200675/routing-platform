"""HTTPS GeoJSON source collector for traffic_intelligence_hub."""
from __future__ import annotations
import json
import os
import re
import threading
import time
import urllib.request


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
        auth_env = item.get("authorizationEnv")
        if auth_env is not None and not re.fullmatch(r"[A-Z][A-Z0-9_]{0,63}", str(auth_env)):
            raise ValueError("authorizationEnv must be an environment variable name")
        feeds.append({"id": feed_id, "provider": provider, "url": url,
                      "attribution": attribution, "intervalSeconds": interval,
                      "authorizationEnv": auth_env})
        ids.add(feed_id)
    return feeds


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
                events = hub.parse_geojson(collection, feed["provider"], feed["attribution"], now_ms)
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
