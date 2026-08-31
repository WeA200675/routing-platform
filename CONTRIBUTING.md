# Contributing

Foundation 0.1 is intentionally strict about architecture and licensing.

1. Keep routing policy in the shared core, not in a UI adapter.
2. Add deterministic tests for behavior changes.
3. Do not add proprietary dependencies to `core/`, `schemas/`, `tools/` or generic adapters.
4. Document each new dependency and its SPDX license in `dependencies/oss-dependencies.toml`.
5. Keep map/data licensing separate from source-code licensing.
6. Preserve explainability: routing-cost changes should expose a reason/contribution.

All first-party contributions are submitted under the repository's Apache-2.0 license.
