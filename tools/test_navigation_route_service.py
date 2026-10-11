#!/usr/bin/env python3

import os
import sys
import unittest

import navigation_route_service as service


class NavigationRouteServiceTest(unittest.TestCase):
    def test_no_suitable_edges_is_structured_and_not_retryable(self):
        error = service.classify_exporter_failure(
            1,
            (
                "Tile extract successfully loaded\n"
                "FAIL: VALHALLA_ROUTE_FAILED - "
                "No suitable edges near location\n"
            ),
        )

        self.assertEqual(
            422,
            error.status,
        )

        self.assertEqual(
            "no_suitable_edges",
            error.code,
        )

        self.assertFalse(
            error.retryable
        )

        payload = service.error_payload(
            error
        )

        self.assertEqual(
            1,
            payload["schemaVersion"],
        )

        self.assertNotIn(
            "Tile extract",
            payload["error"]["message"],
        )

    def test_generic_exporter_failure_hides_backend_log(self):
        secret_backend_log = (
            "sensitive internal exporter details"
        )

        error = service.classify_exporter_failure(
            9,
            secret_backend_log,
        )

        self.assertEqual(
            502,
            error.status,
        )

        self.assertEqual(
            "route_export_failed",
            error.code,
        )

        self.assertTrue(
            error.retryable
        )

        self.assertNotIn(
            secret_backend_log,
            error.message,
        )

    def test_readiness_reports_dependencies_without_routing(self):
        ready = service.readiness_snapshot(
            sys.executable,
            __file__,
        )

        self.assertEqual(
            "ready",
            ready["status"],
        )

        self.assertTrue(
            all(
                ready["checks"].values()
            )
        )

    def test_readiness_fails_when_exporter_is_missing(self):
        missing = service.readiness_snapshot(
            os.path.join(
                os.path.dirname(__file__),
                "definitely-missing-exporter",
            ),
            __file__,
        )

        self.assertEqual(
            "not_ready",
            missing["status"],
        )

        self.assertFalse(
            missing["checks"]["exporterFile"],
        )


if __name__ == "__main__":
    unittest.main()