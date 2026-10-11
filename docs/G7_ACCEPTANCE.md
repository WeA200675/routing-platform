# G7 – Produktreife Navigation: Acceptance Contract

Status: normative acceptance contract established because the repository previously contained no G7 definition.

G7 is complete only when the existing deterministic navigation architecture is production-ready.

## Definition of Done

1. Route preview, start, active guidance, reroute, arrival and stop transitions are deterministic and regression-tested.
2. Location loss, stale fixes, route-source failure and reroute failure degrade safely and visibly; stale position must not silently advance guidance.
3. Destination and ordered via points survive acquisition and rerouting without reordering.
4. Voice, haptic and visual guidance derive from the same authoritative navigation snapshot; presentation code makes no routing decisions.
5. Lifecycle restart/background recovery cannot create duplicate sessions or route requests.
6. Accessibility-critical guidance does not rely on color alone.
7. Social AI cannot directly mutate GPS state, route geometry, maneuver progress or safety gates.
8. Unit, integration and regression tests cover these gates; Core CI and Android CI are green on the final G7 SHA.
9. A signed candidate is produced from pinned inputs. Hardware-only checks may be combined with the final G10 device package.
