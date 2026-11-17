"""Journey Intelligence backend: deterministic, fail-closed decision boundary.

Only trusted adapters may construct candidate evidence. This module does not
fetch POIs, call Valhalla, mutate routes, or claim live WC availability.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

MAX_DETOUR_SECONDS = 600
MAX_TRAFFIC_AGE_SECONDS = 300
COOLDOWN_SECONDS = 1800


@dataclass(frozen=True)
class RouteEvidence:
    direct_seconds: float
    via_seconds: float
    map_version: str
    via_map_version: str
    profile: str
    via_profile: str
    access_verified: bool
    same_direction: bool
    exit_ahead: bool

    def detour_seconds(self) -> float | None:
        if not (self.access_verified and self.same_direction and self.exit_ahead):
            return None
        if not (self.map_version and self.profile
                and self.map_version == self.via_map_version
                and self.profile == self.via_profile):
            return None
        values = (self.direct_seconds, self.via_seconds)
        if any(isinstance(v, bool) or not isinstance(v, (int, float))
               or not math.isfinite(v) for v in values):
            return None
        if self.direct_seconds <= 0 or self.via_seconds < self.direct_seconds:
            return None
        return self.via_seconds - self.direct_seconds


@dataclass(frozen=True)
class Stop:
    id: str
    name: str
    wc_evidence: str
    source: str
    source_version: str
    before_congestion: bool
    route: RouteEvidence


@dataclass(frozen=True)
class TrafficEvidence:
    observed_at_epoch_seconds: int
    starts_in_seconds: int
    delay_seconds: int
    route_matched: bool
    source_verified: bool

    def actionable(self, now: int) -> bool:
        return (self.route_matched and self.source_verified
                and isinstance(now, int) and not isinstance(now, bool)
                and type(self.observed_at_epoch_seconds) is int
                and 0 <= self.observed_at_epoch_seconds <= now
                and now - self.observed_at_epoch_seconds <= MAX_TRAFFIC_AGE_SECONDS
                and type(self.starts_in_seconds) is int
                and 0 <= self.starts_in_seconds <= 1800
                and type(self.delay_seconds) is int and self.delay_seconds > 0)


@dataclass(frozen=True)
class Context:
    driving_seconds: int
    remaining_meters: float
    now_epoch_seconds: int
    seconds_since_last_offer: int | None = None
    urgent_wc: bool = False
    traffic: TrafficEvidence | None = None


def recommend(context: Context, stops: list[Stop]) -> dict:
    """Return at most three recommendations, never an automatic route change."""
    if (isinstance(context.driving_seconds, bool)
            or not isinstance(context.driving_seconds, int)
            or context.driving_seconds < 0
            or isinstance(context.remaining_meters, bool)
            or not isinstance(context.remaining_meters, (int, float))
            or not math.isfinite(context.remaining_meters)
            or context.remaining_meters < 0
            or not isinstance(context.now_epoch_seconds, int)
            or isinstance(context.now_epoch_seconds, bool)
            or context.now_epoch_seconds < 0
            or (context.seconds_since_last_offer is not None and
                (type(context.seconds_since_last_offer) is not int
                 or context.seconds_since_last_offer < 0))
            or not isinstance(context.urgent_wc, bool)
            or not isinstance(stops, (list, tuple))
            or len(stops) > 500):
        return {"reason": "invalid_context", "options": []}
    if (not context.urgent_wc and context.seconds_since_last_offer is not None
            and context.seconds_since_last_offer < COOLDOWN_SECONDS):
        return {"reason": "cooldown", "options": []}

    regular = context.driving_seconds > 5400 and context.remaining_meters > 60000
    traffic = (context.remaining_meters > 60000 and context.traffic is not None
               and context.traffic.actionable(context.now_epoch_seconds))
    if not (regular or traffic or context.urgent_wc):
        return {"reason": "not_due", "options": []}

    def candidates(before_only: bool) -> list[dict]:
        options = []
        seen = set()
        for stop in stops:
            if not (stop.id and stop.id.strip() and stop.name and stop.name.strip()
                    and stop.id not in seen and stop.source and stop.source_version
                    and stop.wc_evidence == "verified"):
                continue
            if before_only and not stop.before_congestion:
                continue
            detour = stop.route.detour_seconds()
            if detour is None or detour > MAX_DETOUR_SECONDS:
                continue
            seen.add(stop.id)
            options.append({"id": stop.id, "name": stop.name,
                            "detour_seconds": detour, "wc_evidence": stop.wc_evidence})
        return sorted(options, key=lambda x: (x["detour_seconds"], x["id"]))[:3]

    if traffic and not context.urgent_wc:
        before = candidates(True)
        if before:
            return {"reason": "before_congestion", "options": before}
    if regular or context.urgent_wc:
        options = candidates(False)
        return {"reason": "urgent_wc" if context.urgent_wc else "regular",
                "options": options}
    return {"reason": "no_verified_stop", "options": []}
