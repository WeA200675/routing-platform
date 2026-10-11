# Open Source Policy

## Goal

The routing platform is developed open source from the first commit. First-party
code is intended to be distributable under the Apache License 2.0 unless a later,
explicit project decision changes that license.

## Core rule

`core/`, `schemas/`, `tools/`, backend services and generic adapters may depend only
on open-source or public-domain components.

No proprietary routing, map-rendering, analytics, traffic, geocoding, weather or
cloud SDK may become a required dependency of the Routing Core.

## Vehicle/platform adapters

Android Auto, Apple CarPlay and original Ford SYNC devices are proprietary runtime
platforms. Our adapter source code can remain open source, but compatibility may
require vendor-provided platform APIs/SDKs. These dependencies must remain isolated
under `platform/` or an adapter boundary and must never be required to build the
core routing engine.

SmartDeviceLink is preferred where applicable because its SDK/core ecosystem is
open source.

## Data licensing

Open-source software licensing and open-data licensing are tracked separately.
OpenStreetMap-derived data is subject to the Open Database License (ODbL). Generated
packages must retain required attribution and data-license metadata.

## Dependency review

Before adding a dependency, record:

- project name and source repository
- exact version
- SPDX license identifier
- whether it is linked, embedded, invoked as a service, or used only at build time
- required attribution/notices
- whether it affects redistribution of binaries or source

A dependency inventory will become machine-readable before the first public release.
