# Hybrid Oracle Architecture – Implementation Master Plan

**Project:** routing-platform  
**Status:** Approved target design; implementation pending  
**Date:** 2026-11-15  
**Scope:** Offline-first Android navigation with Oracle Always Free storage, background analytics, controlled AI training and model delivery.

> This is an implementation plan, not evidence that the described components have been deployed or tested. Existing runtime services must be inventoried before migration. No secrets, public IPs or personal trip data belong in this document.

## 1. Non-negotiable requirements

1. **Navigation works without Oracle.** Local Valhalla routing, offline maps, GPS/map matching, maneuver selection and spoken guidance remain available on Android.
2. **Cloud failures never block navigation.** Telemetry is buffered durably, retried later and subject to explicit storage budgets.
3. **No unverified AI influence.** Training produces candidates; deterministic replay, regression, shadow evaluation and controlled release gate all changes.
4. **Durable, auditable ingestion.** Every event has stable IDs; duplicate uploads are harmless; an HTTP acknowledgement follows committed storage.
5. **Always Free cost guardrails.** Verify tenancy-specific limits and existing consumption. Do not provision paid resources or auto-scale to paid capacity.
6. **Recoverability.** Versioned deployment, backup/restore drills, rollback and observability are required before production rollout.

## 2. Logical architecture

```text
Android (offline authoritative)
  local Valhalla + maps + GPS + guidance + stable model release
  Room event outbox -> WorkManager HTTPS sync -> API gateway
                                      |
Oracle ARM64 VM                       v
  TLS gateway -> authenticated versioned ingestion API
                    |                 |
                    v                 v
             PostgreSQL         MinIO object storage
              events/jobs       raw batches/replay/models
                    \                 /
                     durable job scheduler
                             |
                   CPU/RAM-budgeted workers
                    normalize -> analyze
                    replay -> train -> evaluate
                             |
                signed model registry + release gate
                             |
                    Android staged download
  Dashboard <- read-only operational APIs and metrics
```

**Trust boundary:** The routing core is authoritative for route geometry, maneuver direction and safety-critical instructions. An LLM may summarize findings and propose experiments, never directly override the live route.

## 3. Repository findings requiring remediation

These are findings from the repository review and must be rechecked against the latest commit before editing:

- `backend/training_api.py` currently relies on process memory and a JSON state file; PostgreSQL and MinIO provisioning alone does **not** make the API state durable.
- `deploy/training-cloud/docker-compose.yml` provisions PostgreSQL/MinIO, but the training API is not yet fully wired to them; persistent API state and volume configuration need verification.
- Placeholder secrets in deployment examples must be replaced with injected secrets before external exposure.
- The existing Android diagnostics queue is SharedPreferences-backed and capped at 64 snapshots; replace with a transactional Room outbox with explicit retention.
- The Android upload worker and server request contracts must be checked end-to-end: payload shape, endpoint path, acknowledgement semantics, authentication and error codes.
- Verify the Caddy `handle_path /api/*` rewrite: it strips the `/api` prefix while backend handlers may expect `/api/training/*`. Add a reverse-proxy integration test and correct the mapping if reproduced.
- Existing worker completion states must be based on actual evaluation results, not mere syntactic acceptance of events.
- Keep `docs/AI_TRAINING_PLATFORM_ARCHITECTURE.md` and `docs/AI_STORAGE_RETENTION_POLICY.md` aligned with the implementation.

## 4. Infrastructure and resource governance

**Inventory first:** Oracle region, VM shape/architecture, allocated OCPUs and RAM, boot/block volumes, free disk, Docker versions, existing nginx/Caddy listeners, existing workspace workloads, firewall rules, DNS, certificates and actual Free Tier eligibility. Published quotas and account-specific allocations can differ; never assume a particular A1 shape is available.

- Run PostgreSQL 16, MinIO, ingestion API, workers and dashboard in isolated Docker Compose services on ARM64.
- Only TLS gateway ports are internet-facing; PostgreSQL and MinIO use internal networks.
- Use persistent named/bind volumes with explicit ownership, backup procedures and tested restore.
- Pin image digests or reviewed versions; use staged updates, health checks and rollback.
- Reserve RAM and CPU for OS, database, gateway and routing telemetry ingestion; background training is lowest priority.
- Enforce container memory/CPU limits, job concurrency caps and pause-on-pressure behavior.
- Set bounded logs, bounded object storage, disk-pressure thresholds and a protected system reserve.
- Never launch a paid instance, disk, load balancer or managed database automatically.
- Keep personal administration behind a VPN or equivalently strong access controls.

**Capacity:** Start with measured capacity, not a fixed promise. Assign per-class quotas after observing real usage. Target a minimum 20% reserve in the data budget and separate reserves for WAL, temporary files, OS and backup staging.

## 5. Data model and durability

PostgreSQL is the source of truth for structured metadata; MinIO stores immutable larger objects. Proposed tables:

```text
devices                  navigation_sessions
navigation_events        diagnostic_reports
upload_batches           storage_objects
training_jobs            training_experiments
replay_results           model_versions
model_releases           quality_metrics
audit_log
```

Required columns and constraints:

- UUID/ULID `event_id`, `session_id`, `device_id` (pseudonymous); server timestamps and schema version.
- Unique constraint on `event_id` and idempotency key; stable batch IDs.
- Explicit job state, attempt counter, lease owner/expiry, next retry time, failure reason.
- Object key, content hash, size, retention class, reference count and lifecycle state.
- Model parent version, training dataset fingerprint, evaluation results, signature and compatibility metadata.

**Commit protocol:** validate request and authorization; deduplicate; write durable records in a DB transaction; for object payloads use staged object upload plus transactional metadata and a reconciler; only then return a durable acknowledgement. Retries must never duplicate accepted events. Use schema migrations and contract tests.

## 6. Android sync and offline behavior

- Replace SharedPreferences snapshots with a Room outbox; migrate existing pending snapshots without silent loss.
- States: `PENDING -> READY -> UPLOADING -> ACKNOWLEDGED -> CLEANUP`; errors transition to `RETRY_PENDING`, invalid events to quarantine.
- Use WorkManager with network constraints, exponential backoff with jitter and bounded batches. Active navigation may trigger non-blocking sync, never blocking the navigation thread.
- Keep last-known working HTTPS endpoint, configurable primary endpoint and optional secondary endpoint; prevent cross-backend duplicate acceptance with stable IDs.
- Device credentials are revocable and stored using platform-protected mechanisms; no static root secret in the APK.
- Implement compression, timeouts, request-size caps, protocol version negotiation and 401/403/409/413/429/5xx handling.
- Distinguish temporary errors from permanent validation failures. Remove local records only after verified acknowledgement and retention rules.
- Add UI indicators for queue size, last sync, cloud status and recoverable errors.
- On loss of network, DNS, TLS or backend, navigation remains completely local.

## 7. Training and background work

1. **Observe:** capture route stability, map matching, GPS jitter, maneuver direction, spoken cue timing, fallback rate and interruptions.
2. **Normalize:** validate, redact/minimize, group by session and identify anomalies.
3. **Hypothesize:** cluster failure patterns and propose parameter candidates.
4. **Replay:** run reproducible scenarios with pinned inputs and code/model versions.
5. **Evaluate:** compare against baseline with quality and safety metrics; reject regressions.
6. **Shadow:** score candidates without changing live navigation.
7. **Canary:** activate for explicitly permitted pilot scope with automatic rollback.
8. **Release:** publish signed, compatible artifacts and retain known-good rollback versions.

Worker requirements: durable queue, lease/heartbeat, retries with bounded attempts, poison-job quarantine, idempotent execution, CPU/RAM/disk budgets, preemption and pause under load. Train small targeted models/parameters first. Optional quantized LLM supports explanations and summaries only; large-model training is not a Free Tier assumption.

## 8. Storage retention and pressure handling

Proposed initial retention (subject to privacy review and measured usage):

| Class | Initial policy |
| --- | --- |
| Raw GPS/object batches | 30 days |
| Normalized events | 180 days |
| Sessions and diagnostic reports | 12 months |
| Experiment metadata | 12 months |
| Aggregated quality metrics | Longer-term with minimization |
| Logs | 14–30 days |
| Released models, rollback models, critical replay fixtures | Protected until safely superseded |

Use configurable thresholds (e.g. 75% cleanup, 85% stronger eviction, 90% low-priority admission pause, 95% noncritical write rejection). Apply transactional FIFO by class and protection status, not indiscriminate deletion. Keep separate WAL and OS headroom. If cleanup cannot safely free space, reject new noncritical uploads with a retryable response; preserve navigation.

## 9. Security and privacy

- HTTPS/TLS, strict authN/authZ, per-device credentials, revocation and rotation.
- Administrator dashboard with separate identity and MFA where supported.
- MinIO private buckets; PostgreSQL never exposed publicly.
- SSH keys, limited source networks, host firewall and minimal privileges.
- Secrets from protected environment/secret store; never commit credentials.
- Schema validation, request caps, rate limits, audit logging and dependency scanning.
- Encrypt backups; protect access to location traces; minimize precise location retention.
- Provide export and deletion workflows with documented exceptions for immutable audit or legally required records.
- Verify model signatures, hashes and compatibility before Android installation.

## 10. Failure matrix and fallback

| Failure | Required response |
| --- | --- |
| Android offline | Local navigation continues; Room queue persists |
| API/DNS/TLS unavailable | Retry with backoff; do not block routing |
| PostgreSQL unavailable | API returns retryable failure; no false acknowledgement |
| MinIO unavailable | Stage/retry objects; no orphaned success state |
| Worker crashes | Lease expires; job resumes idempotently |
| Storage pressure | Controlled eviction and admission limits |
| Bad model | Disable candidate and restore last known-good release |
| Entire Oracle VM down | Android works offline; cloud recovers from backups |
| Corrupt DB | Restore tested backup/WAL to consistent point |
| Bad deployment | Roll back image/config and validate health |

Single Always Free VM is **not** high availability. Backups solely on that VM do not protect against complete VM loss; maintain an independent encrypted copy and rehearse restoration. Define realistic RPO/RTO after measuring backup capabilities.

## 11. Proposed repository structure

```text
backend/
  api/ ingestion/ persistence/ workers/
  training/ evaluation/ releases/
schemas/sql/ schemas/proto/
deploy/oracle/
  compose.yml Caddyfile scripts/ monitoring/
tests/integration/ tests/replay/ tests/resilience/ tests/security/
docs/architecture/ docs/operations/ docs/acceptance/
```

Keep existing working directories intact and migrate incrementally. Do not restructure the entire monorepo before baseline tests pass.

## 12. Eight implementation milestones

| # | Milestone | Estimate | Definition of done |
| --- | --- | --- | --- |
| M1 | Inventory and baseline | 1–2 workdays | Existing services, resources, tests and restore point documented |
| M2 | Harden Oracle infrastructure | 2–4 workdays | ARM64 stack, TLS, secrets, volumes, limits and health checks validated |
| M3 | Durable backend | 4–7 workdays | PostgreSQL/MinIO wired; migrations, idempotency, persistence and FIFO tested |
| M4 | Android synchronization | 3–5 workdays | Room/WorkManager; network loss/retry/duplicate tests pass |
| M5 | AI analysis and training | 5–10 workdays | Real replay metrics and reproducible candidate evaluation |
| M6 | Model registry and release | 3–5 workdays | Signature, compatibility, shadow/canary and rollback tests pass |
| M7 | Dashboard and operations | 3–5 workdays | Real telemetry, monitoring, backups, access control and restore drills |
| M8 | End-to-end pilot | 5–7 workdays | Resilience, load, security, road tests and acceptance evidence recorded |

**Indicative total:** 26–45 workdays, excluding major newly discovered defects, external approvals or infrastructure availability delays. Parallel work only where interfaces and dependencies are stable.

### Delivery gates

- M1 is mandatory before any destructive migration.
- M3 must pass before Android sync is enabled against production.
- M4 must pass before background training relies on real data.
- M5 evaluation gates must pass before M6 release functionality can activate.
- M8 requires repeatable tests and explicit pilot approval.

## 13. Acceptance metrics (targets, not current measurements)

| Metric | Pilot target |
| --- | --- |
| Offline navigation | All defined offline scenarios pass |
| Duplicate upload persistence | Zero duplicate event IDs after retries |
| Acknowledged event integrity | Zero lost acknowledged events in fault-injection tests |
| API availability | >=99% during measured pilot window |
| API p95 latency | <500 ms at specified test load |
| Storage reserve | >=20% of assigned data budget |
| Critical regression suite | 100% pass before model release |
| Config rollback | Target <60 seconds, measured |
| Backup restore | Full consistency-checked restore succeeds |

Record load profile, dataset, device, environment and measurement windows; percentages without a test definition are not evidence.

## 14. Execution protocol

1. Inventory live Oracle VM and GitHub main branch; record hashes, backups, versions and test failures.
2. Create a feature branch and issues for M1–M8; no secrets in issues or logs.
3. Implement changes in small reviewed commits with migration/rollback steps.
4. Run unit, contract, integration, fault-injection and Android offline tests in CI.
5. Deploy to staging or isolated Compose project first; never replace working services without a rollback.
6. Produce per-milestone evidence: changes, test output, resource consumption, risks and outstanding items.
7. Promote to pilot only after explicit acceptance.

## 15. Immediate next actions

- Check latest `main` against the findings in section 3.
- Measure Oracle CPU/RAM/disk/network capacity and existing workspace contention.
- Verify reverse-proxy path mapping and Android/server event schema with integration tests.
- Backup current persistent state and test restoring it.
- Implement PostgreSQL-backed event ingestion before expanding AI training.

## Related existing documentation

- [AI Training Platform Architecture](AI_TRAINING_PLATFORM_ARCHITECTURE.md)
- [AI Storage Retention Policy](AI_STORAGE_RETENTION_POLICY.md)
- [Training Cloud Deployment](../deploy/training-cloud/README.md)

**Change control:** This document describes the intended architecture. Update its status and link implementation commits, tests and operational runbooks as milestones are completed.
