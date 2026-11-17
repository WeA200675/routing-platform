import unittest

from backend.journey_break_service import Context
from backend.journey_pipeline import VerifiedPoi, evaluate_journey
from backend.journey_valhalla_adapter import Journey


class PoiSource:
    def __init__(self, candidates, fail=False):
        self.candidates = candidates
        self.fail = fail

    def find_candidates(self, journey, *, limit):
        if self.fail:
            raise OSError("offline index unavailable")
        return iter(self.candidates)


class Router:
    def __init__(self, via=1450, fail=False):
        self.calls = 0
        self.via = via
        self.fail = fail

    def route(self, *, origin, destination, via, family):
        self.calls += 1
        if self.fail:
            raise TimeoutError("Valhalla unavailable")
        return {"engineName": "valhalla", "durationS": self.via if via else 1000}


def poi(**changes):
    data = dict(id="wc-1", name="Rastplatz", latitude=48.1,
                longitude=11.1, wc_evidence="verified", source="offline-osm",
                source_version="osm-2026", verified_map_version="tiles-2026",
                legal_access_and_return=True, same_direction=True,
                exit_ahead=True, before_congestion=True,
                route_order_verified=True)
    data.update(changes)
    return VerifiedPoi(**data)


def run(candidates=None, router=None, journey=None, context=None, limit=16):
    return evaluate_journey(
        context or Context(5401, 60001, 10000),
        journey or Journey((48, 11), (49, 12), map_version="tiles-2026"),
        PoiSource([poi()] if candidates is None else candidates),
        router or Router(), candidate_limit=limit,
    )


class JourneyPipelineTest(unittest.TestCase):
    def test_full_pipeline_recommends_verified_stop(self):
        self.assertEqual(run()["options"][0]["detour_seconds"], 450)

    def test_missing_wc_evidence_never_routes(self):
        router = Router()
        self.assertEqual(run([poi(wc_evidence="unknown")], router)["options"], [])
        self.assertEqual(router.calls, 0)

    def test_missing_route_order_never_routes(self):
        router = Router()
        self.assertEqual(run([poi(route_order_verified=False)], router)["options"], [])
        self.assertEqual(router.calls, 0)

    def test_illegal_access_never_routes(self):
        router = Router()
        self.assertEqual(run([poi(legal_access_and_return=False)], router)["options"], [])
        self.assertEqual(router.calls, 0)

    def test_mismatched_map_never_routes(self):
        router = Router()
        self.assertEqual(run([poi(verified_map_version="other")], router)["options"], [])
        self.assertEqual(router.calls, 0)

    def test_route_timeout_fails_closed(self):
        self.assertEqual(run(router=Router(fail=True))["options"], [])

    def test_detour_over_600_fails_closed(self):
        self.assertEqual(run(router=Router(via=1601))["options"], [])

    def test_urgent_mode(self):
        result = run(context=Context(1, 100, 10000, urgent_wc=True))
        self.assertEqual(result["reason"], "urgent_wc")

    def test_source_unavailable(self):
        result = evaluate_journey(Context(5401, 60001, 10000),
                                  Journey((48, 11), (49, 12), map_version="tiles-2026"),
                                  PoiSource([], fail=True), Router())
        self.assertEqual(result["reason"], "poi_source_unavailable")

    def test_candidate_limit_enforced(self):
        self.assertEqual(run([poi(id=str(i)) for i in range(17)])["reason"],
                         "poi_source_limit_exceeded")

    def test_invalid_candidate_limit(self):
        self.assertEqual(run(limit=0)["reason"], "invalid_candidate_limit")

    def test_no_candidate(self):
        self.assertEqual(run([])["options"], [])


if __name__ == "__main__":
    unittest.main()
