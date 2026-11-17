"""Read a bounded, pre-audited local POI index (no live availability claims).

Only signed/independently audited data should be placed at this trusted path
by deployment tooling. Raw OSM points are NOT verified motorway access.
"""
from __future__ import annotations

import json
import math
import os
import stat

from backend.journey_pipeline import VerifiedPoi

MAX_INDEX_BYTES = 1024 * 1024
MAX_INDEX_ENTRIES = 5000


class OfflinePoiIndex:
    def __init__(self, path: str, expected_map_version: str):
        self.path = path
        self.expected_map_version = expected_map_version

    def find_candidates(self, journey, *, limit: int):
        if not self.expected_map_version or journey.map_version != self.expected_map_version:
            return []
        if type(limit) is not int or not 1 <= limit <= 16:
            return []
        info = os.stat(self.path, follow_symlinks=False)
        if not stat.S_ISREG(info.st_mode) or info.st_size > MAX_INDEX_BYTES:
            raise ValueError("untrusted POI index file")
        with open(self.path, "rb") as stream:
            raw = stream.read(MAX_INDEX_BYTES + 1)
        if len(raw) > MAX_INDEX_BYTES:
            raise ValueError("POI index too large")
        document = json.loads(raw.decode("utf-8"))
        if not isinstance(document, dict) or document.get("schema_version") != "journey-poi-v1":
            raise ValueError("invalid POI index schema")
        if document.get("map_version") != self.expected_map_version:
            return []
        entries = document.get("stops")
        if not isinstance(entries, list) or len(entries) > MAX_INDEX_ENTRIES:
            raise ValueError("invalid POI entries")
        candidates = []
        for item in entries:
            if not isinstance(item, dict):
                continue
            # Explicit independent access attestations, never inferred from
            # proximity or generic OSM restroom tags.
            if not all(item.get(k) is True for k in
                       ("wc_verified", "legal_access_and_return",
                        "same_direction", "exit_ahead", "route_order_verified")):
                continue
            lat, lon = item.get("latitude"), item.get("longitude")
            if (type(lat) not in (int, float) or type(lon) not in (int, float)
                    or not math.isfinite(lat) or not math.isfinite(lon)
                    or not -90 <= lat <= 90 or not -180 <= lon <= 180):
                continue
            if not all(isinstance(item.get(k), str) and item[k].strip()
                       for k in ("id", "name", "source", "source_version")):
                continue
            candidates.append(VerifiedPoi(
                id=item["id"], name=item["name"], latitude=float(lat),
                longitude=float(lon), wc_evidence="verified",
                source=item["source"], source_version=item["source_version"],
                verified_map_version=self.expected_map_version,
                legal_access_and_return=True, same_direction=True,
                exit_ahead=True, before_congestion=item.get("before_congestion") is True,
                route_order_verified=True,
            ))
            if len(candidates) > limit:
                break
        return candidates
