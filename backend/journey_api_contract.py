"""Journey API v1 strict JSON contract, without network or route side effects.

The endpoint may call evaluate_payload only with independently verified
candidate evidence from trusted local adapters. Do not expose this as a public
HTTP endpoint before authentication and rate limiting are wired.
"""
from __future__ import annotations

from backend.journey_break_service import (
    Context, RouteEvidence, Stop, TrafficEvidence, recommend,
)


class InvalidJourneyRequest(ValueError):
    pass


def _object(value, name):
    if not isinstance(value, dict):
        raise InvalidJourneyRequest(f"{name} must be an object")
    return value


def _required(obj, key, expected):
    value = obj.get(key)
    if type(value) not in expected:
        raise InvalidJourneyRequest(f"invalid {key}")
    return value


def evaluate_payload(payload: dict) -> dict:
    data = _object(payload, "request")
    if data.get("schema_version") != "journey-recommendation-v1":
        raise InvalidJourneyRequest("unsupported schema_version")
    ctx = _object(data.get("context"), "context")
    traffic = ctx.get("traffic")
    if traffic is not None:
        t = _object(traffic, "traffic")
        traffic = TrafficEvidence(
            _required(t, "observed_at_epoch_seconds", (int,)),
            _required(t, "starts_in_seconds", (int,)),
            _required(t, "delay_seconds", (int,)),
            _required(t, "route_matched", (bool,)),
            _required(t, "source_verified", (bool,)),
        )
    context = Context(
        _required(ctx, "driving_seconds", (int,)),
        _required(ctx, "remaining_meters", (int, float)),
        _required(ctx, "now_epoch_seconds", (int,)),
        ctx.get("seconds_since_last_offer"),
        _required(ctx, "urgent_wc", (bool,)),
        traffic,
    )
    if (context.seconds_since_last_offer is not None and
            type(context.seconds_since_last_offer) is not int):
        raise InvalidJourneyRequest("invalid seconds_since_last_offer")
    raw_stops = data.get("stops")
    if not isinstance(raw_stops, list) or len(raw_stops) > 500:
        raise InvalidJourneyRequest("invalid stops")
    stops = []
    for raw in raw_stops:
        s = _object(raw, "stop")
        r = _object(s.get("route"), "route")
        route = RouteEvidence(
            _required(r, "direct_seconds", (int, float)),
            _required(r, "via_seconds", (int, float)),
            _required(r, "map_version", (str,)),
            _required(r, "via_map_version", (str,)),
            _required(r, "profile", (str,)),
            _required(r, "via_profile", (str,)),
            _required(r, "access_verified", (bool,)),
            _required(r, "same_direction", (bool,)),
            _required(r, "exit_ahead", (bool,)),
        )
        stops.append(Stop(
            _required(s, "id", (str,)),
            _required(s, "name", (str,)),
            _required(s, "wc_evidence", (str,)),
            _required(s, "source", (str,)),
            _required(s, "source_version", (str,)),
            _required(s, "before_congestion", (bool,)),
            route,
        ))
    return {"schema_version": "journey-recommendation-v1",
            "decision": recommend(context, stops),
            "requires_user_confirmation": True,
            "route_mutated": False}
