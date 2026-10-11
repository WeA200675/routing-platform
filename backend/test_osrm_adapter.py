import importlib.util
import pathlib
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location(
    "osrm_adapter", pathlib.Path(__file__).with_name("osrm_adapter.py")
)
adapter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(adapter)


class Response:
    def __enter__(self): return self
    def __exit__(self, *args): pass


class OsrmAdapterTest(unittest.TestCase):
    def request(self, family="profile_optimal"):
        return {
            "origin": {"latitude": 48.5, "longitude": 12.1},
            "destination": {"latitude": 48.6, "longitude": 12.2},
            "viaPoints": [{"latitude": 48.55, "longitude": 12.15}],
            "family": family,
        }

    def osrm(self):
        return {
            "code": "Ok",
            "routes": [{
                "distance": 1234.0,
                "duration": 321.0,
                "geometry": {"coordinates": [[12.1,48.5],[12.15,48.55],[12.2,48.6]]},
                "legs": [{"steps": [{
                    "distance": 1234.0, "duration": 321.0, "name": "Teststraße",
                    "maneuver": {"type": "depart", "location": [12.1,48.5],
                                 "bearing_before": 0, "bearing_after": 90},
                }]}],
            }],
        }

    def test_real_engine_values_are_preserved_and_traffic_is_not_claimed(self):
        response = Response()
        response.read = lambda: b""
        with mock.patch.object(adapter.urllib.request, "urlopen", return_value=response), \
             mock.patch.object(adapter.json, "load", return_value=self.osrm()):
            result = adapter.route(self.request())
        self.assertEqual(1234.0, result["distanceM"])
        self.assertEqual(321.0, result["durationS"])
        self.assertEqual([[48.5,12.1],[48.55,12.15],[48.6,12.2]], result["geometry"])
        self.assertEqual("unavailable", result["segmentDataStatus"])
        self.assertEqual("traffic_unavailable", result["diagnostics"][0]["code"])

    def test_via_order_is_sent_to_engine_exactly(self):
        response = Response()
        with mock.patch.object(adapter.urllib.request, "urlopen", return_value=response) as call, \
             mock.patch.object(adapter.json, "load", return_value=self.osrm()):
            adapter.route(self.request())
        url = call.call_args.args[0]
        self.assertIn("12.1,48.5;12.15,48.55;12.2,48.6", url)

    def test_unsupported_family_fails_closed_without_network(self):
        with mock.patch.object(adapter.urllib.request, "urlopen") as call:
            with self.assertRaises(ValueError):
                adapter.route(self.request("low_traffic"))
        call.assert_not_called()

    def test_invalid_coordinate_fails_closed(self):
        request = self.request()
        request["origin"]["latitude"] = 91
        with self.assertRaises(ValueError):
            adapter.route(request)


if __name__ == "__main__":
    unittest.main()
