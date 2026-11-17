"""Local Journey service gateway with explicit readiness and safe fallback.

Bind only to loopback. Place authenticated HTTPS reverse proxy in front of
this service before Android access. The health endpoint exposes no secrets.
"""
from __future__ import annotations

import argparse
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from ipaddress import ip_address


SCHEMA = "journey-backend-v1"
MAX_BODY = 64 * 1024


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
        # No client may submit self-asserted POI/route verification booleans.
        if self.path == "/api/journey/v1/recommend":
            return self._json(503, {
                "schema_version": SCHEMA, "reason": "evidence_pipeline_not_verified",
                "options": [], "route_mutated": False,
            })
        return self._json(404, {"error": "not_found"})

    def log_message(self, format, *args):
        # Avoid logging query strings, tokens, or GPS data.
        return


class JourneyGateway(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address):
        host, _ = address
        if not ip_address(host).is_loopback:
            raise ValueError("Journey gateway must bind to numeric loopback")
        super().__init__(address, JourneyHandler)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--listen", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8791)
    args = parser.parse_args()
    server = JourneyGateway((args.listen, args.port))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
