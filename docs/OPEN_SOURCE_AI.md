# Open Source AI Policy

G6.19 and later AI features are designed to remain usable without a mandatory proprietary AI service.

## Runtime rule

The routing platform must not require a closed cloud model API or proprietary SDK for its core AI behavior. Network-backed providers may be added later only as optional adapters; the local/open-source path must remain a first-class supported configuration.

## Dependency rule

Before adding an AI runtime, tokenizer, inference engine, vector store, model loader, or similar component:

1. Record the exact upstream project, version/revision, and SPDX license identifier.
2. Prefer permissive OSI-approved licenses such as Apache-2.0, MIT, BSD-2-Clause, or BSD-3-Clause.
3. Keep third-party code behind a narrow adapter boundary so it can be replaced.
4. Do not copy model/runtime artifacts into this repository unless redistribution is explicitly allowed.
5. Pin downloaded artifacts by immutable revision and verify a cryptographic digest.
6. Add the dependency to the project SBOM/license inventory before release.

## Model rule

"Open weights" is not automatically the same as open source. Every model used or recommended by the project must have its model/weight license reviewed separately from the inference engine license.

For a distributable default model, the license must permit the intended use, modification, and redistribution. Model provenance, exact revision, license, and checksum must be documented.

## Privacy and offline operation

Persistent learned memory is owned by the user profile and stored separately from the model. The architecture should support fully local inference and local memory. Uploading learned memory or conversation context must never become an implicit requirement.

## Safety boundary

AI components may propose presentation, conversation, learned preferences, and non-authoritative assistance. They must not gain write authority over positioning integrity, route-progress safety gates, permission enforcement, or other safety-critical navigation state.

Critical guidance suppresses nonessential social behavior, including humor, playful embellishment, charm, and flirt behavior.

## Adult social features

Adult-flirt behavior is an explicit opt-in feature. It is disabled by default and is never inferred from conversation or profile data. Navigation status suppresses flirt behavior, and critical guidance suppresses all nonessential social behavior.

## CI expectation

AI-related changes should pass both native Core CI and Android CI. License/SBOM checks should become a required CI gate before a third-party inference engine or model artifact is introduced.
