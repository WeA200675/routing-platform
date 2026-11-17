"""Loopback-only client for the existing development Valhalla route service.

No public URL, no redirects, no arbitrary outbound requests. The local
routing service is a development bridge, not an authenticated public API.
"""
from __future__ import annotations

import ipaddress
import json
import urllib.error
import urllib.request
from urllib.parse import urlsplit


MAX_ROUTE_RESPONSE_BYTES = 16 * 1024 * 1024


class LocalRouteUnavailable(RuntimeError):
    pass


class LoopbackRouteProvider:
    def __init__(self, url="http://127.0.0.1:8787/v1/navigation/route", *,
                 timeout_seconds=15, opener=None):
        parts = urlsplit(url)
        if (parts.scheme != "http" or parts.username or parts.password
                or parts.query or parts.fragment
                or parts.path != "/v1/navigation/route"):
            raise ValueError("only the loopback route endpoint is allowed")
        try:
            if not ipaddress.ip_address(parts.hostname).is_loopback:
                raise ValueError("route service must use loopback")
        except (ValueError, TypeError) as exc:
            raise ValueError("route service must use numeric loopback") from exc
        if not isinstance(timeout_seconds, (int, float)) or isinstance(timeout_seconds, bool) or not 0 < timeout_seconds <= 30:
            raise ValueError("invalid routing timeout")
        self.url = url
        self.timeout_seconds = timeout_seconds
        self.opener = opener or urllib.request.build_opener(_NoRedirect())

    def route(self, *, origin, destination, via, family):
        request_data = {
            "origin": {"latitude": origin[0], "longitude": origin[1]},
            "destination": {"latitude": destination[0], "longitude": destination[1]},
            "viaPoints": [{"latitude": p[0], "longitude": p[1]} for p in via],
            "family": family,
        }
        raw = json.dumps(request_data, allow_nan=False).encode("utf-8")
        if len(raw) > 65536:
            raise LocalRouteUnavailable("request exceeds size limit")
        request = urllib.request.Request(
            self.url, data=raw, method="POST",
            headers={"Content-Type": "application/json", "X-Routing-Platform-Dev": "1"},
        )
        try:
            with self.opener.open(request, timeout=self.timeout_seconds) as response:
                if response.status != 200:
                    raise LocalRouteUnavailable("route service returned an error")
                if response.headers.get_content_type() != "application/json":
                    raise LocalRouteUnavailable("unexpected response type")
                body = response.read(MAX_ROUTE_RESPONSE_BYTES + 1)
                if len(body) > MAX_ROUTE_RESPONSE_BYTES:
                    raise LocalRouteUnavailable("route response exceeds limit")
                data = json.loads(body.decode("utf-8"))
                if not isinstance(data, dict):
                    raise LocalRouteUnavailable("invalid route response")
                return data
        except (urllib.error.URLError, OSError, ValueError, UnicodeError) as exc:
            raise LocalRouteUnavailable("local route service unavailable") from exc


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise LocalRouteUnavailable("redirect refused")
