# Backend

Future open-source routing, synchronization, feedback and package services. Backend business
logic must share schemas and routing semantics with the device core.


## OSRM navigation adapter

`osrm_adapter.py` exposes the Android navigation contract at
`POST /v1/navigation/route` and delegates route calculation to an OSRM server.
It never derives geometry or traffic data itself.

Run an OSRM instance prepared from the OpenStreetMap extract for the intended
coverage area, then start the adapter with `OSRM_URL` pointing at that local
instance. The adapter binds only to loopback by default. Put an authenticated,
TLS-terminating reverse proxy in front of it before configuring a production
Android build, because the Android route source intentionally rejects non-HTTPS
production endpoints.

Only `fastest` and `profile_optimal` are admitted by this adapter because
the current OSRM integration cannot prove the semantics of the other route
families. Unsupported families fail closed. The returned contract explicitly
marks traffic data unavailable; periodic client re-evaluation must not be
presented as live traffic unless a future provider contract supplies verified
traffic provenance.

Contract regression test:

```sh
python3 -m unittest backend/test_osrm_adapter.py
```
