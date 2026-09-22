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

P29 adds a canonical offline provenance admission boundary requiring an explicit source identity and lowercase 64-hex SHA-256 before provenance can be represented as verified. This contract does not itself calculate a dataset digest; storage/download adapters must supply and verify the digest before admission.

P30 adds a versioned persisted-navigation restore boundary. Restore requires the current schema, non-blank session identity, valid monotonic timestamps and an inclusive freshness budget. Future, stale, malformed and incompatible state fails closed.

P31 maps resource admission into an explicit backpressure decision. Available capacity admits work; exhausted or malformed resource state rejects new work and requests queued-work cancellation. Unit tests cover both pressure and invalid-state behavior. Integration with concrete runtime queues remains required before claiming measured queue/resource behavior.

## P32–P34 implementation evidence

P32 introduces an explicit secure-storage admission contract: sensitive evidence may be stored only when the platform reports OS- or hardware-backed storage and authenticated access is available. Unavailable storage and unauthenticated access fail closed. This contract does not by itself prove Android Keystore or Apple Keychain integration; those adapters remain separate platform evidence.

P33 extends the production candidate with reproducibility identity files for the generated release manifest and SBOM and immediately verifies those SHA-256 records. These records prove integrity of the generated evidence within a run; cross-run byte-for-byte reproducibility still requires a second independent build comparison.

P34 emits an explicit machine-readable distribution state. The current Android candidate is classified as `test-signed-installable-rc`, with `productionSigned=false` and `storePublished=false`. Promotion logic therefore has concrete evidence that prevents the existing debug-key RC from being represented as a production-signed or store-published artifact.
