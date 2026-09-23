# P28–P36 Acceptance — Advanced Platform

P28–P36 extend the P-series without weakening prior fail-closed, evidence-integrity, privacy or platform boundaries.

| Milestone | Required invariant |
|---|---|
| P28 | Android and iOS execute one versioned parity corpus; platform-local duplicate truth is not acceptance evidence. |
| P29 | Offline routing datasets carry explicit identity, version, digest and provenance; unknown/mismatched provenance is unavailable. |
| P30 | Persisted navigation state is versioned, bounded and freshness-gated; corrupt/incompatible state is never authoritative. |
| P31 | Runtime queues, buffers and cancellation obey explicit resource budgets; pressure degrades by rejection, not unsafe bypass. |
| P32 | Secure storage/authentication use OS-backed adapters; unavailable capability fails closed and never falls back to plaintext. |
| P33 | Reproducibility checks bind source, reviewed inputs and generated evidence/artifacts; mismatches block promotion. |
| P34 | Test-signed, production-signed and distributed artifacts are distinct states with non-interchangeable evidence. |
| P35 | Physical validation records bind candidate SHA, artifact digest, platform/version and test result. |
| P36 | GA promotion requires every declared automated and external evidence class for one immutable candidate. |

## Evidence integrity

CI proves only executed automation. macOS Swift tests are not physical iPhone evidence. Android JVM/emulator tests are not physical Android evidence. A debug/test signature is not production signing, notarization, store acceptance or publication.

## Promotion

P28–P34 may implement automatable contracts while physical P27 acceptance is outstanding. P35 and P36 cannot be accepted without real external/device evidence. Missing evidence is represented as missing, never synthesized.

## P29–P31 implementation evidence

P29 binds admitted offline datasets to explicit source identity, dataset version and canonical lowercase SHA-256 provenance. Missing, malformed or version-mismatched provenance makes the dataset unavailable. The byte-level release verifier remains the boundary that computes and compares actual dataset bytes.

P30 has a versioned restore boundary plus an Android persistence adapter wired into the navigation lifecycle. Persisted records are bounded, schema checked, freshness gated and bound to Android's boot-count identity so monotonic timestamps cannot be trusted across reboot. Navigation start/runtime activity refreshes the bounded record, regular stop clears it, and corrupt, stale, future-dated, cross-boot, session-mismatched or incompatible state is cleared and never authoritative. If a boot identity is unavailable, persistence fails closed.

P31 maps resource admission into explicit backpressure and wires the bounded pending-work queue into the Android location callback path. A single-drainer handoff prevents concurrent callback drains and closes the empty-queue ownership race. Queue diagnostics expose bounded queue depth, high-water mark, admissions, rejections and cancellations; queue exhaustion rejects work and clears queued work. Safety telemetry also carries the matched route distance and ambiguity margin needed to diagnose a hold without weakening the safety thresholds. Independently bounded sensor histories remain separate. Physical CPU, battery and wakeup measurements remain external device evidence.

## P32–P34 implementation evidence

P32 retains the platform-neutral secure-storage admission contract and now has concrete platform adapters on both sides: Android Keystore for Android, and Apple Keychain plus LocalAuthentication capability detection for the Swift package. The Apple Keychain adapter uses device-only, when-unlocked accessibility and fails closed on unavailable/invalid operations. Swift tests cover invalid identities and a store/load/delete Keychain round trip when the Security framework backend is available. The iOS repository target remains a Swift library/contract package rather than an installable signed iPhone application, so this is OS-backed adapter evidence, not physical-device, application-distribution or Secure Enclave hardware-backing evidence.

P33 regenerates deterministic release evidence independently into a second directory from the same pinned inputs and candidate SHA and byte-compares both SBOM and release manifest before packaging. Portable relative-path SHA-256 identity files are then generated for the final evidence. Full APK cross-run reproducibility is not claimed because the installable RC is test-signed.

P34 emits an explicit machine-readable distribution state and validates it against a closed state machine. Impossible combinations of test signing, production signing and publication fail. The current Android candidate remains `test-signed-installable-rc`, with `productionSigned=false` and `storePublished=false`.

## P35–P36 implementation evidence

P35 defines a strict physical-device acceptance record verifier. A passing record must bind a 40-hex candidate source SHA and 64-hex artifact digest to canonical `android` or `ios` platform identity plus a non-empty platform version, assert `physicalDevice=true`, and contain `result=pass`. The repository example is deliberately non-passing with placeholder identities, so it cannot be mistaken for real hardware evidence.

P36 is fail closed by default. GA eligibility always requires immutable Android and iOS artifact identities, matching physical-device acceptance for both platforms, production signing and store publication. There is no flag that can downgrade those GA requirements. The current candidate cannot pass GA because it intentionally lacks production distribution and an immutable iOS release artifact identity.

## Completion hardening

P28 now has a candidate-CI corpus gate that validates the single shared parity fixture file before release evidence is produced. P29 now also has a byte-level dataset provenance verifier that computes SHA-256 from the actual dataset and rejects identity/version omissions or digest mismatches. These close earlier evidence gaps without converting host CI into device evidence.

The repository implementation can be completed only up to externally controlled boundaries. P35/P36 deliberately require real physical Android/iOS records, and production distribution requires real signing/store state. Placeholder records, host tests or test signing are structurally rejected as substitutes.
