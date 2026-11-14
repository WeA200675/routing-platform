# Startup Crash Refactor Audit

Branch: `recovery/startup-crash-refactor`
Base: `g620-integration-local`
Date: 2026-11-14

## Current findings

### P0: native local-AI bridge is fail-closed by implementation

`social_ai_llama_jni.cpp` currently returns `JNI_FALSE` from `nativeLoadModel` unconditionally and returns an empty string from `nativeGenerate`. Any production path that warms up or starts local training will therefore fail even though the APK contains the model and the native library.

This is not a harmless placeholder in a production APK. It must be gated out of app startup or replaced by a verified llama.cpp implementation before local AI is advertised as active.

### P0: startup safety boundary is incomplete

`MainActivity.onCreate` calls `MapLibre.getInstance(this)` before any guarded startup state exists. Native bridge creation and route bootstrap are also constructed during Compose initialization. If any native loading, map initialization, route decoding, or provider construction fails, the launcher activity can terminate before showing a recoverable UI.

The startup path needs a guarded supervisor with:
- isolated MapLibre initialization,
- isolated navigation JNI initialization,
- isolated optional AI initialization,
- a visible degraded-mode screen,
- persisted crash diagnostics,
- no model warm-up during launcher composition.

### P1: route bootstrap recovery is not fully safe

`NavigationRouteBootstrap.load` catches the first failure but calls `bridge.currentSnapshot()` again inside the fallback path. If the native bridge itself is the failure source, the fallback throws again and escapes the recovery boundary.

### P1: model admission and worker behavior are inconsistent

`SocialAiProductRuntime` requires a verified model and native load success. `AiTrainingWorker` separately constructs the JNI engine and treats a failed native load as a terminal worker failure. These paths need one shared runtime admission result and must never run synchronously from the launcher.

### P1: runtime contracts need smoke coverage

Unit tests cover contracts and parser behavior, but the current repository does not prove:
- launcher activity survives missing/failed optional native AI,
- MapLibre initialization failure produces degraded mode,
- route bootstrap fallback cannot throw recursively,
- model load is deferred until an explicit training/runtime action.

## Refactoring order

1. Add startup supervisor and degraded-mode state.
2. Remove all eager optional-AI startup work.
3. Make route bootstrap fallback non-throwing.
4. Unify model/native admission.
5. Add Android instrumentation smoke tests.
6. Run GitHub Actions; only then produce a new APK.

## Explicit non-claim

This audit does not claim the app is fixed yet. The current branch is intentionally a recovery branch until the device-start path is verified.
