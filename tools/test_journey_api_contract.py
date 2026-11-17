import unittest

from backend.journey_api_contract import InvalidJourneyRequest, evaluate_payload


def payload():
    return {
        "schema_version": "journey-recommendation-v1",
        "context": {"driving_seconds": 5401, "remaining_meters": 60001,
                    "now_epoch_seconds": 1000, "urgent_wc": False},
        "stops": [{
            "id": "wc-1", "name": "WC", "wc_evidence": "verified",
            "source": "offline-osm", "source_version": "v1",
            "before_congestion": True,
            "route": {"direct_seconds": 1000, "via_seconds": 1400,
                      "map_version": "v1", "via_map_version": "v1",
                      "profile": "auto", "via_profile": "auto",
                      "access_verified": True, "same_direction": True,
                      "exit_ahead": True},
        }],
    }


class JourneyApiContractTest(unittest.TestCase):
    def test_verified_candidate(self):
        result = evaluate_payload(payload())
        self.assertEqual(result["decision"]["reason"], "regular")
        self.assertEqual(len(result["decision"]["options"]), 1)
        self.assertTrue(result["requires_user_confirmation"])
        self.assertFalse(result["route_mutated"])

    def test_unknown_wc(self):
        p = payload()
        p["stops"][0]["wc_evidence"] = "unknown"
        self.assertEqual(evaluate_payload(p)["decision"]["options"], [])

    def test_601_seconds(self):
        p = payload()
        p["stops"][0]["route"]["via_seconds"] = 1601
        self.assertEqual(evaluate_payload(p)["decision"]["options"], [])

    def test_unknown_schema(self):
        p = payload()
        p["schema_version"] = "v2"
        with self.assertRaises(InvalidJourneyRequest):
            evaluate_payload(p)

    def test_invalid_bool_as_number(self):
        p = payload()
        p["context"]["driving_seconds"] = True
        with self.assertRaises(InvalidJourneyRequest):
            evaluate_payload(p)

    def test_invalid_route_evidence(self):
        p = payload()
        p["stops"][0]["route"]["access_verified"] = "yes"
        with self.assertRaises(InvalidJourneyRequest):
            evaluate_payload(p)

    def test_missing_context(self):
        p = payload()
        del p["context"]
        with self.assertRaises(InvalidJourneyRequest):
            evaluate_payload(p)

    def test_invalid_stops(self):
        p = payload()
        p["stops"] = {}
        with self.assertRaises(InvalidJourneyRequest):
            evaluate_payload(p)

    def test_empty_stops(self):
        p = payload()
        p["stops"] = []
        self.assertEqual(evaluate_payload(p)["decision"]["options"], [])

    def test_user_urgent(self):
        p = payload()
        p["context"].update(driving_seconds=5, remaining_meters=100,
                            urgent_wc=True)
        self.assertEqual(evaluate_payload(p)["decision"]["reason"], "urgent_wc")


if __name__ == "__main__":
    unittest.main()
