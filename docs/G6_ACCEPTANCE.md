# G6 Acceptance Gate

G6 is the local Social AI and natural-language navigation product track.

## Completed architecture
- G6.19: safety/privacy architecture; deterministic navigation remains authoritative.
- G6.20: pinned llama.cpp + signed Android candidate + Qwen2.5-1.5B Q4_K_M local runtime.
- G6.21: strict fail-closed structured routing intent extraction.
- G6.22: trusted place resolution and bridge into NavigationRouteLifecycleController.
- G6.23: routing-intent protocol suite including malformed and injection-like output rejection.
- G6.24: model lifecycle limits plus bounded single-flight native generation guard.
- G6.25: SHA-256 verified atomic model installation primitive.
- G6.27: no route-family approximation is accepted as an exact avoidance guarantee.
- G6.29: LLM output remains non-authoritative; unknown/malformed/ambiguous values fail closed.

## G6.26 product integration gate
The production UI must invoke the same SocialAiNavigationFlow used by tests. It must never parse coordinates from model text. Home/work must resolve from trusted favorites; category stops require deterministic resolution or clarification.

## G6.28 physical-device evidence gate
Before final acceptance, run the signed release candidate on the target physical Android device and record: model SHA, native runtime SHA, inference latency, total request latency, available-memory observation, repeated-run result, offline/no-network-fallback result, and one cancellation/restart recovery result.

## G6.30 release gate
G6 is accepted only when all of the following are true:
1. Core CI and Android CI are green at the final G6 SHA.
2. Candidate evidence verifies the pinned runtime/model and APK signature.
3. Physical-device end-to-end flow reaches a real route preview from natural-language input.
4. Ambiguous destination/via input requests clarification rather than inventing coordinates.
5. motorway/toll/ferry avoidance is either represented by an exact deterministic routing option or rejected; approximation is forbidden.
6. The AI path has no implicit network fallback.

A green CI build alone is not physical-device acceptance.
