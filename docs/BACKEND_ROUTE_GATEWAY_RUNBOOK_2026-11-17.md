# Backend route gateway: secure development deployment

The navigation backend is now two separate services:
- **Valhalla route exporter bridge**: `tools/navigation_route_service.py`, loopback port 8788 recommended; requires exporter executable and Valhalla configuration.
- **Journey gateway**: `backend/journey_gateway.py`, loopback port 8791; optional authenticated proxy `POST /api/navigation/v1/route`; `GET /api/journey/v1/status` reports route availability independently of POI recommendation readiness.

## Start on the same trusted host

```bash
# Do not bind either service to 0.0.0.0.
python3 tools/navigation_route_service.py \
  --listen 127.0.0.1 --port 8788 \
  --executable /absolute/path/to/route-exporter \
  --config /absolute/path/to/valhalla.json

# Separate shell; generate and store token in a protected secret store.
export JOURNEY_ROUTE_BEARER_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
export JOURNEY_ROUTE_SERVICE_URL="http://127.0.0.1:8788/v1/navigation/route"
python3 -m backend.journey_gateway --listen 127.0.0.1 --port 8791
```

Do **not** put the token in a repository, APK, public website, shell-history command, or mobile app. For Android, use a user-authenticated HTTPS reverse proxy with short-lived device-scoped credentials and TLS certificate validation; static shared tokens embedded in an APK are unsafe. The above gateway is an **internal integration bridge**, not a finished public mobile endpoint.

## Local checks

```bash
curl -fsS http://127.0.0.1:8791/api/journey/v1/status
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8791/ready
PYTHONPATH=. python3 -m unittest -v tools.test_journey_gateway tools.test_journey_route_client
```

Expected: status returns HTTP 200 with `navigation_routing_enabled=true` only when provider and token are configured; `recommendations_enabled=false` and `/ready=503` until audited POI/graph identity integration is complete. Route proxy is default-off without a valid token and provider. Route requests require `Authorization: Bearer ...`; malformed coordinates, oversized requests and routing errors fail closed.

## Remaining release blockers

1. Verify real Valhalla route export and map coverage; no live engine was executed in this change.
2. Implement a production-grade authenticated HTTPS mobile gateway (rotatable credentials, abuse protection and observability).
3. Wire Android navigation runtime to the new endpoint with local offline Valhalla fallback; **the current Android Journey client is status-only**.
4. Verify Wi-Fi handover, offline map import, voice guidance, route recovery and device field test.
5. Verify CI and build artifact SHA-256 for the actual release commit.
6. Independently attest POI data and map graph identity before enabling journey break recommendations.

**No Oracle production deployment or main-branch merge was performed.**
