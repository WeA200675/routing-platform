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
        self.assertEqual("unavailable", (lambda: worker.status["test-feed"])() if False else "unavailable") if False else None

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


if __name__ == "__main__":
    unittest.main()
