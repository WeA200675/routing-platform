#!/usr/bin/env python3

import json
import unittest

import navigation_fault_injection_service as service


class NavigationFaultInjectionServiceTest(unittest.TestCase):
    def test_retryable_backend_failure_is_structured(self):
        status, payload = service.route_response(
            "backend_failure"
        )

        self.assertEqual(
            502,
            status,
        )
        self.assertEqual(
            "route_export_failed",
            payload["error"]["code"],
        )
        self.assertTrue(
            payload["error"]["retryable"]
        )

    def test_timeout_is_retryable(self):
        status, payload = service.route_response(
            "routing_timeout"
        )

        self.assertEqual(
            504,
            status,
        )
        self.assertEqual(
            "routing_timeout",
            payload["error"]["code"],
        )
        self.assertTrue(
            payload["error"]["retryable"]
        )

    def test_no_suitable_edges_is_not_retryable(self):
        status, payload = service.route_response(
            "no_suitable_edges"
        )

        self.assertEqual(
            422,
            status,
        )
        self.assertEqual(
            "no_suitable_edges",
            payload["error"]["code"],
        )
        self.assertFalse(
            payload["error"]["retryable"]
        )

    def test_unknown_retryable_code_is_available_for_policy_injection(self):
        status, payload = service.route_response(
            "unknown_retryable"
        )

        self.assertEqual(
            400,
            status,
        )
        self.assertEqual(
            "future_unknown",
            payload["error"]["code"],
        )
        self.assertTrue(
            payload["error"]["retryable"]
        )

    def test_invalid_response_is_deliberately_malformed_json(self):
        status, payload = service.route_response(
            "invalid_response"
        )

        self.assertEqual(
            200,
            status,
        )

        with self.assertRaises(
            json.JSONDecodeError
        ):
            json.loads(
                payload.decode(
                    "utf-8"
                )
            )

    def test_stats_are_reset_between_fault_cases(self):
        state = service.FaultState()

        state.configure(
            "backend_failure",
            reset_stats=True,
        )
        state.record_route_request()
        state.record_route_request()

        self.assertEqual(
            2,
            state.snapshot()["routeRequests"],
        )

        state.configure(
            "no_suitable_edges",
            reset_stats=True,
        )

        self.assertEqual(
            0,
            state.snapshot()["routeRequests"],
        )
        self.assertEqual(
            "no_suitable_edges",
            state.snapshot()["mode"],
        )


if __name__ == "__main__":
    unittest.main()