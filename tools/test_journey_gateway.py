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

    def request(self, method, path, body=None, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=2)
        try:
            conn.request(method, path, body=body, headers=headers or {})
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
        self.assertFalse(data["navigation_routing_enabled"])
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

    def test_route_endpoint_default_off(self):
        status, data, _ = self.request("POST", "/api/navigation/v1/route", "{}")
        self.assertEqual(status, 503)
        self.assertEqual(data["error"], "routing_disabled")

    def test_authenticated_route_proxy(self):
        class Provider:
            def route(self, **kwargs):
                return {"engineName": "valhalla", "durationS": 42}
        self.server.shutdown()
        self.server.server_close()
        self.worker.join(timeout=3)
        self.server = JourneyGateway(("127.0.0.1", 0),
                                     route_provider=Provider(), route_token="x" * 40)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
        body = json.dumps({
            "origin": {"latitude": 48, "longitude": 11},
            "destination": {"latitude": 49, "longitude": 12},
            "viaPoints": [], "family": "fastest"
        })
        status, data, _ = self.request("POST", "/api/navigation/v1/route", body,
                                       {"Authorization": "Bearer " + "x" * 40})
        self.assertEqual(status, 200)
        self.assertEqual(data["durationS"], 42)
        _, status_data, _ = self.request("GET", "/api/journey/v1/status")
        self.assertTrue(status_data["navigation_routing_enabled"])
        self.assertFalse(status_data["recommendations_enabled"])

    def test_proxy_requires_authentication(self):
        class Provider:
            def route(self, **kwargs):
                raise AssertionError("unauthenticated request reached router")
        self.server.shutdown()
        self.server.server_close()
        self.worker.join(timeout=3)
        self.server = JourneyGateway(("127.0.0.1", 0),
                                     route_provider=Provider(), route_token="x" * 40)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
        status, data, _ = self.request("POST", "/api/navigation/v1/route", "{}")
        self.assertEqual(status, 401)
        self.assertEqual(data["error"], "unauthorized")

    def test_proxy_rejects_invalid_coordinates(self):
        class Provider:
            def route(self, **kwargs):
                raise AssertionError("invalid coordinates reached router")
        self.server.shutdown()
        self.server.server_close()
        self.worker.join(timeout=3)
        self.server = JourneyGateway(("127.0.0.1", 0),
                                     route_provider=Provider(), route_token="x" * 40)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
        body = json.dumps({
            "origin": {"latitude": 999, "longitude": 11},
            "destination": {"latitude": 49, "longitude": 12}
        })
        status, data, _ = self.request("POST", "/api/navigation/v1/route", body,
                                       {"Authorization": "Bearer " + "x" * 40})
        self.assertEqual(status, 400)
        self.assertEqual(data["error"], "invalid_route_request")

    def test_proxy_failure_is_recoverable(self):
        class Provider:
            def route(self, **kwargs):
                raise TimeoutError("Valhalla timed out")
        self.server.shutdown()
        self.server.server_close()
        self.worker.join(timeout=3)
        self.server = JourneyGateway(("127.0.0.1", 0),
                                     route_provider=Provider(), route_token="x" * 40)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
        body = json.dumps({
            "origin": {"latitude": 48, "longitude": 11},
            "destination": {"latitude": 49, "longitude": 12}
        })
        status, data, _ = self.request("POST", "/api/navigation/v1/route", body,
                                       {"Authorization": "Bearer " + "x" * 40})
        self.assertEqual(status, 503)
        self.assertTrue(data["retryable"])

    def test_non_loopback_binding_refused(self):
        with self.assertRaises(ValueError):
            JourneyGateway(("0.0.0.0", 0))


if __name__ == "__main__":
    unittest.main()
