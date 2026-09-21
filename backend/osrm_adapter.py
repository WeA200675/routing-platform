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


def point(value):
    lat, lon = float(value["latitude"]), float(value["longitude"])
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise ValueError("invalid coordinate")
    return lat, lon


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
            kind = TYPE_MAP.get(m.get("type", ""), "unknown")
            if m.get("type") == "turn":
                if modifier == "left": kind = "turn_left"
                elif modifier == "right": kind = "turn_right"
                elif modifier == "uturn": kind = "u_turn"
            loc = m.get("location")
            begin = 0
            if loc:
                # Bind maneuver to an exact returned geometry vertex when present.
                target = [loc[1], loc[0]]
                try: begin = geometry.index(target)
                except ValueError: begin = 0
            maneuvers.append({
                "type": kind,
                "instruction": step.get("name") or m.get("type", "continue"),
                "streetNames": [step["name"]] if step.get("name") else [],
                "distanceM": float(step["distance"]),
                "durationS": float(step["duration"]),
                "beginShapeIndex": begin,
                "endShapeIndex": begin,
                "bearingBeforeDeg": m.get("bearing_before"),
                "bearingAfterDeg": m.get("bearing_after"),
                "engineType": None,
            })
    # Contract requires route-ordered maneuver indices. OSRM steps are ordered;
    # exact vertex misses inherit the previous verified index, never an invented coordinate.
    last = 0
    for m in maneuvers:
        if m["beginShapeIndex"] < last: m["beginShapeIndex"] = last
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
        except Exception as error:
            self.send_error(502, str(error))

    def log_message(self, fmt, *args):
        print(fmt % args)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("PORT", "8080"))), Handler).serve_forever()
