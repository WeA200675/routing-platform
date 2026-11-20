import unittest

from backend.engineering_triage import InvalidIncident, backlog, classify


def event(category="maneuver", code="WRONG_TURN", repeats=1, blocked=False):
    return {"category": category, "component": "navigation", "error_code": code,
            "repeat_count": repeats, "navigation_blocked": blocked}


class EngineeringTriageTest(unittest.TestCase):
    def test_deterministic_deduplication(self):
        first = classify(event())
        self.assertEqual(first["fingerprint"], classify(event(repeats=5))["fingerprint"])
        result = backlog([event(), event(repeats=4), event(category="sync", code="TIMEOUT")])
        self.assertEqual(result["incident_count"], 3)
        self.assertEqual(result["unique_count"], 2)
        self.assertEqual(result["items"][0]["repeat_count"], 5)
        self.assertEqual(result["items"][0]["severity"], "high")

    def test_navigation_blocker_escalates(self):
        self.assertEqual(classify(event(category="gps", blocked=True))["severity"], "critical")
        self.assertEqual(classify(event(category="crash"))["severity"], "critical")
        self.assertFalse(classify(event())["auto_merge_eligible"])

    def test_sensitive_payloads_fail_closed(self):
        for code in ("Bearer abcdef", "token=supersecret", "48.123456, 11.987654"):
            with self.subTest(code=code):
                with self.assertRaises(InvalidIncident):
                    classify(event(code=code))
        with self.assertRaises(InvalidIncident):
            classify({**event(), "raw_log": "private"})
        with self.assertRaises(InvalidIncident):
            classify(event(repeats=0))
        with self.assertRaises(InvalidIncident):
            backlog([event()] * 10001)

    def test_priority_is_stable(self):
        result = backlog([event(category="performance"), event(category="offline"), event(category="crash")])
        self.assertEqual([x["severity"] for x in result["items"]], ["critical", "high", "low"])


if __name__ == "__main__":
    unittest.main()
