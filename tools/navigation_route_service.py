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
                    "status": "ok",
                    "service": "routing-platform-navigation-route",
                },
            )
            return

        self.respond_json(
            404,
            {"error": "not_found"},
        )

    def do_POST(self):
        if self.path != "/v1/navigation/route":
            self.respond_json(
                404,
                {"error": "not_found"},
            )
            return

        if self.headers.get("X-Routing-Platform-Dev") != "1":
            self.respond_json(
                403,
                {"error": "development_header_required"},
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
                raise RuntimeError(
                    "route response exceeds size limit"
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
            self.respond_json(
                400,
                {
                    "error": "invalid_request",
                    "message": str(error),
                },
            )

        except subprocess.TimeoutExpired:
            self.respond_json(
                504,
                {
                    "error": "routing_timeout",
                },
            )

        except Exception as error:
            self.respond_json(
                502,
                {
                    "error": "routing_failed",
                    "message": str(error),
                },
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

                    raise RuntimeError(
                        "Valhalla route exporter failed "
                        f"with exit {process.returncode}: "
                        + output
                    )

                size = os.path.getsize(
                    export_path
                )

                if size <= 0 or size > MAX_RESPONSE_BYTES:
                    raise RuntimeError(
                        "invalid exported route size"
                    )

                with open(
                    export_path,
                    "r",
                    encoding="utf-8",
                ) as route_file:
                    route = json.load(
                        route_file
                    )

                if route.get("engineName") != "valhalla":
                    raise RuntimeError(
                        "exported route lost Valhalla identity"
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