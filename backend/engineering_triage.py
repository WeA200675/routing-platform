"""Deterministic, privacy-conscious incident triage for R2 engineering backlog.

Pure standard-library module. No GitHub write access, cloud calls or automatic
production changes. Consumers must redact sensitive payloads before submission.
"""
from __future__ import annotations

import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timezone
from typing import Any

SEVERITY = ("critical", "high", "medium", "low")
CATEGORIES = ("crash", "maneuver", "voice", "gps", "offline", "sync", "performance", "other")
MAX_INCIDENTS = 10000
MAX_TEXT = 512
SENSITIVE = re.compile(r"(?i)(bearer\s+\S+|(?:token|password|secret|api[_-]?key)\s*[:=]\s*\S+)")
COORDINATES = re.compile(r"(?<!\d)[+-]?\d{1,2}\.\d{4,}\s*[,;]\s*[+-]?\d{1,3}\.\d{4,}(?!\d)")


class InvalidIncident(ValueError):
    """Invalid or unsafe incident input."""


def _clean(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip() or len(value) > MAX_TEXT:
        raise InvalidIncident("invalid " + field)
    if SENSITIVE.search(value) or COORDINATES.search(value):
        raise InvalidIncident("sensitive data in " + field)
    return value.strip()


def classify(incident: dict[str, Any]) -> dict[str, Any]:
    """Return stable fingerprint and priority without copying raw logs or GPS."""
    if not isinstance(incident, dict):
        raise InvalidIncident("incident must be an object")
    category = _clean(incident.get("category"), "category").lower()
    if category not in CATEGORIES:
        raise InvalidIncident("unknown category")
    component = _clean(incident.get("component"), "component").lower()
    code = _clean(incident.get("error_code"), "error_code").upper()
    # Explicitly reject raw payloads: callers must pre-extract safe fields.
    if set(incident) - {"category", "component", "error_code", "repeat_count", "navigation_blocked"}:
        raise InvalidIncident("unexpected fields; raw logs are not accepted")
    repeats = incident.get("repeat_count", 1)
    blocked = incident.get("navigation_blocked", False)
    if isinstance(repeats, bool) or not isinstance(repeats, int) or not 1 <= repeats <= 100000:
        raise InvalidIncident("invalid repeat_count")
    if not isinstance(blocked, bool):
        raise InvalidIncident("invalid navigation_blocked")
    critical = blocked or category == "crash"
    severity = "critical" if critical else "high" if category in {"maneuver", "voice", "offline"} or repeats >= 10 else "medium" if category in {"gps", "sync"} or repeats >= 3 else "low"
    identity = json.dumps([category, component, code], separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    return {"schema_version": "engineering-triage-v1",
            "fingerprint": hashlib.sha256(identity).hexdigest(),
            "category": category, "component": component, "error_code": code,
            "severity": severity, "repeat_count": repeats,
            "navigation_blocked": blocked, "requires_replay": category in {"maneuver", "voice", "gps", "offline"},
            "auto_merge_eligible": False, "production_deploy_eligible": False}


def backlog(incidents: list[dict[str, Any]]) -> dict[str, Any]:
    if not isinstance(incidents, list) or len(incidents) > MAX_INCIDENTS:
        raise InvalidIncident("invalid incident list")
    grouped: dict[str, dict[str, Any]] = {}
    for incident in incidents:
        item = classify(incident)
        key = item["fingerprint"]
        if key not in grouped:
            grouped[key] = item
        else:
            old = grouped[key]
            old["repeat_count"] = min(100000, old["repeat_count"] + item["repeat_count"])
            old["navigation_blocked"] = old["navigation_blocked"] or item["navigation_blocked"]
            old["severity"] = min((old["severity"], item["severity"]), key=SEVERITY.index)
            if old["repeat_count"] >= 10 and SEVERITY.index(old["severity"]) > SEVERITY.index("high"):
                old["severity"] = "high"
    items = sorted(grouped.values(), key=lambda x: (SEVERITY.index(x["severity"]), -x["repeat_count"], x["fingerprint"]))
    return {"schema_version": "engineering-backlog-v1", "incident_count": len(incidents),
            "unique_count": len(items), "by_severity": dict(Counter(x["severity"] for x in items)),
            "items": items}
