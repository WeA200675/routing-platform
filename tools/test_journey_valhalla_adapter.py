import unittest

from backend.journey_valhalla_adapter import (
    Journey, RouteComparisonUnavailable, compare_via_stop,
)


class FakeRouter:
    def __init__(self, direct=1000, via=1500):
        self.direct, self.via = direct, via
        self.calls = 0

    def route(self, *, origin, destination, via, family):
        self.calls += 1
        return {"engineName": "valhalla", "durationS":
                self.via if via else self.direct}


def compare(router=None, **kwargs):
    args = dict(verified_map_version="tiles-1", legal_access_and_return=True,
                same_direction=True, exit_ahead=True)
    args.update(kwargs)
    return compare_via_stop(router or FakeRouter(),
                            Journey((48.1, 11.1), (48.2, 11.2), map_version="tiles-1"),
                            (48.15, 11.15), **args)


class JourneyValhallaAdapterTest(unittest.TestCase):
    def test_full_route_difference(self):
        self.assertEqual(compare().detour_seconds(), 500)

    def test_exact_boundary(self):
        self.assertEqual(compare(FakeRouter(via=1600)).detour_seconds(), 600)

    def test_over_boundary(self):
        self.assertEqual(compare(FakeRouter(via=1601)).detour_seconds(), 601)

    def test_map_mismatch_never_calls_router(self):
        router = FakeRouter()
        with self.assertRaises(RouteComparisonUnavailable):
            compare(router, verified_map_version="other")
        self.assertEqual(router.calls, 0)

    def test_unknown_map_never_calls_router(self):
        with self.assertRaises(RouteComparisonUnavailable):
            compare(verified_map_version="")

    def test_illegal_access_never_calls_router(self):
        router = FakeRouter()
        with self.assertRaises(RouteComparisonUnavailable):
            compare(router, legal_access_and_return=False)
        self.assertEqual(router.calls, 0)

    def test_wrong_carriageway_never_calls_router(self):
        with self.assertRaises(RouteComparisonUnavailable):
            compare(same_direction=False)

    def test_exit_behind_never_calls_router(self):
        with self.assertRaises(RouteComparisonUnavailable):
            compare(exit_ahead=False)

    def test_missing_total_duration_fails_closed(self):
        with self.assertRaises(RouteComparisonUnavailable):
            compare(FakeRouter(direct=None))

    def test_negative_or_shorter_route_fails_policy(self):
        self.assertIsNone(compare(FakeRouter(via=900)).detour_seconds())

    def test_existing_via_points_rejected_until_insertion_verified(self):
        router = FakeRouter()
        with self.assertRaises(RouteComparisonUnavailable):
            compare_via_stop(router, Journey((48, 11), (49, 12), ((48.5, 11.5),),
                                              map_version="tiles-1"),
                             (48.6, 11.6), verified_map_version="tiles-1",
                             legal_access_and_return=True, same_direction=True,
                             exit_ahead=True)
        self.assertEqual(router.calls, 0)


if __name__ == "__main__":
    unittest.main()
