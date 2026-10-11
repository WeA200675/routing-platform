# Routing Platform Android

Open-source Android presentation adapter for the routing platform.

## Current scope

This first Android shell consumes a deterministic navigation UI snapshot
through `NavigationCoreBridge` and renders a route preview using Jetpack
Compose and MapLibre Native.

The default `MainActivity` deliberately uses `DemoNavigationCoreBridge`.
The demo geometry is a deterministic Liechtenstein-area fixture and is not
presented as a route calculated by the native routing core.

`JniNavigationCoreBridge` defines the future native boundary, but no JNI
library is loaded by the demo application.

The Android adapter does not implement routing, candidate selection,
CostEngine scoring, GPS map matching, rerouting, or production route
mutation.

## Technology

- Android Gradle Plugin 9.4
- Gradle 9.6
- AGP built-in Kotlin with Kotlin/Compose compiler 2.3.21
- Jetpack Compose BOM 2026.06.00
- MapLibre Native Android OpenGL 13.4.1
- compileSdk 36
- targetSdk 36
- JDK 17

`compileSdk 36` is intentional in this bootstrap: the local Android CLI
currently exposes API 36 but not the API 37 platform package. The project
therefore uses the last stable Compose generation before the API 37
compileSdk requirement. This can be upgraded independently when API 37 is
available in the installed SDK repository.

## Planned progression

1. Native JNI bridge to Navigation Runtime schema v1.
2. GPS/location adapter and map matching.
3. Controlled rerouting.
4. Driver Voice & Intent Runtime:
   configurable wake word, extensible command registry, local-first
   intent recognition, and a deterministic safety/action gate.
5. Arrival Experience:
   configurable destination greeting and short approximately three-second
   arrival celebration overlays such as confetti, stars, or custom assets.

Driver Voice and Arrival Experience remain presentation/runtime features;
neither is permitted to bypass routing-core safety boundaries.
