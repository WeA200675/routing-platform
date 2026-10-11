# AI Model Integration Checklist

A local model/runtime may be enabled only after all items are satisfied.

- exact runtime upstream and immutable revision recorded
- runtime SPDX license reviewed
- exact model upstream and immutable revision recorded
- model/weight license reviewed separately
- redistribution terms reviewed
- model SHA-256 recorded and verified before loading
- expected memory/storage envelope documented
- offline behavior tested
- no implicit cloud fallback
- backend isolated behind LocalSocialAiTextGenerationBackend
- critical-guidance suppression test passes through the backend boundary
- navigation authority remains unchanged
- SBOM/license inventory updated
- Core CI and Android CI green

Candidate names in design notes are not approvals. Passing this checklist is the approval gate.
