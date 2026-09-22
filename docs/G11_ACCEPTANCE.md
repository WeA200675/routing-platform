# G11 Acceptance — Production Navigation & Security

G11 is complete only when all automated criteria are green on one immutable candidate and the required physical-device evidence is attached.

1. Calibration is capability-driven, manufacturer-neutral, local, versioned, resettable and never alters coordinates, geometry, maneuvers, traffic, ETA or progress.
2. Persisted calibration has explicit UTC freshness; future, stale, malformed and incompatible profiles fail closed to conservative defaults.
3. Driving-security evidence records OS-authorized diagnostic events only. Unknown Bluetooth/Wi-Fi presence alone is a connectivity change, never proof of attack.
4. Evidence has separate payload digest and event identifier, UTC plus monotonic time, pseudonymous session reference, provenance/action/integrity state and optional vehicle position.
5. Vehicle position is never source/attacker attribution and may be attached only after the normal trusted navigation freshness/provenance gates.
6. Sensitive evidence remains local and encrypted at rest, is excluded from AI prompts/normal telemetry, has bounded age and bounded record count, and is available only behind an authenticated local-admin surface.
7. Driving mode never emits a distracting full-screen or extreme-audio security alarm. Containment is deterministic and defensive; no hack-back exists.
8. Routing and AI retain all G6-G10 fail-closed boundaries. Unsupported provider capabilities remain unavailable rather than approximated.
9. Core CI, Android CI and the full release-candidate pipeline succeed on the exact G11 candidate SHA.
10. Physical-device acceptance is combined into one final package after all automated gates are green.
