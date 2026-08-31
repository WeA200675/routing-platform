# Adapters

External engines/providers are isolated behind platform-owned interfaces.

## Foundation 0.2

- `valhalla/`: open-source Valhalla routing adapter boundary.
- The default build contains a deterministic fallback that reports `VALHALLA_NOT_LINKED` rather
  than calling any proprietary routing service.
- A linked libvalhalla implementation is intentionally deferred until a Valhalla version is pinned
  and exercised by integration tests.
