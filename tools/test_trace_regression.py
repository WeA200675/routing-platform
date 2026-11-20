import unittest

from backend.trace_regression import InvalidReplayEvidence, evaluate_trace


def traces():
    base = {"route_id": "route-1", "map_version": "map-a", "input_sha256": "abc"}
    ref = dict(base, maneuvers=[{"id": "m1", "direction": "right"},
                                {"id": "m2", "direction": "left"}])
    candidate = dict(base, maneuvers=[
        {"id": "m1", "direction": "right", "voice_delay_ms": 100, "gps_error_m": 3, "fallback_used": False},
        {"id": "m2", "direction": "left", "voice_delay_ms": 150, "gps_error_m": 4, "fallback_used": False},
    ])
    return ref, candidate


class TraceRegressionTest(unittest.TestCase):
    def test_complete_matching_trace_is_advisory(self):
        ref, cand = traces()
        result = evaluate_trace(ref, cand)
        self.assertTrue(result["passed"])
        self.assertFalse(result["verified"])
        self.assertFalse(result["promotion_eligible"])
        self.assertEqual(result["maneuver_accuracy"], 1)

    def test_wrong_direction_fails(self):
        ref, cand = traces()
        cand["maneuvers"][0]["direction"] = "left"
        self.assertFalse(evaluate_trace(ref, cand)["passed"])

    def test_voice_gps_and_fallback_fail(self):
        for field, value in [("voice_delay_ms", 999), ("gps_error_m", 99), ("fallback_used", True)]:
            with self.subTest(field=field):
                ref, cand = traces()
                cand["maneuvers"][0][field] = value
                self.assertFalse(evaluate_trace(ref, cand)["passed"])

    def test_missing_evidence_rejected(self):
        for field in ("voice_delay_ms", "gps_error_m", "fallback_used"):
            with self.subTest(field=field):
                ref, cand = traces()
                del cand["maneuvers"][0][field]
                with self.assertRaises(InvalidReplayEvidence):
                    evaluate_trace(ref, cand)

    def test_mismatched_map_and_route_rejected(self):
        for field in ("map_version", "route_id", "input_sha256"):
            with self.subTest(field=field):
                ref, cand = traces()
                cand[field] = "other"
                with self.assertRaises(InvalidReplayEvidence):
                    evaluate_trace(ref, cand)

    def test_duplicate_or_misaligned_maneuvers_rejected(self):
        ref, cand = traces()
        cand["maneuvers"][1]["id"] = "m1"
        with self.assertRaises(InvalidReplayEvidence):
            evaluate_trace(ref, cand)

    def test_result_digest_deterministic(self):
        ref, cand = traces()
        self.assertEqual(evaluate_trace(ref, cand)["input_sha256"],
                         evaluate_trace(ref, cand)["input_sha256"])


if __name__ == "__main__":
    unittest.main()
