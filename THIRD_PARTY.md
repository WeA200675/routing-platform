# Planned third-party components

This file is an architecture inventory, not yet a vendored dependency list.
Versions will be pinned when each component is introduced.

| Component | Intended role | License / status | Isolation |
|---|---|---|---|
| Valhalla | Routing + map matching foundation | MIT | Routing adapter/core integration |
| MapLibre Native | Map rendering | BSD-2-Clause | Platform presentation layer |
| Protocol Buffers | Data contracts | BSD-3-Clause style license | Schemas/build |
| SQLite | Local application data | Public domain | Device storage |
| PostgreSQL | Backend database | PostgreSQL License | Backend only |
| PostGIS | Spatial backend extension | GPL-2.0 | Backend process/database only |
| Nominatim | OSM geocoding service | GPL (mixed GPLv3+/GPLv2 files) | Separate service/adapter |
| SmartDeviceLink | Vehicle integration where applicable | BSD-3-Clause | Vehicle adapter |
| OpenStreetMap data | Road/map data | ODbL | Data pipeline/offline packages |

No closed-source routing or map engine is planned for the core product.
