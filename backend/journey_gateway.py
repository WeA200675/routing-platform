"""Local Journey service gateway with explicit readiness and safe fallback.

Bind only to loopback. Place authenticated HTTPS reverse proxy in front of
this service before Android access. The health endpoint exposes no secrets.
"""
from __future__ import annotations

import argparse
import json
import hmac
import os
import math
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from ipaddress import ip_address


SCHEMA = "journey-backend-v1"
MAX_BODY = 64 * 1024
MAX_ACTIVE_ROUTES = 1


def status_payload():
    return {
        "schema_version": SCHEMA,
        "service": "journey",
        "status": "degraded",
        "recommendations_enabled": False,
        "routing_graph_attested": False,
        "poi_audit_ready": False,
        "offline_fallback": "continue_navigation",
    }


class JourneyHandler(BaseHTTPRequestHandler):
    server_version = "JourneyGateway/1"

    def _json(self, status, payload):
        raw = json.dumps(payload, separators=(",", ":"), allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path in ("/health", "/api/journey/v1/status"):
            return self._json(200, status_payload())
        if self.path == "/ready":
            return self._json(503, {"schema_version": SCHEMA, "ready": False,
                                    "reason": "evidence_pipeline_not_verified"})
        return self._json(404, {"error": "not_found"})

    def do_POST(self):
        if self.path == "/api/navigation/v1/route":
            return self._route_request()
        # No client may submit self-asserted POI/route verification booleans.
        if self.path == "/api/journey/v1/recommend":
            return self._json(503, {
                "schema_version": SCHEMA, "reason": "evidence_pipeline_not_verified",
                "options": [], "route_mutated": False,
            })
        return self._json(404, {"error": "not_found"})

    def _route_request(self):
        server = self.server
        if not server.route_enabled:
            return self._json(503, {"error": "routing_disabled", "retryable": False})
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer ") or not hmac.compare_digest(
                auth[7:], server.route_token):
            return self._json(401, {"error": "unauthorized"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= MAX_BODY:
                return self._json(413, {"error": "invalid_request_size"})
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            from tools.navigation_route_service import validate_request
            origin, destination, via, family = validate_request(payload)
            for point in (origin, destination, *via):
                if any(not math.isfinite(coordinate) for coordinate in point):
                    return self._json(400, {"error": "nonfinite_coordinate"})
        except (ValueError, TypeError, KeyError, UnicodeError, OverflowError):
            return self._json(400, {"error": "invalid_route_request"})
        if not server.route_slots.acquire(blocking=False):
            return self._json(503, {"error": "route_busy", "retryable": True})
        try:
            route = server.route_provider.route(
                origin=origin, destination=destination, via=via, family=family)
            if not isinstance(route, dict) or route.get("engineName") != "valhalla":
                return self._json(502, {"error": "invalid_route_identity"})
            raw = json.dumps(route, allow_nan=False).encode("utf-8")
            if len(raw) > 1024 * 1024:
                return self._json(502, {"error": "route_too_large"})
            return self._json(200, route)
        except Exception:
            return self._json(503, {"error": "route_backend_unavailable",
                                    "retryable": True})
        finally:
            server.route_slots.release()

    def log_message(self, format, *args):
        # Avoid logging query strings, tokens, or GPS data.
        return


class JourneyGateway(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, *, route_provider=None, route_token=""):
        host, _ = address
        if not ip_address(host).is_loopback:
            raise ValueError("Journey gateway must bind to numeric loopback")
        self.route_provider = route_provider
        self.route_token = route_token
        self.route_enabled = route_provider is not None and len(route_token) >= 32
        self.route_slots = threading.BoundedSemaphore(MAX_ACTIVE_ROUTES)
        super().__init__(address, JourneyHandler)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--listen", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8791)
    args = parser.parse_args()
    route_token = os.environ.get("JOURNEY_ROUTE_BEARER_TOKEN", "")
    if route_token and len(route_token) < 32:
        raise SystemExit("JOURNEY_ROUTE_BEARER_TOKEN must contain at least 32 characters")
    route_provider = None
    if len(route_token) >= 32:
        from backend.journey_route_client import LoopbackRouteProvider
        route_provider = LoopbackRouteProvider(
            url=os.environ.get("JOURNEY_ROUTE_SERVICE_URL",
                               "http://127.0.0.1:8787/v1/navigation/route"))
    server = JourneyGateway((args.listen, args.port),
                            route_provider=route_provider, route_token=route_token)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
