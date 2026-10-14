#!/usr/bin/env python3
"""Minimal fail-closed OSRM adapter for the Android NavigationRouteContract.

The adapter owns no routing decisions: OSRM calculates geometry, duration,
distance and maneuvers from OpenStreetMap-derived data. No LLM is involved.
"""
from __future__ import annotations

import hashlib
import json
import os
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

OSRM_URL = os.environ.get("OSRM_URL", "http://127.0.0.1:5000").rstrip("/")
MAX_BODY = 64 * 1024

FAMILIES = {"fastest", "profile_optimal"}
TYPE_MAP = {
    "depart": "start", "arrive": "arrive", "turn": "continue",
    "continue": "continue", "merge": "merge", "fork": "continue",
    "on ramp": "merge", "off ramp": "continue", "roundabout": "roundabout",
    "rotary": "roundabout", "new name": "continue", "end of road": "continue",
}


def maneuver_side(modifier):
    normalized = str(modifier or "").lower().replace(" ", "_")
    if normalized in {"left", "slight_left", "sharp_left"}:
        return "left"
    if normalized in {"right", "slight_right", "sharp_right"}:
        return "right"
    if normalized in {"uturn", "u_turn"}:
        return "uturn"
    if normalized == "straight":
        return "straight"
    return None


def maneuver_instruction(maneuver, name):
    kind = maneuver.get("type", "")
    modifier = maneuver_side(maneuver.get("modifier"))
    if kind == "off ramp":
        side = "rechts" if modifier == "right" else "links" if modifier == "left" else None
        return f"Ausfahrt {side} nehmen. Bitte Beschilderung beachten." if side else \
            "Ausfahrt nehmen. Bitte Beschilderung beachten."
    if kind == "on ramp":
        side = "rechts" if modifier == "right" else "links" if modifier == "left" else None
        return f"{side.capitalize()} einfädeln. Bitte Beschilderung beachten." if side else \
            "Einfädeln. Bitte Beschilderung beachten."
    if kind in {"turn", "end of road", "fork"}:
        if modifier == "left": return "Links abbiegen."
        if modifier == "right": return "Rechts abbiegen."
        if modifier == "uturn": return "Wenden. Bitte Beschilderung beachten."
    return name or kind or "Weiterfahren"


def point(value):
    lat, lon = float(value["latitude"]), float(value["longitude"])
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise ValueError("invalid coordinate")
    return lat, lon


LANE_ARROW = {
    "sharp left": "⇐", "left": "←", "slight left": "↖",
    "straight": "↑", "slight right": "↗", "right": "→",
    "sharp right": "⇒", "uturn": "↶",
}


def maneuver_lane_indications(step):
    """Return lane glyphs only when OSRM identifies one unambiguous junction."""
    intersections = [
        intersection for intersection in step.get("intersections", [])
        if intersection.get("lanes")
    ]
    if len(intersections) != 1:
        return []
    lanes = intersections[0]["lanes"]
    if len(lanes) > 12:
        return []
    result = []
    for lane in lanes:
        indications = lane.get("indications") or []
        if not indications:
            return []
        glyphs = [LANE_ARROW.get(str(value).lower()) for value in indications]
        if any(glyph is None for glyph in glyphs):
            return []
        result.append("".join(glyphs) + (" ✓" if lane.get("valid") is True else ""))
    return result


def route(request):
    family = request.get("family")
    if family not in FAMILIES:
        raise ValueError("routing family is not exactly supported by OSRM adapter")
    points = [point(request["origin"])]
    points += [point(v) for v in request.get("viaPoints", [])]
    points += [point(request["destination"])]
    coords = ";".join(f"{lon},{lat}" for lat, lon in points)
    query = urllib.parse.urlencode({
        "overview": "full", "geometries": "geojson", "steps": "true",
        "annotations": "false",
    })
    url = f"{OSRM_URL}/route/v1/driving/{coords}?{query}"
    with urllib.request.urlopen(url, timeout=30) as response:
        payload = json.load(response)
    if payload.get("code") != "Ok" or not payload.get("routes"):
        raise RuntimeError("OSRM did not return a route")
    r = payload["routes"][0]
    geometry = [[lat, lon] for lon, lat in r["geometry"]["coordinates"]]
    maneuvers = []
    for leg in r["legs"]:
        for step in leg.get("steps", []):
            m = step.get("maneuver", {})
            modifier = m.get("modifier", "")
            source_type = m.get("type", "")
            kind = TYPE_MAP.get(source_type, "unknown")
            if source_type == "off ramp": kind = "exit"
            elif source_type == "on ramp": kind = "merge"
            if source_type in {"turn", "end of road", "fork"}:
                side = maneuver_side(modifier)
                if side == "left": kind = "turn_left"
                elif side == "right": kind = "turn_right"
                elif side == "uturn": kind = "u_turn"
            loc = m.get("location")
            if not loc:
                raise RuntimeError("OSRM maneuver is missing its engine location")
            # Never invent a shape index. A maneuver is admitted only when the
            # engine location is an exact vertex of the returned route geometry.
            target = [loc[1], loc[0]]
            try:
                begin = geometry.index(target)
            except ValueError as exc:
                raise RuntimeError(
                    "OSRM maneuver location is not present in route geometry"
                ) from exc
            destinations = step.get("destinations") or step.get("ref")
            # OSRM does not supply a trustworthy lane-to-route assignment.
            # Preserve only the provider's actual lane indications; consumers
            # must not infer a recommended lane from them.
            lane_indications = maneuver_lane_indications(step)
            maneuvers.append({
                "type": kind,
                "instruction": maneuver_instruction(m, step.get("name")),
                "streetNames": [step["name"]] if step.get("name") else [],
                "distanceM": float(step["distance"]),
                "durationS": float(step["duration"]),
                "beginShapeIndex": begin,
                "endShapeIndex": begin,
                "bearingBeforeDeg": m.get("bearing_before"),
                "bearingAfterDeg": m.get("bearing_after"),
                "engineType": None,
                "maneuverSide": maneuver_side(modifier),
                "destinationSign": str(destinations)[:160] if destinations else None,
                "laneIndications": lane_indications,
            })
    # Contract requires route-ordered maneuver indices. Fail closed if
    # upstream steps contradict the geometry ordering instead of clamping them.
    last = -1
    for m in maneuvers:
        if m["beginShapeIndex"] < last:
            raise RuntimeError("OSRM maneuver order contradicts route geometry")
        last = m["beginShapeIndex"]
        m["endShapeIndex"] = last
    digest = hashlib.sha256(json.dumps(r, sort_keys=True).encode()).hexdigest()[:24]
    return {
        "schemaVersion": 1, "routeId": f"osrm-{digest}", "family": family,
        "distanceM": float(r["distance"]), "durationS": float(r["duration"]),
        "geometry": geometry, "maneuvers": maneuvers,
        "engineName": "OSRM", "engineVersion": payload.get("version", "api-v1"),
        "segmentDataStatus": "unavailable",
        "diagnostics": [{"code": "traffic_unavailable",
                         "message": "OSRM adapter has no live traffic feed."}],
    }


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/v1/navigation/route":
            self.send_error(404); return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_BODY: raise ValueError("invalid request size")
            request = json.loads(self.rfile.read(length))
            result = route(request)
            body = json.dumps(result, separators=(",", ":")).encode()
            self.send_response(200); self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body))); self.end_headers()
            self.wfile.write(body)
        except ValueError as error:
            self.send_error(422, str(error))
        except Exception:
            # Do not expose upstream URLs, payload details or infrastructure
            # exceptions to clients.
            self.send_error(502, "routing provider failure")

    def log_message(self, fmt, *args):
        print(fmt % args)


if __name__ == "__main__":
    bind_host = os.environ.get("BIND_HOST", "127.0.0.1")
    if bind_host not in {"127.0.0.1", "0.0.0.0"}:
        raise SystemExit("BIND_HOST must be 127.0.0.1 or 0.0.0.0")
    ThreadingHTTPServer((bind_host, int(os.environ.get("PORT", "8080"))), Handler).serve_forever()
