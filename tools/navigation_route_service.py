#!/usr/bin/env python3

import argparse
import json
import os
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


MAX_REQUEST_BYTES = 64 * 1024
MAX_RESPONSE_BYTES = 16 * 1024 * 1024
ERROR_SCHEMA_VERSION = 1
SERVICE_VERSION = 2

ALLOWED_FAMILIES = {
    "fastest",
    "shortest",
    "profile_optimal",
    "major_roads",
    "comfort",
    "low_urban",
    "low_curvature",
    "low_gradient",
    "low_traffic",
    "energy",
    "scenic",
    "stable",
}


class RouteServiceError(RuntimeError):
    def __init__(
        self,
        *,
        status,
        code,
        message,
        retryable,
    ):
        super().__init__(message)
        self.status = int(status)
        self.code = str(code)
        self.message = str(message)
        self.retryable = bool(retryable)


def error_payload(error):
    return {
        "schemaVersion": ERROR_SCHEMA_VERSION,
        "error": {
            "code": error.code,
            "message": error.message,
            "retryable": error.retryable,
        },
    }


def classify_exporter_failure(returncode, output):
    text = str(output or "")

    if "No suitable edges near location" in text:
        return RouteServiceError(
            status=422,
            code="no_suitable_edges",
            message=(
                "No suitable routable edges were found "
                "near one or more requested route points."
            ),
            retryable=False,
        )

    return RouteServiceError(
        status=502,
        code="route_export_failed",
        message=(
            "The routing backend could not produce a route."
        ),
        retryable=True,
    )


def readiness_snapshot(executable, config):
    checks = {
        "exporterFile": os.path.isfile(executable),
        "exporterExecutable": (
            os.path.isfile(executable)
            and os.access(executable, os.X_OK)
        ),
        "configFile": os.path.isfile(config),
        "configReadable": (
            os.path.isfile(config)
            and os.access(config, os.R_OK)
        ),
    }

    ready = all(checks.values())

    return {
        "schemaVersion": ERROR_SCHEMA_VERSION,
        "status": "ready" if ready else "not_ready",
        "service": "routing-platform-navigation-route",
        "version": SERVICE_VERSION,
        "checks": checks,
    }


def parse_point(value, name):
    if not isinstance(value, dict):
        raise ValueError(f"{name} must be an object")

    latitude = float(value["latitude"])
    longitude = float(value["longitude"])

    if not -90.0 <= latitude <= 90.0:
        raise ValueError(f"{name}.latitude outside [-90, 90]")

    if not -180.0 <= longitude <= 180.0:
        raise ValueError(f"{name}.longitude outside [-180, 180]")

    return latitude, longitude


def validate_request(payload):
    if not isinstance(payload, dict):
        raise ValueError("request must be an object")

    origin = parse_point(
        payload.get("origin"),
        "origin",
    )

    destination = parse_point(
        payload.get("destination"),
        "destination",
    )

    raw_via = payload.get(
        "viaPoints",
        [],
    )

    if not isinstance(raw_via, list):
        raise ValueError("viaPoints must be an array")

    if len(raw_via) > 16:
        raise ValueError("too many via points")

    via = [
        parse_point(
            point,
            f"viaPoints[{index}]",
        )
        for index, point in enumerate(raw_via)
    ]

    family = str(
        payload.get(
            "family",
            "profile_optimal",
        )
    )

    if family not in ALLOWED_FAMILIES:
        raise ValueError(
            f"unsupported family: {family}"
        )

    return origin, destination, via, family


class NavigationRouteHandler(BaseHTTPRequestHandler):
    server_version = "RoutingPlatformDevRoute/1"

    def log_message(self, format_string, *args):
        print(
            "[navigation-route-service] "
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
                    "service": "routing-platform-navigation-route",
                    "version": SERVICE_VERSION,
                },
            )
            return

        if self.path == "/ready":
            payload = self.server.readiness()
            status = (
                200
                if payload["status"] == "ready"
                else 503
            )

            self.respond_json(
                status,
                payload,
            )
            return

        self.respond_error(
            RouteServiceError(
                status=404,
                code="not_found",
                message="The requested service endpoint does not exist.",
                retryable=False,
            )
        )

    def do_POST(self):
        if self.path != "/v1/navigation/route":
            self.respond_error(
                RouteServiceError(
                    status=404,
                    code="not_found",
                    message="The requested service endpoint does not exist.",
                    retryable=False,
                )
            )
            return

        if self.headers.get("X-Routing-Platform-Dev") != "1":
            self.respond_error(
                RouteServiceError(
                    status=403,
                    code="development_header_required",
                    message="The development routing header is required.",
                    retryable=False,
                )
            )
            return

        try:
            length = int(
                self.headers.get(
                    "Content-Length",
                    "0",
                )
            )

            if length <= 0 or length > MAX_REQUEST_BYTES:
                raise ValueError(
                    "invalid request size"
                )

            raw = self.rfile.read(
                length
            )

            payload = json.loads(
                raw.decode("utf-8")
            )

            origin, destination, via, family = (
                validate_request(payload)
            )

            route = self.server.route(
                origin=origin,
                destination=destination,
                via=via,
                family=family,
            )

            encoded = json.dumps(
                route,
                ensure_ascii=False,
                separators=(",", ":"),
            ).encode("utf-8")

            if len(encoded) > MAX_RESPONSE_BYTES:
                raise RouteServiceError(
                    status=502,
                    code="response_too_large",
                    message="The routing backend response exceeded the size limit.",
                    retryable=False,
                )

            self.send_response(200)
            self.send_header(
                "Content-Type",
                "application/json; charset=utf-8",
            )
            self.send_header(
                "Content-Length",
                str(len(encoded)),
            )
            self.send_header(
                "Cache-Control",
                "no-store",
            )
            self.end_headers()
            self.wfile.write(encoded)

        except ValueError as error:
            self.respond_error(
                RouteServiceError(
                    status=400,
                    code="invalid_request",
                    message=str(error),
                    retryable=False,
                )
            )

        except subprocess.TimeoutExpired:
            self.respond_error(
                RouteServiceError(
                    status=504,
                    code="routing_timeout",
                    message="The routing backend timed out.",
                    retryable=True,
                )
            )

        except RouteServiceError as error:
            self.respond_error(
                error
            )

        except Exception as error:
            self.log_message(
                "unclassified backend failure: %s",
                str(error),
            )

            self.respond_error(
                RouteServiceError(
                    status=502,
                    code="backend_failure",
                    message="The routing backend failed unexpectedly.",
                    retryable=True,
                )
            )

    def respond_error(self, error):
        self.respond_json(
            error.status,
            error_payload(
                error
            ),
        )

    def respond_json(self, status, payload):
        encoded = json.dumps(
            payload,
            ensure_ascii=False,
            separators=(",", ":"),
        ).encode("utf-8")

        self.send_response(status)
        self.send_header(
            "Content-Type",
            "application/json; charset=utf-8",
        )
        self.send_header(
            "Content-Length",
            str(len(encoded)),
        )
        self.send_header(
            "Cache-Control",
            "no-store",
        )
        self.end_headers()
        self.wfile.write(encoded)


class NavigationRouteServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(
        self,
        address,
        executable,
        config,
    ):
        super().__init__(
            address,
            NavigationRouteHandler,
        )

        self.executable = executable
        self.config = config

        # Keep development routing deterministic and avoid loading
        # multiple Valhalla engines concurrently.
        self.route_lock = threading.Lock()

    def readiness(self):
        return readiness_snapshot(
            self.executable,
            self.config,
        )

    def route(
        self,
        origin,
        destination,
        via,
        family,
    ):
        with self.route_lock:
            fd, export_path = tempfile.mkstemp(
                prefix="routing-platform-live-route-",
                suffix=".json",
            )

            os.close(fd)

            try:
                environment = os.environ.copy()

                environment[
                    "ROUTING_PLATFORM_VALHALLA_TEST_CONFIG"
                ] = self.config

                environment[
                    "ROUTING_PLATFORM_NAVIGATION_ROUTE_EXPORT"
                ] = export_path

                environment[
                    "ROUTING_PLATFORM_ROUTE_ORIGIN_LAT"
                ] = repr(origin[0])

                environment[
                    "ROUTING_PLATFORM_ROUTE_ORIGIN_LON"
                ] = repr(origin[1])

                environment[
                    "ROUTING_PLATFORM_ROUTE_DESTINATION_LAT"
                ] = repr(destination[0])

                environment[
                    "ROUTING_PLATFORM_ROUTE_DESTINATION_LON"
                ] = repr(destination[1])

                environment[
                    "ROUTING_PLATFORM_ROUTE_FAMILY"
                ] = family

                environment[
                    "ROUTING_PLATFORM_ROUTE_VIA"
                ] = ";".join(
                    f"{latitude},{longitude}"
                    for latitude, longitude in via
                )

                process = subprocess.run(
                    [self.executable],
                    env=environment,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    timeout=30,
                    check=False,
                )

                if process.returncode != 0:
                    output = process.stdout[-4000:]

                    print(
                        "[navigation-route-service] "
                        "route exporter failure "
                        f"(exit {process.returncode}): "
                        + output,
                        flush=True,
                    )

                    raise classify_exporter_failure(
                        process.returncode,
                        output,
                    )

                size = os.path.getsize(
                    export_path
                )

                if size <= 0 or size > MAX_RESPONSE_BYTES:
                    raise RouteServiceError(
                        status=502,
                        code="invalid_exported_route",
                        message="The routing backend exported an invalid route size.",
                        retryable=False,
                    )

                try:
                    with open(
                        export_path,
                        "r",
                        encoding="utf-8",
                    ) as route_file:
                        route = json.load(
                            route_file
                        )
                except (
                    json.JSONDecodeError,
                    UnicodeDecodeError,
                ) as error:
                    raise RouteServiceError(
                        status=502,
                        code="invalid_exported_route",
                        message="The routing backend exported invalid route JSON.",
                        retryable=False,
                    ) from error

                if route.get("engineName") != "valhalla":
                    raise RouteServiceError(
                        status=502,
                        code="invalid_exported_route",
                        message="The exported route lost routing-engine identity.",
                        retryable=False,
                    )

                return route

            finally:
                try:
                    os.unlink(
                        export_path
                    )
                except FileNotFoundError:
                    pass


def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--listen",
        default="127.0.0.1",
    )

    parser.add_argument(
        "--port",
        type=int,
        default=8787,
    )

    parser.add_argument(
        "--executable",
        required=True,
    )

    parser.add_argument(
        "--config",
        required=True,
    )

    arguments = parser.parse_args()

    if not os.path.isfile(
        arguments.executable
    ):
        raise SystemExit(
            f"route exporter missing: {arguments.executable}"
        )

    if not os.path.isfile(
        arguments.config
    ):
        raise SystemExit(
            f"Valhalla config missing: {arguments.config}"
        )

    server = NavigationRouteServer(
        (
            arguments.listen,
            arguments.port,
        ),
        executable=arguments.executable,
        config=arguments.config,
    )

    print(
        "Navigation route service listening on "
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