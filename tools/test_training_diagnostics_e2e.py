#!/usr/bin/env python3
"""End-to-end smoke test for provider diagnostics ingestion and reporting."""
import json
import subprocess
import sys
import tempfile
import time
import urllib.request


def request(url, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST" if payload is not None else "GET",
    )
    with urllib.request.urlopen(req, timeout=5) as response:
        return response.status, json.loads(response.read().decode())


def main():
    port = "18787"
    state_file = tempfile.NamedTemporaryFile(suffix=".json", delete=False)
    state_file.close()
    environment = {**__import__("os").environ, "TRAINING_API_HOST": "127.0.0.1", "TRAINING_API_PORT": port, "TRAINING_STATE_PATH": state_file.name}
    process = subprocess.Popen(
        [sys.executable, "backend/training_api.py"],
        env=environment,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        base = f"http://127.0.0.1:{port}"
        for _ in range(30):
            try:
                request(base + "/api/training/diagnostics")
                break
            except Exception:
                time.sleep(0.1)
        status, accepted = request(
            base + "/api/training/events",
            {
                "events": [{
                    "event_id": "e2e-provider-001",
                    "type": "navigation-provider-diagnostic",
                    "captured_at": int(time.time() * 1000),
                    "size_bytes": 256,
                    "payload": {
                        "provider_id": "traffic",
                        "success": True,
                        "latency_ms": 143,
                        "event_count": 4,
                        "accepted_event_count": 3,
                        "fallback_used": False,
                        "error": None,
                    },
                }]
            },
        )
        assert status == 202 and accepted["accepted"] == 1, accepted

        status, diagnostics = request(base + "/api/training/diagnostics")
        provider = diagnostics["provider_diagnostics"]
        assert status == 200
        assert provider["sample_count"] == 1, provider
        assert provider["success_rate"] == 1.0, provider
        assert provider["average_latency_ms"] == 143, provider

        retry_status, retry = request(
            base + "/api/training/events",
            {
                "events": [{
                    "event_id": "e2e-provider-001",
                    "type": "navigation-provider-diagnostic",
                    "size_bytes": 256,
                    "payload": {"provider_id": "traffic", "success": True, "latency_ms": 143},
                }]
            },
        )
        assert retry_status == 202 and retry["accepted"] == 0, retry
        process.terminate()
        process.wait(timeout=5)
        process = subprocess.Popen(
            [sys.executable, "backend/training_api.py"],
            env=environment,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        time.sleep(0.3)
        restart_status, restart = request(
            base + "/api/training/events",
            {"events": [{"event_id": "e2e-provider-001", "type": "navigation-provider-diagnostic", "size_bytes": 256}]},
        )
        assert restart_status == 202 and restart["accepted"] == 0, restart
        print("provider diagnostics e2e: PASS")
    finally:
        process.terminate()
        process.wait(timeout=5)


if __name__ == "__main__":
    main()
