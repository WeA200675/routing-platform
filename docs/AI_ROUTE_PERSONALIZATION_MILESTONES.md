# AI route personalization milestones

Target: supervised Android field test on 2026-10-10. Software changes remain candidates until CI passes on one exact commit and the field APK is verified on the device.

## M11 — Ask the route priority before generation (implemented; CI pending)

In Preview, the assistant asks which route family to use for this request. The three common choices are fastest, shortest and profile-optimized. An expandable list exposes the engine's other exact families: major roads, comfort, less urban, less curvy, less gradient, lower traffic according to route data, energy, scenic and stable. The currently learned recommendation is also available when one exists.

The choice is per request. The driver is never prompted while navigation is active.

## M12 — Connect the answer to the real routing request (implemented; CI pending)

The selected `NavigationRouteFamily` is passed through the local AI orchestration and validated request bridge into `NavigationRouteRequest`. The model still cannot create coordinates or route geometry. Unsupported avoidance constraints continue to fail closed.

## M13 — Rate a successful route preview (implemented; CI pending)

After the route service returns `LiveReady`, Preview asks whether the selected priority fit. “Andere Priorität” returns to the question and creates a new preview. A rating is not shown for an unsuccessful route acquisition or a bundled fallback.

## M14 — Learn one small, profile-local preference (implemented; CI pending)

A positive rating stores only the chosen route-family enum in the existing local AI memory store, and only when **Routenlernen** is enabled in AI settings. It stores no destination, coordinates, route geometry, trip history or free-form rating text. The profile identifier is hashed before it is used to select the memory namespace.

Disabling **Routenlernen** removes this stored route-family preference. When learning is off, ratings apply only to the current preview.

## M15 — Verify the interaction and learning boundaries (in progress)

Required checks:
1. Unit tests for profile isolation, opt-in storage, recall and forget.
2. Android CI and the full production-candidate workflow pass on the same commit.
3. On-device checks confirm the question and rating only appear in Preview and the selected family reaches the generated request.
4. Verify the field APK checksum before the drive.

## M16 — Learn from corrective feedback (not implemented)

Today, a negative rating reopens the priority question but is not remembered. Add opt-in learning from a few structured reasons such as “too long”, “too complex” or “not the right road type”. Keep the feedback profile-local, bounded, inspectable and deletable. Apply it as a suggestion that the user can override per route.

## M17 — Measure on the road (not run)

Follow [Saturday calibration drive runbook](SATURDAY_CALIBRATION_RUNBOOK.md). The field run records the exact candidate and APK hashes, service readiness, route outcome and operator observations. A successful build or a route rating is not physical-device acceptance or GA approval.

## M18 — Production promotion (not ready)

After the supervised field drive, review exact-SHA evidence, privacy behavior, route-family quality, crashes and safety holds. Promote only through the existing production-candidate and GA gates; Saturday's drive alone does not authorize release.
