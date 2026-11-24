# Production promotion gate – 2026-11-17

## Verified CI baseline
Commit `5178ebe8ece1e39e86d010ad77a88d68f33c28ec` passed Core CI, Android CI, Android and core verification, Training backend safety checks and local-storage-stack. Android CI run `44158761448` published the `routing-platform-debug-apk` artifact (artifact ID `14363270772`). This is a **debug artifact**, not a road-tested production release.

## No automatic production promotion
This repository must not promote code to the Oracle VM or open public network ports solely because CI passed. Promotion requires an operator-controlled deployment session with verified SSH host identity and credentials. Preserve the existing training API and database before changes.

## Operator checklist
1. Create a backup of PostgreSQL and MinIO data, test restoring into an isolated environment, and record checksums.
2. Check the Oracle VM OS/CPU architecture, free RAM/disk, existing listeners (especially 8787), Valhalla binary/config/tile versions, and service account.
3. Fetch the exact approved commit; validate source and service unit files before installation.
4. Install the loopback-only Valhalla bridge (8788) and Journey gateway (8791) using the systemd templates. Ensure tokens are generated securely and never included in APKs or Git.
5. Run `PYTHONPATH=. python3 tools/journey_production_preflight.py` on the VM. Require exit 0.
6. Implement and verify HTTPS reverse proxy, authenticated device sessions, TLS, request limits and logs without coordinates or secrets.
7. Connect the Android navigation runtime to that HTTPS service, preserving independent offline Valhalla routing; verify Android runtime integration and device smoke tests.
8. Test power/network loss, backend restart, map mismatch, oversized route, credential rotation, backups and rollback.
9. Approve production only after all gates are green. Keep Journey recommendations disabled until independent POI/graph attestation is complete.

## Rollback
Stop the new systemd units, restore previous verified binaries/configuration, keep old PostgreSQL/MinIO data intact, and validate existing API health before reopening access. Never destroy or overwrite unverified backups.

## Outstanding
The Oracle VM is **not deployed by GitHub commits**. This environment has GitHub repository access, not a verified SSH deployment session. Public HTTPS, device authentication, Android routing integration and field acceptance remain unverified.
