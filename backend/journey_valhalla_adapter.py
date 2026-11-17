"""Validated adapter for comparable full-route Valhalla travel times.

The route callback is the existing NavigationRouteServer.route signature.
Access legality and carriageway direction MUST be verified by a separate
map-matching/POI adapter; this module does not infer them from coordinates.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

from backend.journey_break_service import RouteEvidence


class RouteComparisonUnavailable(ValueError):
    pass


@dataclass(frozen=True)
class Journey:
    origin: tuple[float, float]
    destination: tuple[float, float]
    via: tuple[tuple[float, float], ...] = ()
    family: str = "profile_optimal"
    map_version: str = ""


def _seconds(route: dict, expected_map_version: str) -> float:
    if not isinstance(route, dict) or route.get("engineName") != "valhalla":
        raise RouteComparisonUnavailable("unverified routing engine")
    # Route identity must originate from the actual routing response, not the
    # POI record or client request. Current exporters without this field
    # remain safely disabled until graph identity is implemented.
    if route.get("mapGraphVersion") != expected_map_version:
        raise RouteComparisonUnavailable("route graph version not attested")
    # Exporter contracts may differ. Only accept a documented top-level
    # total for the entire route; never sum unverified leg guesses.
    value = route.get("durationS")
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise RouteComparisonUnavailable("missing durationS")
    if not math.isfinite(value) or value <= 0:
        raise RouteComparisonUnavailable("invalid full-route duration")
    return float(value)


def compare_via_stop(route_provider, journey: Journey, stop: tuple[float, float],
                     *, verified_map_version: str, legal_access_and_return: bool,
                     same_direction: bool, exit_ahead: bool) -> RouteEvidence:
    if (not journey.map_version or not verified_map_version
            or journey.map_version != verified_map_version):
        raise RouteComparisonUnavailable("map version not proven identical")
    if journey.via:
        raise RouteComparisonUnavailable("candidate insertion order not yet verified for existing waypoints")
    if not (legal_access_and_return and same_direction and exit_ahead):
        raise RouteComparisonUnavailable("POI access not verified")
    def invoke(via):
        return route_provider.route(origin=journey.origin, destination=journey.destination,
                                    via=list(via), family=journey.family)
    direct = invoke(journey.via)
    direct_seconds = _seconds(direct, journey.map_version)
    # Validate the direct route before invoking the potentially expensive
    # detour. The upstream matcher must attest route order independently.
    detour = invoke(journey.via + (stop,))
    return RouteEvidence(
        direct_seconds=direct_seconds,
        via_seconds=_seconds(detour, journey.map_version),
        map_version=journey.map_version, via_map_version=verified_map_version,
        profile=journey.family, via_profile=journey.family,
        access_verified=legal_access_and_return, same_direction=same_direction,
        exit_ahead=exit_ahead,
    )
