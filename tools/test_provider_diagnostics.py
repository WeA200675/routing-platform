#!/usr/bin/env python3
"""Smoke test for provider diagnostics ingestion."""
import json
import unittest
from backend.training_api import STATE, section


class ProviderDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        with STATE._lock:
            STATE.provider_diagnostics.clear()

    def test_diagnostics_are_aggregated(self):
        with STATE._lock:
            STATE.provider_diagnostics.append({
                "provider_id": "traffic",
                "captured_at": 1000,
                "success": True,
                "latency_ms": 120,
                "event_count": 3,
                "accepted_event_count": 2,
                "fallback_used": False,
                "error": None,
            })
        payload = section("diagnostics")
        diagnostics = payload["provider_diagnostics"]
        self.assertEqual(diagnostics["sample_count"], 1)
        self.assertEqual(diagnostics["success_count"], 1)
        self.assertEqual(diagnostics["average_latency_ms"], 120)
        self.assertEqual(diagnostics["recent"][0]["provider_id"], "traffic")


if __name__ == "__main__":
    unittest.main()
