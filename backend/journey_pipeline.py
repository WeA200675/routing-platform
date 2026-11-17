"""Orchestrate verified stop candidates and Valhalla comparisons.

The POI source must return independently audited evidence; this module does
not infer motorway access, direction, WC existence, or map identity from GPS.
No route is changed and no candidate is presented without verified evidence.
"""
from __future__ import annotations

from dataclasses import dataclass

from backend.journey_break_service import Context, Stop, recommend
from backend.journey_valhalla_adapter import (
    Journey, RouteComparisonUnavailable, compare_via_stop,
)


@dataclass(frozen=True)
class VerifiedPoi:
    id: str
    name: str
    latitude: float
    longitude: float
    wc_evidence: str
    source: str
    source_version: str
    verified_map_version: str
    legal_access_and_return: bool
    same_direction: bool
    exit_ahead: bool
    before_congestion: bool
    # The map matcher must explicitly attest that this stop is on the
    # direct origin-to-destination route, not merely near its geometry.
    route_order_verified: bool


def evaluate_journey(context: Context, journey: Journey, poi_source,
                     route_provider, *, candidate_limit: int = 16) -> dict:
    """Produce advisory-only recommendations; fail closed per candidate.

    POI source contract: find_candidates(journey, limit=integer) -> iterable
    of VerifiedPoi. It must never return speculative access evidence.
    """
    if not isinstance(candidate_limit, int) or isinstance(candidate_limit, bool) or not 1 <= candidate_limit <= 16:
        return {"reason": "invalid_candidate_limit", "options": []}
    try:
        candidates = list(poi_source.find_candidates(journey, limit=candidate_limit))
    except Exception:
        return {"reason": "poi_source_unavailable", "options": []}
    if len(candidates) > candidate_limit:
        return {"reason": "poi_source_limit_exceeded", "options": []}

    verified = []
    for candidate in candidates:
        if not isinstance(candidate, VerifiedPoi):
            continue
        if not (candidate.route_order_verified and candidate.wc_evidence == "verified"
                and candidate.id and candidate.name and candidate.source
                and candidate.source_version and candidate.verified_map_version):
            continue
        try:
            evidence = compare_via_stop(
                route_provider, journey, (candidate.latitude, candidate.longitude),
                verified_map_version=candidate.verified_map_version,
                legal_access_and_return=candidate.legal_access_and_return,
                same_direction=candidate.same_direction,
                exit_ahead=candidate.exit_ahead,
            )
        except (RouteComparisonUnavailable, ValueError, TypeError, TimeoutError, OSError):
            continue
        verified.append(Stop(
            id=candidate.id, name=candidate.name,
            wc_evidence=candidate.wc_evidence,
            source=candidate.source, source_version=candidate.source_version,
            before_congestion=candidate.before_congestion, route=evidence,
        ))
    return recommend(context, verified)
