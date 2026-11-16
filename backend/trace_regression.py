"""Deterministic offline comparison of recorded navigation traces.

This is a *trace regression evaluator*, not a Valhalla route executor.
Inputs must contain independently captured reference and candidate maneuvers.
No missing measurement is treated as a passing result.
"""
from __future__ import annotations

import hashlib
import json
import math
from typing import Any

METRICS = ("maneuver_accuracy", "voice_timing_ms", "gps_error_m", "fallback_count")


class InvalidReplayEvidence(ValueError):
    pass


def _number(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise InvalidReplayEvidence("invalid numeric measurement: " + label)
    return float(value)


def evaluate_trace(reference: dict[str, Any], candidate: dict[str, Any],
                   *, max_voice_delay_ms: float = 500, max_gps_error_m: float = 25) -> dict[str, Any]:
    """Compare two traces from the same map/route/input version.

    Requires per-maneuver IDs and directions. Missing or conflicting evidence
    raises rather than silently passing. Results are advisory until an
    independent Valhalla replay runner attests the inputs and execution.
    """
    if not isinstance(reference, dict) or not isinstance(candidate, dict):
        raise InvalidReplayEvidence("reference and candidate objects required")
    for field in ("route_id", "map_version", "input_sha256"):
        value = reference.get(field)
        if not isinstance(value, str) or not value or candidate.get(field) != value:
            raise InvalidReplayEvidence("trace identity mismatch: " + field)
    ref = reference.get("maneuvers")
    cand = candidate.get("maneuvers")
    if not isinstance(ref, list) or not ref or not isinstance(cand, list) or len(ref) != len(cand):
        raise InvalidReplayEvidence("nonempty equally sized maneuver lists required")
    if len(ref) > 10000:
        raise InvalidReplayEvidence("too many maneuvers")
    directions = {"left", "right", "straight", "uturn", "arrive", "depart"}
    matches = 0
    voice_delays = []
    gps_errors = []
    fallback_count = 0
    seen = set()
    for expected, actual in zip(ref, cand):
        if not isinstance(expected, dict) or not isinstance(actual, dict):
            raise InvalidReplayEvidence("maneuver objects required")
        key = expected.get("id")
        if not isinstance(key, str) or not key or key in seen or actual.get("id") != key:
            raise InvalidReplayEvidence("missing, duplicate or mismatched maneuver ID")
        seen.add(key)
        expected_direction = expected.get("direction")
        actual_direction = actual.get("direction")
        if expected_direction not in directions or actual_direction not in directions:
            raise InvalidReplayEvidence("unknown maneuver direction")
        matches += expected_direction == actual_direction
        voice_delays.append(abs(_number(actual.get("voice_delay_ms"), "voice_delay_ms")))
        gps_errors.append(_number(actual.get("gps_error_m"), "gps_error_m"))
        if gps_errors[-1] < 0:
            raise InvalidReplayEvidence("negative GPS error")
        fallback = actual.get("fallback_used")
        if not isinstance(fallback, bool):
            raise InvalidReplayEvidence("fallback_used must be boolean")
        fallback_count += fallback
    accuracy = matches / len(ref)
    max_voice = max(voice_delays)
    max_gps = max(gps_errors)
    thresholds = {"max_voice_delay_ms": _number(max_voice_delay_ms, "max_voice_delay_ms"),
                  "max_gps_error_m": _number(max_gps_error_m, "max_gps_error_m")}
    if min(thresholds.values()) < 0:
        raise InvalidReplayEvidence("negative threshold")
    passed = accuracy == 1 and max_voice <= thresholds["max_voice_delay_ms"] and max_gps <= thresholds["max_gps_error_m"] and fallback_count == 0
    canonical = json.dumps({"reference": reference, "candidate": candidate, "thresholds": thresholds},
                           sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode()
    return {
        "schema_version": "trace-regression-v1",
        "evaluation_type": "recorded_trace_comparison",
        "verified": False,
        "passed": passed,
        "promotion_eligible": False,
        "reason": "independent_route_replay_not_connected",
        "input_sha256": hashlib.sha256(canonical).hexdigest(),
        "maneuver_accuracy": accuracy,
        "max_voice_delay_ms": max_voice,
        "max_gps_error_m": max_gps,
        "fallback_count": fallback_count,
        "maneuver_count": len(ref),
        "thresholds": thresholds,
    }
