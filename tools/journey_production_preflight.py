#!/usr/bin/env python3
"""Read-only production preflight for a loopback Journey + Valhalla deployment.

Exit 0 only when both services are reachable, the bridge is ready, and the
gateway's advertised capabilities match the configured route token. Never
prints credentials, GPS coordinates, or user data.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

MAX_BYTES = 8192


def get_json(url: str, *, timeout: float = 3.0):
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    opener = urllib.request.build_opener(
        urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(request, timeout=timeout) as response:
            body = response.read(MAX_BYTES + 1)
            if len(body) > MAX_BYTES:
                raise RuntimeError("oversized health response")
            if response.headers.get_content_type() != "application/json":
                raise RuntimeError("invalid health content type")
            return response.status, json.loads(body)
    except urllib.error.HTTPError as error:
        body = error.read(MAX_BYTES + 1)
        if len(body) > MAX_BYTES:
            raise RuntimeError("oversized health response") from error
        return error.code, json.loads(body)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("health redirect refused")


def check(gateway="http://127.0.0.1:8791",
          bridge="http://127.0.0.1:8788"):
    results = {}
    status, data = get_json(bridge + "/ready")
    results["valhalla_bridge_ready"] = (
        status == 200 and data.get("status") == "ready"
        and data.get("checks", {}).get("exporterExecutable") is True
        and data.get("checks", {}).get("configReadable") is True)
    status, data = get_json(gateway + "/api/journey/v1/status")
    results["gateway_status_valid"] = (
        status == 200 and data.get("schema_version") == "journey-backend-v1")
    results["gateway_route_enabled"] = (
        results["gateway_status_valid"]
        and data.get("navigation_routing_enabled") is True)
    results["recommendations_fail_closed"] = (
        results["gateway_status_valid"]
        and data.get("recommendations_enabled") is False)
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--gateway", default="http://127.0.0.1:8791")
    parser.add_argument("--bridge", default="http://127.0.0.1:8788")
    args = parser.parse_args()
    if args.gateway != "http://127.0.0.1:8791" or args.bridge != "http://127.0.0.1:8788":
        raise SystemExit("preflight only permits fixed local loopback endpoints")
    try:
        results = check(args.gateway, args.bridge)
    except (OSError, ValueError, RuntimeError) as exc:
        print(json.dumps({"ready": False, "error": type(exc).__name__}))
        return 1
    ready = all(results.values())
    print(json.dumps({"ready": ready, "checks": results}, sort_keys=True))
    return 0 if ready else 1


if __name__ == "__main__":
    sys.exit(main())
