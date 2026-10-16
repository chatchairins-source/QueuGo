# Rider map physical road-test gate — 2026-10-16

Source: PR #368, `work-native-rider-map-follow-stability-20261016`.
Baseline: `72e56554f4baaa552afbad8418fb03d857ad1c8c`.
Last published gate checkpoint: `5da8046234ebe9d03e9b0e9cd08d409ce7df4283`.
Local follow-up source checkpoint: `db3817cc` (keep puck/recenter live during route I/O).

## Implemented

- Google Maps handoff already contained no preference write. Correct obsolete assertions that required the removed workflow write/automatic handoff; keep committed bundle snapshot checks. Fix undefined merchantHome in source gate.
- Repeated GPS service starts request a genuinely fresh fused measurement (zero cached age, bounded request, cancellation on replacement/destroy).
- Rebuild Rider puck from last valid fix in the same renderer restoration pass, even if location producer temporarily emits null.
- Flatten and rotate idle/end/cancel map north immediately, including no-fix and persisted tilted-camera restoration. Active navigation remains on existing stabilized pipeline.
- Add actual Longdo job-end instrumentation assertions: flat/north-up, retained puck without a new GPS tick, same MapGLSurfaceView.
- GPS, recenter and gestures update visible presentation while route coordinator awaits network (up to 12 seconds); late route results retain the latest Rider fix/view mode/insets.
- Activate all seven requested Pilot jobs on this branch. Publish only the exact Pilot APK artifact after all seven succeed, as a prerelease with source HEAD and SHA256. No rebuild or Production deployment.

## Evidence

- Full `npm test` PASS before the final route-I/O presentation follow-up.
- Final follow-up: native navigation module contracts PASS, Android source 272 checks PASS, diff check PASS.
- Stability106, optional Google Maps, route bundle, slide parity, map runtime budget and workflow pinning contracts PASS.
- Pilot38888706895 on 5da80462: three race jobs already green at this checkpoint; build/runtime jobs still running. This is not final-HEAD certification.
- No local Android SDK/Gradle; Android JVM/build/emulator evidence must come from the exact final Pilot run.

## Next action / open gates

1. Inspect final-HEAD Native Pilot: native-pilot, map-display-only, navigation-map-runtime, maplibre-osm-runtime, continuous-dispatch-races, passenger-ride-races, credit-races.
2. Resolve any actual build/runtime failures and checkpoint immediately. Do not weaken correct assertions.
3. Verify published Rider APK source HEAD, download URL, checksum and install packaging. Provide plain HTTPS URL for Android/LINE/Facebook.
4. Owner physical road test remains mandatory: idle GPS/recenter; accept; straight/turn/U-turn; tabs; HOME; screen lock; resume; explicit Google Maps and return. Job state, route, puck and renderer continuity must survive.

No physical PASS claim, no Production writes, no renderer migration. MapLibre remains fallback evidence only.

## Gate evidence on source 86f276f19ab32f3a3dca67f2306ed33cbb9de1a6

Pilot38888952472 passed native-pilot (full npm/source/JVM, three APKs, 16KB packaging, system-kill recreation, eight-stage Rider recovery, GPS foreground service), navigation-map-runtime (7/7 actual Longdo tests), maplibre-osm-runtime and all three race gates. No Production writes.

Map-display-only remains OPEN:
- Attempt1: actual Customer location screenshot had a partially gray map (gray fraction0.077937); denied-permission case passed. Artifact12040329558 retains original pixels/logs; strict gray threshold remains unchanged.
- Attempts2/3: before inspecting tiles, UiTestAutomationBridge returned a zero-exit null root; pull failed because XML did not exist (artifacts12042296747,12042118549). The intended30-second wait aborted on its first transient read error.
- Follow-up4e64d9ca fixes the real QA collector: discard previous remote XML, detect null-root output, retry transient hierarchy failures only within the original wait budget, retain poll errors, and fail on deadline or wrong-screen predicate. New fixtures prove recovery, no stale evidence, bounded timeout and strict failure. Existing map pixel/gray/crash assertions remain unchanged. No application code changed in this follow-up.
- PASS: real collector fixtures and native-rider-map-runtime-budget. Next: exact new HEAD all seven gates, including the still-unverified Customer tile case. APK publication remains blocked until all seven pass; physical road test still OPEN. Do not report Map complete.
