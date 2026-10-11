#!/usr/bin/env python3

import argparse
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


ERROR_SCHEMA_VERSION = 1
SERVICE_VERSION = 1
MAX_REQUEST_BYTES = 64 * 1024
CONTROL_HEADER = "X-Routing-Platform-Test-Control"

SUPPORTED_MODES = {
    "backend_failure",
    "routing_timeout",
    "no_suitable_edges",
    "invalid_response",
    "unknown_retryable",
}


class FaultState:
    def __init__(self):
        self._lock = threading.Lock()
        self._mode = "backend_failure"
        self._route_requests = 0

    def configure(self, mode, reset_stats=True):
        if mode not in SUPPORTED_MODES:
            raise ValueError(f"unsupported fault mode: {mode}")

        with self._lock:
            self._mode = mode

            if reset_stats:
                self._route_requests = 0

    def record_route_request(self):
        with self._lock:
            self._route_requests += 1
            return self._mode

    def snapshot(self):
        with self._lock:
            return {
                "schemaVersion": ERROR_SCHEMA_VERSION,
                "mode": self._mode,
                "routeRequests": self._route_requests,
            }


def structured_error(code, message, retryable):
    return {
        "schemaVersion": ERROR_SCHEMA_VERSION,
        "error": {
            "code": code,
            "message": message,
            "retryable": bool(retryable),
        },
    }


def route_response(mode):
    if mode == "backend_failure":
        return (
            502,
            structured_error(
                "route_export_failed",
                "Injected routing backend failure.",
                True,
            ),
        )

    if mode == "routing_timeout":
        return (
            504,
            structured_error(
                "routing_timeout",
                "Injected routing timeout.",
                True,
            ),
        )

    if mode == "no_suitable_edges":
        return (
            422,
            structured_error(
                "no_suitable_edges",
                "Injected no-suitable-edges result.",
                False,
            ),
        )

    if mode == "unknown_retryable":
        return (
            400,
            structured_error(
                "future_unknown",
                "Injected unknown future error code.",
                True,
            ),
        )

    if mode == "invalid_response":
        return (
            200,
            b'{"schemaVersion":1,"routeId":',
        )

    raise ValueError(
        f"unsupported fault mode: {mode}"
    )


class FaultInjectionHandler(BaseHTTPRequestHandler):
    server_version = "RoutingPlatformFaultInjection/1"

    def log_message(self, format_string, *args):
        print(
            "[navigation-fault-injection] "
            + format_string % args,
            flush=True,
        )

    def do_GET(self):
        if self.path == "/health":
            self.respond_json(
                200,
                {
                    "schemaVersion": ERROR_SCHEMA_VERSION,
                    "status": "ok",
                    "service": "routing-platform-navigation-fault-injection",
                    "version": SERVICE_VERSION,
                },
            )
            return

        if self.path == "/ready":
            self.respond_json(
                200,
                {
                    "schemaVersion": ERROR_SCHEMA_VERSION,
                    "status": "ready",
                    "service": "routing-platform-navigation-fault-injection",
                    "version": SERVICE_VERSION,
                },
            )
            return

        if self.path == "/__test__/stats":
            if not self.control_authorized():
                self.respond_json(
                    403,
                    structured_error(
                        "test_control_denied",
                        "Test control header is required.",
                        False,
                    ),
                )
                return

            self.respond_json(
                200,
                self.server.fault_state.snapshot(),
            )
            return

        self.respond_json(
            404,
            structured_error(
                "not_found",
                "Fault injection endpoint not found.",
                False,
            ),
        )

    def do_POST(self):
        if self.path == "/__test__/configure":
            if not self.control_authorized():
                self.respond_json(
                    403,
                    structured_error(
                        "test_control_denied",
                        "Test control header is required.",
                        False,
                    ),
                )
                return

            try:
                payload = self.read_json_body()

                self.server.fault_state.configure(
                    mode=str(
                        payload.get(
                            "mode",
                            "",
                        )
                    ),
                    reset_stats=bool(
                        payload.get(
                            "resetStats",
                            True,
                        )
                    ),
                )

                self.respond_json(
                    200,
                    self.server.fault_state.snapshot(),
                )
            except (
                ValueError,
                UnicodeDecodeError,
                json.JSONDecodeError,
            ) as error:
                self.respond_json(
                    400,
                    structured_error(
                        "invalid_test_control",
                        str(error),
                        False,
                    ),
                )

            return

        if self.path != "/v1/navigation/route":
            self.respond_json(
                404,
                structured_error(
                    "not_found",
                    "Fault injection endpoint not found.",
                    False,
                ),
            )
            return

        if self.headers.get("X-Routing-Platform-Dev") != "1":
            self.respond_json(
                403,
                structured_error(
                    "development_header_required",
                    "Development routing header is required.",
                    False,
                ),
            )
            return

        try:
            self.read_bounded_body()

            mode = (
                self.server
                .fault_state
                .record_route_request()
            )

            status, payload = route_response(
                mode
            )

            if isinstance(
                payload,
                bytes,
            ):
                self.respond_bytes(
                    status,
                    payload,
                )
            else:
                self.respond_json(
                    status,
                    payload,
                )
        except ValueError as error:
            self.respond_json(
                400,
                structured_error(
                    "invalid_request",
                    str(error),
                    False,
                ),
            )

    def control_authorized(self):
        return (
            self.headers.get(
                CONTROL_HEADER
            )
            == "1"
        )

    def read_bounded_body(self):
        length = int(
            self.headers.get(
                "Content-Length",
                "0",
            )
        )

        if (
            length <= 0
            or length > MAX_REQUEST_BYTES
        ):
            raise ValueError(
                "invalid request size"
            )

        return self.rfile.read(
            length
        )

    def read_json_body(self):
        raw = self.read_bounded_body()

        payload = json.loads(
            raw.decode(
                "utf-8"
            )
        )

        if not isinstance(
            payload,
            dict,
        ):
            raise ValueError(
                "control payload must be an object"
            )

        return payload

    def respond_json(self, status, payload):
        encoded = json.dumps(
            payload,
            ensure_ascii=False,
            separators=(",", ":"),
        ).encode(
            "utf-8"
        )

        self.respond_bytes(
            status,
            encoded,
        )

    def respond_bytes(self, status, payload):
        self.send_response(
            status
        )
        self.send_header(
            "Content-Type",
            "application/json; charset=utf-8",
        )
        self.send_header(
            "Content-Length",
            str(
                len(
                    payload
                )
            ),
        )
        self.send_header(
            "Cache-Control",
            "no-store",
        )
        self.end_headers()
        self.wfile.write(
            payload
        )


class FaultInjectionServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(
        self,
        address,
        fault_state=None,
    ):
        super().__init__(
            address,
            FaultInjectionHandler,
        )

        self.fault_state = (
            fault_state
            if fault_state is not None
            else FaultState()
        )


def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--listen",
        default="127.0.0.1",
    )

    parser.add_argument(
        "--port",
        type=int,
        default=18787,
    )

    arguments = parser.parse_args()

    server = FaultInjectionServer(
        (
            arguments.listen,
            arguments.port,
        )
    )

    print(
        "Navigation fault injection service listening on "
        f"{arguments.listen}:{arguments.port}",
        flush=True,
    )

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()