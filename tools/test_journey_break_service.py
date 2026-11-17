"""Regression tests for evidence-gated Journey Intelligence backend."""
import unittest

from backend.journey_break_service import (
    Context, RouteEvidence, Stop, TrafficEvidence, recommend,
)


def route(**changes):
    data = dict(direct_seconds=1000, via_seconds=1600, map_version="dach-1",
                via_map_version="dach-1", profile="auto", via_profile="auto",
                access_verified=True, same_direction=True, exit_ahead=True)
    data.update(changes)
    return RouteEvidence(**data)


def stop(**changes):
    data = dict(id="wc1", name="Rastplatz", wc_evidence="verified",
                source="offline-osm", source_version="2026-11",
                before_congestion=True, route=route())
    data.update(changes)
    return Stop(**data)


def context(**changes):
    data = dict(driving_seconds=5401, remaining_meters=60001,
                now_epoch_seconds=10000)
    data.update(changes)
    return Context(**data)


class JourneyBreakServiceTest(unittest.TestCase):
    def test_regular_thresholds_strict(self):
        self.assertEqual(recommend(context(driving_seconds=5400), [stop()])["reason"], "not_due")
        self.assertEqual(recommend(context(remaining_meters=60000), [stop()])["reason"], "not_due")

    def test_detour_boundary(self):
        self.assertEqual(len(recommend(context(), [stop()])["options"]), 1)
        self.assertEqual(recommend(context(), [stop(route=route(via_seconds=1601))])["options"], [])

    def test_shorter_via_route_is_not_comparable(self):
        self.assertEqual(recommend(context(), [stop(route=route(via_seconds=900))])["options"], [])

    def test_nonfinite_route_time(self):
        self.assertEqual(recommend(context(), [stop(route=route(via_seconds=float("nan")))])["options"], [])

    def test_map_version_must_match(self):
        self.assertEqual(recommend(context(), [stop(route=route(via_map_version="other"))])["options"], [])

    def test_profile_must_match(self):
        self.assertEqual(recommend(context(), [stop(route=route(via_profile="bike"))])["options"], [])

    def test_access_must_be_verified(self):
        self.assertEqual(recommend(context(), [stop(route=route(access_verified=False))])["options"], [])

    def test_correct_direction_required(self):
        self.assertEqual(recommend(context(), [stop(route=route(same_direction=False))])["options"], [])

    def test_exit_must_be_ahead(self):
        self.assertEqual(recommend(context(), [stop(route=route(exit_ahead=False))])["options"], [])

    def test_route_legality_attestation_requires_literal_true(self):
        for field in ("access_verified", "same_direction", "exit_ahead"):
            for value in (1, "true", None):
                self.assertEqual(
                    recommend(context(), [stop(route=route(**{field: value}))])["options"],
                    [], (field, value))

    def test_wc_must_be_verified(self):
        self.assertEqual(recommend(context(), [stop(wc_evidence="unknown")])["options"], [])

    def test_source_version_required(self):
        self.assertEqual(recommend(context(), [stop(source_version="")])["options"], [])

    def test_cooldown(self):
        self.assertEqual(recommend(context(seconds_since_last_offer=5), [stop()])["reason"], "cooldown")

    def test_urgent_overrides_thresholds_and_cooldown(self):
        result = recommend(context(driving_seconds=5, remaining_meters=500,
                                   seconds_since_last_offer=5, urgent_wc=True), [stop()])
        self.assertEqual(result["reason"], "urgent_wc")
        self.assertEqual(len(result["options"]), 1)

    def test_traffic_preempts_drive_time(self):
        traffic = TrafficEvidence(9999, 120, 300, True, True)
        result = recommend(context(driving_seconds=10, traffic=traffic), [stop()])
        self.assertEqual(result["reason"], "before_congestion")

    def test_stale_traffic_cannot_preempt(self):
        traffic = TrafficEvidence(9000, 120, 300, True, True)
        self.assertEqual(recommend(context(driving_seconds=10, traffic=traffic), [stop()])["reason"], "not_due")

    def test_traffic_after_congestion_not_suggested(self):
        traffic = TrafficEvidence(9999, 120, 300, True, True)
        result = recommend(context(driving_seconds=10, traffic=traffic),
                           [stop(before_congestion=False)])
        self.assertEqual(result["options"], [])

    def test_invalid_remaining_distance(self):
        self.assertEqual(recommend(context(remaining_meters=float("nan")), [stop()])["reason"], "invalid_context")

    def test_boolean_cooldown_rejected(self):
        self.assertEqual(
            recommend(context(seconds_since_last_offer=True), [stop()])["reason"],
            "invalid_context")

    def test_boolean_traffic_times_never_trigger(self):
        for field in ("observed_at_epoch_seconds", "starts_in_seconds", "delay_seconds"):
            data = dict(observed_at_epoch_seconds=9999, starts_in_seconds=120,
                        delay_seconds=300, route_matched=True, source_verified=True)
            data[field] = True
            self.assertEqual(
                recommend(context(driving_seconds=10, traffic=TrafficEvidence(**data)),
                          [stop()])["reason"], "not_due", field)

    def test_traffic_attestation_requires_literal_true(self):
        for field in ("route_matched", "source_verified"):
            data = dict(observed_at_epoch_seconds=9999, starts_in_seconds=120,
                        delay_seconds=300, route_matched=True, source_verified=True)
            for untrusted in (1, "verified", None):
                data[field] = untrusted
                self.assertEqual(
                    recommend(context(driving_seconds=10, traffic=TrafficEvidence(**data)),
                              [stop()])["reason"], "not_due", (field, untrusted))

    def test_invalid_urgent_flag_rejected(self):
        self.assertEqual(
            recommend(context(urgent_wc=1), [stop()])["reason"], "invalid_context")

    def test_invalid_stops_container_rejected(self):
        self.assertEqual(recommend(context(), None)["reason"], "invalid_context")

    def test_three_options_maximum(self):
        stops = [stop(id=f"wc-{i}") for i in range(10)]
        self.assertEqual(len(recommend(context(), stops)["options"]), 3)

    def test_invalid_duplicate_does_not_shadow_valid_candidate(self):
        invalid = stop(route=route(access_verified=False))
        valid = stop()
        self.assertEqual(len(recommend(context(), [invalid, valid])["options"]), 1)

    def test_no_route_mutation_output(self):
        self.assertNotIn("new_route", recommend(context(), [stop()]))
        self.assertNotIn("accepted", recommend(context(), [stop()]))


if __name__ == "__main__":
    unittest.main()
