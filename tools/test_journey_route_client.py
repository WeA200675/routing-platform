import io
import json
import unittest
from email.message import Message

from backend.journey_route_client import LoopbackRouteProvider, LocalRouteUnavailable, MAX_ROUTE_RESPONSE_BYTES


class Response:
    status = 200
    def __init__(self, data, content_type="application/json"):
        self.data = data
        self.headers = Message()
        self.headers["Content-Type"] = content_type

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def read(self, limit):
        return self.data[:limit]


class Opener:
    def __init__(self, response):
        self.response = response
        self.requests = []

    def open(self, request, timeout):
        self.requests.append((request, timeout))
        return self.response


class JourneyRouteClientTest(unittest.TestCase):
    def test_valid_route_payload(self):
        opener = Opener(Response(json.dumps({"engineName": "valhalla", "durationS": 12}).encode()))
        client = LoopbackRouteProvider(opener=opener)
        result = client.route(origin=(48, 11), destination=(49, 12), via=[(48.5, 11.5)],
                              family="profile_optimal")
        self.assertEqual(result["durationS"], 12)
        request, timeout = opener.requests[0]
        self.assertEqual(request.get_header("X-routing-platform-dev"), "1")
        body = json.loads(request.data)
        self.assertEqual(body["viaPoints"][0]["latitude"], 48.5)

    def test_remote_host_refused(self):
        for url in ("http://example.com/v1/navigation/route",
                    "http://192.168.1.2/v1/navigation/route",
                    "https://127.0.0.1/v1/navigation/route",
                    "http://127.0.0.1/evil"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                LoopbackRouteProvider(url)

    def test_redirect_refused(self):
        from backend.journey_route_client import _NoRedirect
        with self.assertRaises(LocalRouteUnavailable):
            _NoRedirect().redirect_request(None, None, 302, "redirect", {}, "http://evil")

    def test_html_response_refused(self):
        client = LoopbackRouteProvider(opener=Opener(Response(b"<html></html>", "text/html")))
        with self.assertRaises(LocalRouteUnavailable):
            client.route(origin=(48, 11), destination=(49, 12), via=[], family="fastest")

    def test_oversized_response_refused(self):
        client = LoopbackRouteProvider(opener=Opener(Response(b"x" * (MAX_ROUTE_RESPONSE_BYTES + 1))))
        with self.assertRaises(LocalRouteUnavailable):
            client.route(origin=(48, 11), destination=(49, 12), via=[], family="fastest")

    def test_large_valid_route_accepted(self):
        payload = json.dumps({"engineName": "valhalla",
                              "shape": "a" * (1024 * 1024 + 1)}).encode()
        client = LoopbackRouteProvider(opener=Opener(Response(payload)))
        result = client.route(origin=(48, 11), destination=(49, 12),
                              via=[], family="fastest")
        self.assertGreater(len(result["shape"]), 1024 * 1024)

    def test_invalid_timeout(self):
        with self.assertRaises(ValueError):
            LoopbackRouteProvider(timeout_seconds=0)

    def test_nan_coordinate_refused(self):
        client = LoopbackRouteProvider(opener=Opener(Response(b"{}")))
        with self.assertRaises(ValueError):
            client.route(origin=(float("nan"), 11), destination=(49, 12),
                         via=[], family="fastest")


if __name__ == "__main__":
    unittest.main()
