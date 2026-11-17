import unittest
from unittest.mock import patch

from tools.journey_production_preflight import check


class ProductionPreflightTest(unittest.TestCase):
    def test_all_checks_pass(self):
        def fake(url):
            if url.endswith("/ready"):
                return 200, {"status": "ready", "checks": {
                    "exporterExecutable": True, "configReadable": True}}
            return 200, {"schema_version": "journey-backend-v1",
                         "navigation_routing_enabled": True,
                         "recommendations_enabled": False}
        with patch("tools.journey_production_preflight.get_json", side_effect=fake):
            self.assertTrue(all(check().values()))

    def test_unconfigured_gateway_fails(self):
        def fake(url):
            if url.endswith("/ready"):
                return 200, {"status": "ready", "checks": {
                    "exporterExecutable": True, "configReadable": True}}
            return 200, {"schema_version": "journey-backend-v1",
                         "navigation_routing_enabled": False,
                         "recommendations_enabled": False}
        with patch("tools.journey_production_preflight.get_json", side_effect=fake):
            self.assertFalse(all(check().values()))

    def test_unready_bridge_fails(self):
        def fake(url):
            if url.endswith("/ready"):
                return 503, {"status": "not_ready", "checks": {}}
            return 200, {"schema_version": "journey-backend-v1",
                         "navigation_routing_enabled": True,
                         "recommendations_enabled": False}
        with patch("tools.journey_production_preflight.get_json", side_effect=fake):
            self.assertFalse(all(check().values()))


if __name__ == "__main__":
    unittest.main()
