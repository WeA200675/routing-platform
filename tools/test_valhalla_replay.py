import json
import unittest

from backend.valhalla_replay import ReplayUnavailable, replay
from backend.trace_regression import InvalidReplayEvidence


def fixture():
    return {
        "route_id": "test-route", "map_version": "tiles-sha256-abc",
        "locations": [{"lat": 48.4, "lon": 11.9}, {"lat": 48.5, "lon": 12.0}],
        "reference_directions": ["other", "right", "left", "other"],
    }


def mock_transport(types):
    response = json.dumps({"trip": {"legs": [{"maneuvers": [{"type": kind} for kind in types]}]}}).encode()
    return lambda url, data, timeout: response


class ValhallaReplayTest(unittest.TestCase):
    def test_matching_maneuvers_are_not_auto_verified(self):
        result = replay(fixture(), transport=mock_transport([1, 10, 15, 4]))
        self.assertTrue(result["passed"])
        self.assertFalse(result["verified"])
        self.assertFalse(result["promotion_eligible"])
        self.assertEqual(len(result["response_sha256"]), 64)

    def test_wrong_turn_fails(self):
        result = replay(fixture(), transport=mock_transport([1, 15, 15, 4]))
        self.assertFalse(result["passed"])

    def test_missing_maneuver_fails(self):
        result = replay(fixture(), transport=mock_transport([1, 10, 4]))
        self.assertFalse(result["passed"])

    def test_rejects_unknown_maneuver(self):
        with self.assertRaises(InvalidReplayEvidence):
            replay(fixture(), transport=mock_transport([1, 999, 15, 4]))

    def test_rejects_invalid_coordinates(self):
        case = fixture()
        case["locations"][0]["lat"] = 999
        with self.assertRaises(InvalidReplayEvidence):
            replay(case, transport=mock_transport([1, 10, 15, 4]))

    def test_rejects_remote_endpoint(self):
        with self.assertRaises(ReplayUnavailable):
            replay(fixture(), endpoint="https://example.org", transport=mock_transport([1, 10, 15, 4]))

    def test_service_failure_fails_closed(self):
        def unavailable(*args):
            raise OSError("offline")
        with self.assertRaises(ReplayUnavailable):
            replay(fixture(), transport=unavailable)

    def test_reproducible_evidence_hashes(self):
        a = replay(fixture(), transport=mock_transport([1, 10, 15, 4]))
        b = replay(fixture(), transport=mock_transport([1, 10, 15, 4]))
        self.assertEqual(a["fixture_sha256"], b["fixture_sha256"])
        self.assertEqual(a["response_sha256"], b["response_sha256"])


if __name__ == "__main__":
    unittest.main()
