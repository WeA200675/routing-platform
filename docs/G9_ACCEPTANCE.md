# G9 Acceptance — Contextual Driving Assistant

This document is normative because the repository contained no prior G9 acceptance specification.

G9 is complete only when all criteria below are satisfied on the same final commit.

1. Context may change presentation and suggestions, but never authoritative GPS, route geometry, route progress, maneuver state, safety gates, or route-family semantics.
2. Any automatic route change is produced by the configured routing engine and passes the same deterministic lifecycle/replacement gates as a manual route.
3. Stale, missing, low-confidence, dead-reckoned, rejected, or ambiguous position/context fails closed and leaves the active route unchanged.
4. Assistant/LLM output is not a source of coordinates, traffic state, road restrictions, route scores, ETA, or navigation progress.
5. Provider capability and provenance are explicit. Missing traffic data is displayed/treated as unavailable, never inferred from periodic polling.
6. Concurrent assistant, reroute, and refresh operations cannot overwrite a newer navigation session; cancellation/generation boundaries discard stale callbacks.
7. Privacy: no navigation location, favorites, route geometry, or assistant prompt is sent to an undeclared AI/cloud endpoint.
8. Unit/integration/regression tests cover context freshness, trusted origin, deterministic replacement, concurrency and fail-closed provider behavior.
9. Core CI and Android CI are green on the final G9 SHA. Device-only checks may be combined with the final G10 acceptance package.

## Cross-platform device adaptation

10. Device adaptation is capability-driven, not manufacturer- or model-name-driven. Pixel is a reference-device acceptance target, not a product requirement.
11. Calibration starts from conservative defaults and may learn only bounded quality/reliability parameters from local observations. It must never synthesize or move coordinates, route geometry, maneuver state, traffic, ETA, or route progress.
12. Calibration state is local to the device, versioned, resettable, and ignored when stale/incompatible. Navigation remains safe and functional with calibration absent or rejected.
13. Platform sensor/location acquisition is behind an adapter boundary so the same authoritative navigation contracts can be implemented by Android and a future Apple platform implementation without duplicating routing decisions.
14. First-use calibration is disclosed in product UX; it is not hidden data collection. No calibration observation is uploaded by the calibration layer.

## Driving interference evidence

15. During an active navigation session the app may record only OS-authorized connectivity/system events needed for driving-interference diagnostics. It must not perform covert packet interception or continuous radio scanning merely for surveillance.
16. Evidence records distinguish ordinary connectivity changes, interference anomalies, and verified security-integrity failures. Presence of an unknown Bluetooth/Wi-Fi device alone is never classified as an attack.
17. A location attached to an evidence record is explicitly the vehicle/device navigation observation at event time, never an inferred source/attacker location. It is included only when the authoritative observation passes the same freshness/provenance safety gates; otherwise location is absent.
18. Precise evidence stays encrypted/local by default, is excluded from AI prompts and normal telemetry, has bounded retention, and is accessible only through an authenticated local admin diagnostic surface. Driving mode must not introduce distracting full-screen or extreme-audio alerts.
19. Evidence uses deterministic event identifiers/digests, UTC and monotonic timestamps, source/provenance, navigation-session pseudonymous reference, action taken, and integrity state sufficient for an auditable post-drive timeline.
