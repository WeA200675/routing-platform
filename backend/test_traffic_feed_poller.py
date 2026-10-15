import json
import unittest
from unittest import mock

from backend import traffic_feed_poller as poller
from backend import traffic_intelligence_hub as hub


class Response:
    def __init__(self, body): self.body = body
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def read(self, size): return self.body[:size]


class FeedPollerTest(unittest.TestCase):
    def setUp(self):
        self.now = 1_800_000_000_000
        self.store = mock.Mock()
        self.feed = {"id":"test-feed","provider":"feed-a","url":"https://example.test/feed",
                     "attribution":"Example Feed","intervalSeconds":60}

    def test_config_requires_allowlisted_provider_and_https(self):
        raw = json.dumps([self.feed])
        self.assertEqual(1, len(poller.load_config(raw, {"feed-a"})))
        with self.assertRaises(ValueError):
            poller.load_config(raw, {"other"})
        with self.assertRaises(ValueError):
            poller.load_config(raw.replace("https://","http://"), {"feed-a"})

    def test_polls_geojson_with_provider_attribution_and_secret_from_environment(self):
        feature = {"type":"Feature","id":"evt-1","geometry":{"type":"Point","coordinates":[11,48]},
                   "properties":{"kind":"hazard","severity":3,"observedAtEpochMillis":self.now,
                                 "validUntilEpochMillis":self.now+60000}}
        body = json.dumps({"type":"FeatureCollection","features":[feature]}).encode()
        opener = mock.Mock(return_value=Response(body))
        worker = poller.GeoJSONFeedPoller([dict(self.feed, authorizationEnv="FEED_TOKEN")],
            self.store, clock_ms=lambda:self.now, opener=opener, environ={"FEED_TOKEN":"secret"})
        worker.poll_once(0)
        request = opener.call_args.args[0]
        self.assertEqual("Bearer secret", request.get_header("Authorization"))
        self.store.ingest.assert_called_once()
        self.assertEqual("current", worker.status["test-feed"])

    def test_bad_feed_fails_independently_and_records_unavailable(self):
        opener = mock.Mock(side_effect=RuntimeError("secret payload"))
        worker = poller.GeoJSONFeedPoller([self.feed], self.store, clock_ms=lambda:self.now, opener=opener)
        worker.poll_once(0)
        self.store.ingest.assert_not_called()
        self.assertEqual("unavailable", worker.status["test-feed"])

    def test_failed_feed_does_not_retry_before_interval(self):
        opener = mock.Mock(side_effect=RuntimeError())
        worker = poller.GeoJSONFeedPoller([self.feed], self.store, clock_ms=lambda:self.now, opener=opener)
        worker.poll_once(0)
        worker.poll_once(30)
        opener.assert_called_once()

    def test_autobahn_configuration_is_locked_to_official_https_host_and_endpoint(self):
        raw = json.dumps([dict(self.feed, format="autobahn",
            url="https://verkehr.autobahn.de/o/autobahn/A92/services/warning")])
        config = poller.load_config(raw, {"feed-a"})
        self.assertEqual(("A92", "warning"), (config[0]["roadId"], config[0]["service"]))
        for url in ("https://evil.test/o/autobahn/A92/services/warning",
                    "http://verkehr.autobahn.de/o/autobahn/A92/services/warning",
                    "https://verkehr.autobahn.de/o/autobahn/A92/details/warning/x"):
            with self.assertRaises(ValueError):
                poller.load_config(json.dumps([dict(self.feed, format="autobahn", url=url)]), {"feed-a"})

    def test_autobahn_warning_is_normalized_with_hashed_id_and_freshness(self):
        feed = {"service":"warning","roadId":"A92",
                "url":"https://verkehr.autobahn.de/o/autobahn/A92/services/warning"}
        payload = {"warning":[{"identifier":"private/provider/id==","title":"Stau",
            "description":["Fahrbahn blockiert"],"point":"11.123,48.456"}]}
        events = poller.parse_autobahn_response(payload, feed, self.now)
        self.assertEqual(1, len(events))
        self.assertTrue(events[0]["id"].startswith("ab-"))
        self.assertEqual("congestion", events[0]["kind"])
        self.assertEqual(48.456, events[0]["latitude"])
        self.assertEqual(self.now + 300_000, events[0]["validUntilEpochMillis"])
        self.assertNotIn("private/provider", events[0]["id"])

    def test_empty_autobahn_feed_is_valid_and_invalid_schema_fails_closed(self):
        feed = {"service":"closure","roadId":"A9","url":"https://verkehr.autobahn.de/o/autobahn/A9/services/closure"}
        self.assertEqual([], poller.parse_autobahn_response({"closure":[]}, feed, self.now))
        with self.assertRaises(hub.HubError):
            poller.parse_autobahn_response({"closure":{}}, feed, self.now)


if __name__ == "__main__":
    unittest.main()
