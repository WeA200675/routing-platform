import http.client
import json
import threading
import unittest

from backend.journey_gateway import JourneyGateway


class JourneyGatewayTest(unittest.TestCase):
    def setUp(self):
        self.server = JourneyGateway(("127.0.0.1", 0))
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join(timeout=3)

    def request(self, method, path, body=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=2)
        try:
            conn.request(method, path, body=body)
            result = conn.getresponse()
            payload = json.loads(result.read().decode())
            return result.status, payload, result.getheader("Cache-Control")
        finally:
            conn.close()

    def test_status_is_explicitly_degraded(self):
        status, data, cache = self.request("GET", "/api/journey/v1/status")
        self.assertEqual(status, 200)
        self.assertEqual(data["schema_version"], "journey-backend-v1")
        self.assertFalse(data["recommendations_enabled"])
        self.assertEqual(cache, "no-store")

    def test_readiness_is_not_claimed(self):
        status, data, _ = self.request("GET", "/ready")
        self.assertEqual(status, 503)
        self.assertFalse(data["ready"])

    def test_untrusted_recommendations_rejected(self):
        status, data, _ = self.request("POST", "/api/journey/v1/recommend",
                                       '{"stops":[{"wc_verified":true}]}')
        self.assertEqual(status, 503)
        self.assertEqual(data["options"], [])
        self.assertFalse(data["route_mutated"])

    def test_unknown_route(self):
        status, data, _ = self.request("GET", "/private")
        self.assertEqual(status, 404)

    def test_non_loopback_binding_refused(self):
        with self.assertRaises(ValueError):
            JourneyGateway(("0.0.0.0", 0))


if __name__ == "__main__":
    unittest.main()
