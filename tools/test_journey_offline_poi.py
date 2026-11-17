import json
import hashlib
import os
import tempfile
import unittest

from backend.journey_offline_poi import OfflinePoiIndex
from backend.journey_valhalla_adapter import Journey


def entry(**kwargs):
    data = dict(id="wc-1", name="WC", latitude=48.2, longitude=11.2,
                source="audited-osm", source_version="2026-11",
                wc_verified=True, legal_access_and_return=True,
                same_direction=True, exit_ahead=True, route_order_verified=True)
    data.update(kwargs)
    return data


class OfflinePoiIndexTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = os.path.join(self.tmp.name, "pois.json")
        self.journey = Journey((48, 11), (49, 12), map_version="tiles-v1")
        self.trusted_sha256 = ""

    def write(self, entries, version="tiles-v1"):
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(dict(schema_version="journey-poi-v1",
                           map_version=version, stops=entries), f)
        with open(self.path, "rb") as f:
            self.trusted_sha256 = hashlib.sha256(f.read()).hexdigest()

    def read(self, limit=16):
        return OfflinePoiIndex(self.path, "tiles-v1", trusted_sha256=self.trusted_sha256).find_candidates(self.journey, limit=limit)

    def test_verified_candidate(self):
        self.write([entry()])
        self.assertEqual(self.read()[0].id, "wc-1")

    def test_unverified_wc_rejected(self):
        self.write([entry(wc_verified=False)])
        self.assertEqual(self.read(), [])

    def test_unverified_access_rejected(self):
        self.write([entry(legal_access_and_return=False)])
        self.assertEqual(self.read(), [])

    def test_unverified_direction_rejected(self):
        self.write([entry(same_direction=False)])
        self.assertEqual(self.read(), [])

    def test_unverified_order_rejected(self):
        self.write([entry(route_order_verified=False)])
        self.assertEqual(self.read(), [])

    def test_map_mismatch_rejected(self):
        self.write([entry()], version="old")
        self.assertEqual(self.read(), [])

    def test_bad_coordinates_rejected(self):
        self.write([entry(latitude=100)])
        self.assertEqual(self.read(), [])

    def test_bounded_output(self):
        self.write([entry(id=str(i)) for i in range(20)])
        self.assertEqual(len(self.read(limit=3)), 4)

    def test_symlink_rejected(self):
        self.write([entry()])
        alias = os.path.join(self.tmp.name, "alias.json")
        os.symlink(self.path, alias)
        with self.assertRaises((OSError, ValueError)):
            OfflinePoiIndex(alias, "tiles-v1", trusted_sha256=self.trusted_sha256).find_candidates(self.journey, limit=16)

    def test_missing_trusted_digest_rejected(self):
        self.write([entry()])
        self.trusted_sha256 = ""
        self.assertEqual(self.read(), [])

    def test_modified_index_rejected(self):
        self.write([entry()])
        with open(self.path, "ab") as f:
            f.write(b" ")
        self.assertEqual(self.read(), [])

    def test_invalid_schema_rejected(self):
        with open(self.path, "w", encoding="utf-8") as f:
            f.write("{}")
        with open(self.path, "rb") as f:
            self.trusted_sha256 = hashlib.sha256(f.read()).hexdigest()
        with self.assertRaises(ValueError):
            self.read()


if __name__ == "__main__":
    unittest.main()
