"""Read-only Valhalla route replay with independently checkable provenance.

The runner never changes live navigation. It accepts a recorded route fixture
with expected coordinates and a known reference maneuver sequence, invokes
Valhalla /route, and emits an evidence artifact. Network/service failures fail
closed; an HTTP 200 alone is not a successful regression.
"""
from __future__ import annotations

import hashlib
import json
import math
import urllib.error
import urllib.request
from typing import Any, Callable

from backend.trace_regression import InvalidReplayEvidence


class ReplayUnavailable(RuntimeError):
    pass


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, ensure_ascii=False,
                      separators=(",", ":"), allow_nan=False).encode("utf-8")


def _coordinates(fixture: dict[str, Any]) -> list[dict[str, float]]:
    locations = fixture.get("locations")
    if not isinstance(locations, list) or not 2 <= len(locations) <= 100:
        raise InvalidReplayEvidence("2..100 route locations required")
    result = []
    for point in locations:
        if not isinstance(point, dict):
            raise InvalidReplayEvidence("invalid location")
        lat, lon = point.get("lat"), point.get("lon")
        if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v)
               for v in (lat, lon)) or not (-90 <= lat <= 90 and -180 <= lon <= 180):
            raise InvalidReplayEvidence("invalid latitude/longitude")
        result.append({"lat": float(lat), "lon": float(lon)})
    return result


def _directions(response: dict[str, Any]) -> list[str]:
    trips = response.get("trip") if isinstance(response, dict) else None
    legs = trips.get("legs") if isinstance(trips, dict) else None
    if not isinstance(legs, list) or not legs:
        raise InvalidReplayEvidence("Valhalla response missing legs")
    # Valhalla maneuver type numbers are API-defined. Reject unknown codes
    # instead of guessing the turn direction.
    # Valhalla maneuver enum: START=1, DESTINATION=4, RIGHT=10,
    # LEFT=15, RAMP_RIGHT=18, RAMP_LEFT=19, EXIT_RIGHT=20,
    # EXIT_LEFT=21, STAY_RIGHT=23, STAY_LEFT=24.
    left = {3, 6, 13, 14, 15, 16, 19, 21, 24}
    right = {2, 5, 9, 10, 11, 12, 18, 20, 23}
    straight = {1, 4, 7, 8, 17, 22, 25, 26, 27, 28, 29, 30}
    directions = []
    for leg in legs:
        maneuvers = leg.get("maneuvers") if isinstance(leg, dict) else None
        if not isinstance(maneuvers, list) or not maneuvers:
            raise InvalidReplayEvidence("Valhalla response missing maneuvers")
        for maneuver in maneuvers:
            kind = maneuver.get("type") if isinstance(maneuver, dict) else None
            if isinstance(kind, bool) or not isinstance(kind, int):
                raise InvalidReplayEvidence("invalid maneuver type")
            if kind in left:
                directions.append("left")
            elif kind in right:
                directions.append("right")
            elif kind in straight:
                directions.append("other")
            else:
                raise InvalidReplayEvidence("unknown Valhalla maneuver type")
    return directions


def replay(fixture: dict[str, Any], *, endpoint: str = "http://127.0.0.1:8002",
           transport: Callable[[str, bytes, float], bytes] | None = None,
           timeout: float = 10.0) -> dict[str, Any]:
    if not isinstance(fixture, dict):
        raise InvalidReplayEvidence("fixture object required")
    route_id, map_version = fixture.get("route_id"), fixture.get("map_version")
    if not isinstance(route_id, str) or not route_id or not isinstance(map_version, str) or not map_version:
        raise InvalidReplayEvidence("route_id and map_version required")
    expected = fixture.get("reference_directions")
    if not isinstance(expected, list) or not expected or len(expected) > 10000 or any(
        item not in ("left", "right", "other") for item in expected
    ):
        raise InvalidReplayEvidence("reference directions required")
    locations = _coordinates(fixture)
    if endpoint.rstrip("/") != "http://127.0.0.1:8002" and not endpoint.startswith("http://localhost:"):
        raise ReplayUnavailable("only explicitly local Valhalla endpoints permitted")
    if timeout <= 0 or timeout > 60:
        raise ValueError("timeout outside safe bounds")
    request = {"locations": locations, "costing": "auto", "directions_options": {"units": "kilometers"}}
    body = canonical(request)

    def http_transport(url: str, data: bytes, deadline: float) -> bytes:
        req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
        with urllib.request.urlopen(req, timeout=deadline) as response:
            if response.status != 200:
                raise ReplayUnavailable("Valhalla returned non-200")
            return response.read(4 * 1024 * 1024 + 1)

    try:
        raw = (transport or http_transport)(endpoint.rstrip("/") + "/route", body, timeout)
        if len(raw) > 4 * 1024 * 1024:
            raise ReplayUnavailable("Valhalla response exceeds size limit")
        response = json.loads(raw)
        actual = _directions(response)
    except (OSError, urllib.error.URLError, json.JSONDecodeError, KeyError, TypeError) as exc:
        raise ReplayUnavailable("Valhalla execution or response unavailable") from exc
    matches = len(actual) == len(expected) and all(a == b for a, b in zip(actual, expected))
    return {
        "schema_version": "valhalla-replay-v1",
        "route_id": route_id,
        "map_version": map_version,
        "fixture_sha256": hashlib.sha256(canonical(fixture)).hexdigest(),
        "request_sha256": hashlib.sha256(body).hexdigest(),
        "response_sha256": hashlib.sha256(raw).hexdigest(),
        "reference_directions": expected,
        "actual_directions": actual,
        "passed": matches,
        "verified": False,
        "promotion_eligible": False,
        "reason": "map_binary_and_fixture_provenance_not_independently_attested",
    }
