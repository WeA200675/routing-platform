import json
import pathlib
import tempfile
import time
import unittest
import urllib.request
from threading import Thread

from backend import traffic_intelligence_hub as hub


class HubTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = hub.HubStore(pathlib.Path(self.temp.name) / "hub.sqlite", {"die-autobahn"})
        self.now = 1_800_000_000_000

    def tearDown(self):
        self.temp.cleanup()

    def event(self, **changes):
        value = {
            "id": "event-1", "kind": "congestion", "severity": 3,
            "latitude": 48.0, "longitude": 11.0,
            "observedAtEpochMillis": self.now - 20_000,
            "validUntilEpochMillis": self.now + 60_000,
            "estimatedDelaySeconds": 180, "roadLabel": "A 92",
            "description": "Queue", "sourceUrl": "https://example.test/event",
            "confidence": 0.9,
        }
        value.update(changes)
        return value

    def test_ingest_preserves_provider_attribution_and_is_idempotent(self):
        self.assertEqual(1, self.store.ingest("die-autobahn", "Die Autobahn", [self.event()], self.now))
        self.assertEqual(1, self.store.ingest("die-autobahn", "Die Autobahn", [self.event(estimatedDelaySeconds=240)], self.now))
        events = self.store.events()
        self.assertEqual(1, len(events))
        self.assertEqual("Die Autobahn", events[0]["attribution"])
        self.assertEqual(240, events[0]["estimatedDelaySeconds"])

    def test_unknown_provider_and_invalid_coordinates_fail_closed(self):
        with self.assertRaises(hub.HubError):
            self.store.ingest("unknown", "Unknown", [self.event()], self.now)
        with self.assertRaises(hub.HubError):
            self.store.ingest("die-autobahn", "Die Autobahn", [self.event(latitude=91)], self.now)

    def test_invalid_source_delay_and_validity_are_rejected(self):
        for changes in ({"estimatedDelaySeconds": -1}, {"sourceUrl": "http://bad.test"},
                        {"validUntilEpochMillis": self.now - 30_000}, {"severity": 6}):
            with self.subTest(changes=changes), self.assertRaises(hub.HubError):
                hub.normalize_event(self.event(**changes), "die-autobahn", "Die Autobahn", self.now)

    def test_route_corridor_returns_only_fresh_valid_events(self):
        good = hub.normalize_event(self.event(), "die-autobahn", "Die Autobahn", self.now)
        stale = hub.normalize_event(self.event(id="stale", observedAtEpochMillis=self.now-700_000), "die-autobahn", "Die Autobahn", self.now)
        offroute = hub.normalize_event(self.event(id="off", latitude=49.0), "die-autobahn", "Die Autobahn", self.now)
        result = hub.route_corridor_events([good, stale, offroute], [[48.0, 10.99], [48.0, 11.01]], self.now)
        self.assertEqual(["event-1"], [item["id"] for item in result])
        self.assertEqual(20, result[0]["ageSeconds"])

    def test_unknown_delay_is_not_invented(self):
        e = hub.normalize_event(self.event(estimatedDelaySeconds=None), "die-autobahn", "Die Autobahn", self.now)
        self.assertIsNone(e["estimatedDelaySeconds"])

    def test_geojson_adapter_requires_point_and_canonical_timestamps(self):
        feature = {"type":"Feature","id":"g1","geometry":{"type":"Point","coordinates":[11.0,48.0]},
                   "properties":{"kind":"hazard","severity":4,"observedAtEpochMillis":self.now,
                                 "validUntilEpochMillis":self.now+30_000}}
        result = hub.parse_geojson({"type":"FeatureCollection","features":[feature]},
                                   "die-autobahn","Die Autobahn",self.now)
        self.assertEqual("hazard", result[0]["kind"])
        with self.assertRaises(hub.HubError):
            hub.parse_geojson({"type":"FeatureCollection","features":[
                {"type":"Feature","geometry":{"type":"LineString","coordinates":[]},"properties":{}}]},
                "die-autobahn","Die Autobahn",self.now)

    def test_unapproved_knowledge_cannot_reach_search_or_training(self):
        self.store.add_knowledge({"id":"guide-1","title":"Traffic facts","text":"Delay only when source reports delay",
                                  "sourceUrl":"https://example.test/guide","attribution":"Example",
                                  "license":"CC BY 4.0","approved":False,"updatedAtEpochMillis":self.now})
        self.assertEqual([], self.store.search_knowledge("traffic delay"))
        with self.assertRaises(hub.HubError):
            self.store.add_training_example({"id":"ex-1","task":"traffic_summary","prompt":"Summarize",
                "expectedAnswer":"There is congestion","knowledgeIds":["guide-1"],"createdAtEpochMillis":self.now})

    def test_training_examples_require_approved_knowledge_for_traffic_claims(self):
        self.store.add_knowledge({"id":"guide-1","title":"Traffic facts","text":"Delay only when source reports delay",
                                  "sourceUrl":"https://example.test/guide","attribution":"Example",
                                  "license":"CC BY 4.0","approved":True,"updatedAtEpochMillis":self.now})
        example = self.store.add_training_example({"id":"ex-1","task":"traffic_summary","prompt":"Summarize",
            "expectedAnswer":"The source reports 3 minutes of delay","knowledgeIds":["guide-1"],
            "createdAtEpochMillis":self.now})
        self.assertEqual("traffic_summary", example["task"])
        with self.assertRaises(hub.HubError):
            self.store.add_training_example({"id":"ex-2","task":"traffic_summary","prompt":"Summarize",
                "expectedAnswer":"Traffic is clear","knowledgeIds":[],"createdAtEpochMillis":self.now})

    def test_inference_metrics_aggregate_without_storing_prompt_or_answer(self):
        self.store.record_inference_metric({
            "id": "metric-success", "modelId": "Qwen2.5-1.5B-Instruct",
            "task": "conversation", "outcome": "success", "elapsedMillis": 250,
            "prompt": "must never be stored", "answer": "must never be stored"})
        self.store.record_inference_metric({
            "id": "metric-fallback", "modelId": "Qwen2.5-1.5B-Instruct",
            "task": "conversation", "outcome": "fallback", "elapsedMillis": 50})
        self.store.record_inference_metric({
            "id": "metric-error", "modelId": "Qwen2.5-1.5B-Instruct",
            "task": "gps_confidence", "outcome": "error", "elapsedMillis": 0})
        summary = self.store.inference_summary()
        self.assertEqual(3, summary["sampleCount30d"])
        self.assertEqual(1, summary["successCount30d"])
        self.assertEqual(1, summary["fallbackCount30d"])
        self.assertEqual(1, summary["errorCount30d"])
        self.assertEqual(250, summary["p95LatencyMillis30d"])
        with self.store.connect() as db:
            columns = {row["name"] for row in db.execute("PRAGMA table_info(inference_metrics)")}
            self.assertNotIn("prompt", columns)
            self.assertNotIn("answer", columns)
            self.assertEqual(3, db.execute("SELECT COUNT(*) FROM inference_metrics").fetchone()[0])

    def test_invalid_inference_metrics_are_rejected(self):
        for metric in (
            {"modelId": "model", "task": "conversation", "outcome": "maybe", "elapsedMillis": 1},
            {"modelId": "model", "task": "conversation", "outcome": "success", "elapsedMillis": 120001},
            {"modelId": "model", "task": "unknown", "outcome": "success", "elapsedMillis": 1},
        ):
            with self.subTest(metric=metric), self.assertRaises(hub.HubError):
                self.store.record_inference_metric(metric)

    def test_dashboard_training_runs_are_persistent_and_report_data_checks_only(self):
        run = self.store.create_training_run("GPS confidence", "gps_confidence")
        self.assertEqual("queued", run["status"])
        self.assertEqual(0, run["progress"])
        self.store.update_training_run(run["id"], status="completed", progress=100,
                                       stage="Datensatzprüfung abgeschlossen",
                                       metrics={"examplesChecked": 0, "weightUpdate": False})
        saved = self.store.training_runs()[0]
        self.assertEqual(run["id"], saved["id"])
        self.assertEqual("completed", saved["status"])
        self.assertIn('"weightUpdate":false', saved["metrics"])

    def test_gps_confidence_examples_can_be_curated(self):
        example = self.store.add_training_example({
            "id": "gps-ex-1", "task": "gps_confidence",
            "prompt": "GNSS confidence low; retain last stable route match.",
            "expectedAnswer": "Position unsicher; keep route progress stable.",
            "knowledgeIds": [], "createdAtEpochMillis": self.now})
        self.assertEqual("gps_confidence", example["task"])

    def test_dashboard_api_is_authenticated_and_training_run_completes(self):
        server = hub.create_server("127.0.0.1", 0, pathlib.Path(self.temp.name)/"dashboard.sqlite",
                                   {"die-autobahn"}, "t"*32)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_port}"
        headers = {"Authorization": "Bearer " + "t"*32, "Content-Type": "application/json"}
        try:
            unauth = urllib.request.Request(base + "/v1/dashboard")
            with self.assertRaises(Exception):
                urllib.request.urlopen(unauth)
            request = urllib.request.Request(base + "/v1/dashboard", headers=headers)
            with urllib.request.urlopen(request) as response:
                dashboard = json.load(response)
            self.assertEqual("not_measured", dashboard["model"]["quality"])
            self.assertEqual(0, dashboard["inference"]["sampleCount30d"])
            metric_request = urllib.request.Request(
                base + "/v1/metrics/inference",
                data=json.dumps({"modelId":"local-test","task":"conversation",
                                 "outcome":"success","elapsedMillis":123,
                                 "prompt":"must not be stored"}).encode(),
                headers=headers, method="POST")
            with urllib.request.urlopen(metric_request) as response:
                self.assertEqual(202, response.status)
            request = urllib.request.Request(base + "/v1/dashboard", headers=headers)
            with urllib.request.urlopen(request) as response:
                measured = json.load(response)["inference"]
            self.assertEqual(1, measured["sampleCount30d"])
            self.assertEqual(1.0, measured["successRate30d"])
            page = urllib.request.urlopen(base + "/dashboard")
            self.assertIn("KI-Laufzeit", page.read().decode("utf-8"))
            create = urllib.request.Request(base + "/v1/training/runs", data=json.dumps({
                "name": "speech check", "preset": "speech_clarity", "start": True
            }).encode(), headers=headers, method="POST")
            with urllib.request.urlopen(create) as response:
                run = json.load(response)
            deadline = time.monotonic() + 3
            while time.monotonic() < deadline:
                request = urllib.request.Request(base + "/v1/training/runs", headers=headers)
                with urllib.request.urlopen(request) as response:
                    runs = json.load(response)["runs"]
                saved = next(item for item in runs if item["id"] == run["id"])
                if saved["status"] in {"completed", "failed"}:
                    break
                time.sleep(0.01)
            self.assertEqual("completed", saved["status"])
            metrics = json.loads(saved["metrics"])
            self.assertFalse(metrics["weightUpdate"])
            events = urllib.request.Request(base + "/v1/traffic/events", headers=headers)
            with urllib.request.urlopen(events) as response:
                self.assertEqual([], json.load(response)["events"])
        finally:
            server.shutdown()
            server.server_close()

    def test_http_public_route_lookup_falls_back_to_no_current_events(self):
        server = hub.create_server("127.0.0.1", 0, pathlib.Path(self.temp.name)/"http.sqlite",
                                   {"die-autobahn"}, "t"*32)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f"http://127.0.0.1:{server.server_port}/v1/traffic/route-events"
            request = urllib.request.Request(url, data=json.dumps({"geometry":[[48,11],[48.1,11.1]]}).encode(),
                                             headers={"Authorization":"Bearer "+"t"*32,"Content-Type":"application/json"})
            with urllib.request.urlopen(request) as response:
                payload = json.load(response)
            self.assertEqual("no_current_events", payload["status"])
            self.assertEqual([], payload["events"])
        finally:
            server.shutdown()
            server.server_close()
