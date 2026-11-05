# Training Center Runtime Adapter

The dashboard now loads its sections through dashboard/training-center-runtime.js.

## Behavior

- requests are bounded by a five-second timeout;
- each section is loaded independently;
- partial outages produce stale markers instead of fake live values;
- complete API outage produces an explicit Offline / Demo badge;
- every payload is normalized to training-center-v1 metadata;
- dialogue calls use training-dialogue-v1 and carry context separately.

## Required response metadata

Every endpoint should return schema_version, captured_at, sample_count, data_age_seconds, provenance, stale and offline. Section-specific fields may be added without breaking the adapter.
